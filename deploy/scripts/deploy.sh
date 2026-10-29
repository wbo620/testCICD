#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 服务器端部署脚本（由 CD 工作流通过 SSH 调用）
#
# 流程：记录当前版本 -> 拉取新镜像 -> 滚动重启 -> 健康检查
#       健康检查失败时自动回滚到上一个版本。
#
# 依赖环境变量：
#   DEPLOY_PATH       部署目录（默认 /opt/snowy）
#   IMAGE_TAG         要部署的镜像标签
#   REGISTRY          镜像仓库地址（默认 ghcr.io）
#   IMAGE_NAMESPACE   镜像命名空间（GitHub 组织名）
#   IMAGE_NAME        镜像名前缀（默认 snowy）
#   DEPLOY_HEALTH_URL 健康检查地址
#   GHCR_PAT/GHCR_USER 私有仓库登录凭据（可选）
# ---------------------------------------------------------------------------
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/snowy}"
REGISTRY="${REGISTRY:-ghcr.io}"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-local}"
IMAGE_NAME="${IMAGE_NAME:-snowy}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
DEPLOY_HEALTH_URL="${DEPLOY_HEALTH_URL:-http://127.0.0.1:81/healthz}"
COMPOSE_FILE="$DEPLOY_PATH/docker-compose.yml"
STATE_FILE="$DEPLOY_PATH/.last-deployed-tag"

cd "$DEPLOY_PATH"

echo "==> 部署目录: $DEPLOY_PATH"
echo "==> 镜像: $REGISTRY/$IMAGE_NAMESPACE/$IMAGE_NAME-* : $IMAGE_TAG"

if [ -n "${GHCR_PAT:-}" ] && [ -n "${GHCR_USER:-}" ]; then
  echo "==> 登录私有镜像仓库 $REGISTRY"
  echo "$GHCR_PAT" | docker login "$REGISTRY" -u "$GHCR_USER" --password-stdin
elif [ -n "${GHCR_PAT:-}" ]; then
  echo "!! 提供了 GHCR_PAT 但缺少 GHCR_USER，跳过镜像仓库登录"
fi

# 记录回滚目标
PREVIOUS_TAG=""
if [ -f "$STATE_FILE" ]; then
  PREVIOUS_TAG="$(cat "$STATE_FILE")"
fi
echo "==> 上一版本: ${PREVIOUS_TAG:-<无>}"

export REGISTRY IMAGE_NAMESPACE IMAGE_TAG IMAGE_NAME
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-snowy}"

# 生成 MySQL 初始化脚本。
# Snowy 的建表脚本里没有 USE 语句, 而 MySQL 官方镜像的 entrypoint 执行
# /docker-entrypoint-initdb.d 下的 .sql 时不会默认选中数据库,
# 直接导入会报 1046 (3D000) No database selected, 所以这里补一行 USE。
DB_NAME="${MYSQL_DATABASE:-snowy}"
if [ -f "$DEPLOY_PATH/.env" ]; then
  DB_NAME="$(sed -n 's/^MYSQL_DATABASE=//p' "$DEPLOY_PATH/.env" | head -1 | tr -d '"' | tr -d "'")"
  DB_NAME="${DB_NAME:-snowy}"
fi
if [ -f "$DEPLOY_PATH/initdb-src/snowy_mysql.sql" ]; then
  mkdir -p "$DEPLOY_PATH/initdb"
  {
    echo "USE \`$DB_NAME\`;"
    cat "$DEPLOY_PATH/initdb-src/snowy_mysql.sql"
  } > "$DEPLOY_PATH/initdb/01-snowy.sql"
  echo "==> 已生成数据库初始化脚本 (库名: $DB_NAME)"
fi

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

echo "==> 拉取镜像"
compose pull || {
  echo "!! 拉取镜像失败，终止部署"
  exit 1
}

echo "==> 启动新版本"
compose up -d --remove-orphans

echo "==> 等待服务健康"
healthy=0
for attempt in $(seq 1 40); do
  if curl -fsS --max-time 5 "$DEPLOY_HEALTH_URL" >/dev/null 2>&1; then
    healthy=1
    echo "==> 健康检查通过（第 $attempt 次探测）"
    break
  fi
  echo "    第 $attempt 次探测未通过，等待 5s..."
  sleep 5
done

if [ "$healthy" -ne 1 ]; then
  echo "!! 健康检查失败，开始回滚"
  compose ps
  compose logs --tail 100 || true
  if [ -n "$PREVIOUS_TAG" ]; then
    echo "==> 回滚到 $PREVIOUS_TAG"
    export IMAGE_TAG="$PREVIOUS_TAG"
    compose up -d --remove-orphans || true
    sleep 15
    if curl -fsS --max-time 5 "$DEPLOY_HEALTH_URL" >/dev/null 2>&1; then
      echo "==> 回滚成功，服务已恢复到 $PREVIOUS_TAG"
    else
      echo "!! 回滚后服务仍不健康，需要人工介入"
    fi
  else
    echo "!! 没有可回滚的历史版本"
  fi
  exit 1
fi

echo "$IMAGE_TAG" > "$STATE_FILE"
echo "==> 部署完成: $IMAGE_TAG"
compose ps
