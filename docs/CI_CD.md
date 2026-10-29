# CI/CD 全流程说明

本项目采用 **GitHub Actions** 实现持续集成与持续交付：

```
提交 / 合并请求                主干推送 / 打标签 / 发布 Release
        │                                    │
        ▼                                    ▼
   ┌──────────┐                        ┌──────────┐
   │ CI 工作流 │                        │ CD 工作流 │
   │ ci.yml   │                        │ cd.yml   │
   └──────────┘                        └──────────┘
        │                                    │
   后端测试 / 前端测试 /                 构建镜像 → 推送
   镜像构建验证 / CodeQL                 到 GHCR
                                            │
                                     SSH 部署到服务器
                                     （docker compose）
                                            │
                                     健康检查 → 失败自动回滚
```

---

## 1. 目录结构

```
.
├── src/                     后端源码（Spring Boot 3.5 / JDK 17）
├── pom.xml
├── maven-settings.xml       Maven 仓库配置（Central 镜像 + Unidata 仓库）
├── Dockerfile               后端镜像（多阶段：maven → JRE 17）
├── gfs_20260701_00z_f000.grib2   GRIB2 数据文件（后端镜像内置）
├── frontend/                前端源码（Vue 3 + Vite + TypeScript）
│   ├── Dockerfile           前端镜像（多阶段：node 20 → nginx）
│   └── nginx/default.conf   Nginx 配置：静态托管 + /api 反向代理
├── deploy/
│   ├── docker-compose.yml   生产环境编排（backend + frontend）
│   ├── scripts/deploy.sh    服务器端部署脚本（含回滚）
│   └── nginx/default.conf   独立 Nginx 场景的同一份配置
├── .github/
│   ├── workflows/ci.yml     持续集成
│   ├── workflows/cd.yml     持续交付
│   └── dependabot.yml       依赖自动更新
└── docs/CI_CD.md            本文档
```

---

## 2. CI（持续集成）— `.github/workflows/ci.yml`

**触发条件**：推送到 `main` / `master` / `develop`，或向这些分支发起 PR，或手动触发。

| Job | 内容 | 关键点 |
| --- | --- | --- |
| `backend` | `mvn -B -s maven-settings.xml verify` | Temurin JDK 17，`actions/setup-java` 缓存 Maven 依赖；产物 `target/testProject.jar` 与测试报告上传为 artifact |
| `frontend` | `npm ci` → `lint` → `type-check` → `test:unit` → `build` | Node 20，按 `package-lock.json` 缓存；产物 `frontend/dist` 上传为 artifact |
| `docker` | 构建后端与前端镜像（**不推送**） | 矩阵并行；`docker/build-push-action` + GHA 缓存，确保镜像一定可构建 |
| `security` | CodeQL 静态分析 | 分析 `java` 与 `javascript-typescript` 两个语言 |

`docker` job 依赖 `backend` 与 `frontend` 全部通过才执行，保证只有通过测试的代码才会被打包。

---

## 3. CD（持续交付）— `.github/workflows/cd.yml`

**触发条件**：推送到 `main` / `master`、推送 `v*` 标签、发布 Release、或手动触发（可指定 `image_tag`）。

### 3.1 build-push：构建并推送镜像

- 登录 `ghcr.io`，使用内置 `GITHUB_TOKEN`（需要 `packages: write` 权限）。
- 镜像命名：`ghcr.io/<组织名小写>/grib-service-backend`、`ghcr.io/<组织名小写>/grib-service-frontend`。
- 标签策略：

  | 标签 | 含义 |
  | --- | --- |
  | `sha-<完整提交 SHA>` | 每次构建的不可变标签，**部署使用这个标签**（便于回滚） |
  | `latest` | 仅在 `main` / `master` 推送时生成 |
  | `1.2.3` / `1.2` | 推送 `v1.2.3` 标签或发布 Release 时按语义化版本生成 |

### 3.2 deploy：部署到服务器

1. 写入 SSH 私钥与 `known_hosts`；
2. `scp` 上传 `deploy/` 目录到服务器；
3. 通过 SSH 执行 `deploy/scripts/deploy.sh`；
4. 冒烟测试：容器状态、`/healthz`、后端 `/api/grib/variables`。

`deploy/scripts/deploy.sh` 的流程：

```
记录当前版本 → docker compose pull → docker compose up -d
             → 轮询健康检查（最多 30 次，每次间隔 5s）
             → 失败则回滚到 .last-deployed-tag 记录的版本并再次探活
             → 成功则把新版本写入 .last-deployed-tag
```

---

## 4. 需要配置的 Secrets 与 Variables

在仓库 **Settings → Secrets and variables → Actions** 中添加。

### 4.1 Secrets（必填）

| 名称 | 说明 |
| --- | --- |
| `DEPLOY_HOST` | 服务器地址 / IP |
| `DEPLOY_USER` | SSH 用户名（需有 Docker 权限，建议加入 `docker` 用户组） |
| `DEPLOY_SSH_KEY` | SSH 私钥内容（整段 `-----BEGIN OPENSSH PRIVATE KEY-----`） |
| `DEPLOY_SSH_KNOWN_HOSTS` | 服务器 host 公钥（`ssh-keyscan -p 22 <host>` 获取），**强烈建议填写** |

### 4.2 Secrets（可选）

| 名称 | 默认值 | 说明 |
| --- | --- | --- |
| `DEPLOY_PORT` | `22` | SSH 端口 |
| `DEPLOY_PATH` | `/opt/grib-service` | 服务器部署目录 |
| `DEPLOY_HEALTH_URL` | `http://<host>/healthz` | 部署后的健康检查地址（**仓库变量 Variables**，不是 Secret） |
| `GHCR_PAT` | 空 | 私有镜像仓库的 PAT（权限 `read:packages`） |
| `GHCR_USER` | 空 | 与 `GHCR_PAT` 配套的用户名 |

> 若 GHCR 仓库为**公开**，`GHCR_PAT` / `GHCR_USER` 可以不填。

### 4.3 Variables（可选）

| 名称 | 说明 |
| --- | --- |
| `DEPLOY_HEALTH_URL` | 部署后的健康检查地址。**必须用变量而不是 Secret**：`jobs.<id>.environment.url` 的可用上下文里没有 `secrets`，写成 Secret 会让整个工作流文件被判定为无效 |
| `IMAGE_NAMESPACE` | 若不想用 GitHub 组织名作为镜像命名空间，可改用该变量 |
| `DEPLOY_ENABLED` | 设为 `true` 才会执行部署。默认不部署，只构建并推送镜像，这样还没配服务器时主干推送也能保持绿灯 |

---

## 5. 服务器准备

```bash
# 1. 安装 Docker 与 Compose 插件
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"   # 重新登录后生效

# 2. 创建部署目录
sudo mkdir -p /opt/grib-service && sudo chown -R "$USER" /opt/grib-service

# 3. 确认 SSH 可用
ssh -p 22 user@host 'docker version'
```

服务器不需要预装 JDK、Node 或 Nginx —— 运行环境全部在容器内。

---

## 6. 首次启用

### 6.1 创建仓库并推送

```bash
cd E:/wb/testProject
git remote add origin https://github.com/<你的账号>/<仓库名>.git
git push -u origin main
```

推送后 **Actions → CI → Run workflow** 手动触发一次，确认四个 Job 全绿。

### 6.2 配置 Secrets

按第 4 节把 `DEPLOY_*` 填好。

### 6.3 触发第一次部署

```bash
git commit --allow-empty -m "chore: trigger initial deployment"
git push
```

或直接在 Actions 页面手动运行 **CD** 工作流。

---

## 7. 本地验证（与 CI 完全一致）

```bash
# 后端
mvn -B -s maven-settings.xml verify

# 前端
cd frontend
npm ci
npm run lint && npm run type-check && npm run test:unit && npm run build
```

镜像构建（需要本机 Docker）：

```bash
docker build -t grib-backend:local .
docker build -t grib-frontend:local -f frontend/Dockerfile frontend

# 本地起一套完整的编排
cd deploy
REGISTRY=ghcr.io IMAGE_NAMESPACE=local IMAGE_TAG=latest docker compose up -d
curl http://localhost/healthz
curl http://localhost/api/grib/variables
```

---

## 8. 常见问题

**Q：`mvn verify` 报 `edu.ucar:cdm:5.5.3` 无法解析？**
A：5.x 的 `cdm` 构件从未发布到 Unidata 仓库与 Maven Central，项目已锁定在 `cdm:4.6.21` + `grib:4.6.21`（与 README 的踩坑记录一致）。

**Q：CI 报 Maven 证书错误？**
A：`maven-settings.xml` 把 Central 镜像指向 `repo1.maven.org` 直连，绕开本地可能不可用的镜像站；若在受限网络中构建，请改用内网 Nexus。

**Q：部署后页面能打开但接口 502？**
A：Nginx 上游 `backend` 未就绪。`docker compose ps` 确认 `backend` 为 `healthy`，`backend` 的健康检查依赖 `/actuator/health`。

**Q：想换服务器上的 GRIB2 文件？**
A：复制 `deploy/docker-compose.override.yml.example` 为 `docker-compose.override.yml`，挂载单个文件并覆盖 `GRIB_FILE`。**注意挂载单个文件而不是目录**，否则空目录会遮住镜像内置的数据文件。

**Q：镜像仓库是私有的，服务器拉取失败？**
A：配置 `GHCR_PAT`（权限 `read:packages`）与 `GHCR_USER`，`deploy.sh` 会自动执行 `docker login`。
