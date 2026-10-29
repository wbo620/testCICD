# Snowy CI/CD 说明

本分支为 **Snowy** 接入 GitHub Actions 全流程自动化后的 CI/CD 配置。

```
提交 / 合并请求                     主干推送 / snowy 分支 / 打标签 / 发 Release
        │                                    │
        ▼                                    ▼
   ┌──────────┐                        ┌──────────┐
   │ CI 工作流 │                        │ CD 工作流 │
   │ ci.yml   │                        │ cd.yml   │
   └──────────┘                        └──────────┘
        │                                    │
   后端测试 / 前端构建 /                构建镜像 → 推送 GHCR
   镜像构建验证 / CodeQL                        │
                                              SSH 部署到服务器
                                              （docker compose）
                                                     │
                                              健康检查 → 失败自动回滚
```

---

## 1. Snowy 现状与本次补齐的部分

Snowy 官方仓库里 `snowy-web-app/src/main/resources/docker-compose.yml` 引用了根目录的
`Dockerfile`，但**仓库中并不存在该文件**，也没有任何 CI 配置。本次补齐：

| 补齐项 | 说明 |
| --- | --- |
| `Dockerfile` | 后端多阶段镜像：`maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre-jammy` 运行，产物为 `snowy-web-app` 可执行 jar |
| `snowy-admin-web/Dockerfile` | 前端多阶段镜像：`node:20-alpine` 构建 → `nginx:stable-alpine` 运行 |
| `snowy-admin-web/nginx/default.conf` | 托管前端静态资源，并把 `/api` 反向代理到后端容器 |
| `deploy/docker-compose.yml` | MySQL + Redis + 后端 + Nginx 的完整编排 |
| `deploy/scripts/deploy.sh` | 服务器端部署脚本，健康检查失败自动回滚 |
| `.github/workflows/ci.yml` | 持续集成 |
| `.github/workflows/cd.yml` | 持续交付 |
| `snowy-admin-web/package-lock.json` | 原本被 `.gitignore` 忽略，提交后 CI 才能用 `npm ci` 复现构建 |

---

## 2. 关键适配点

### 2.1 后端是多模块 Maven 工程

根 `pom.xml` 聚合了 `snowy-common` / `snowy-plugin` / `snowy-plugin-api` / `snowy-web-app`，
启动模块是 `snowy-web-app`，产物 `snowy-web-app/target/snowy-web-app-3.0.0.jar`。
Dockerfile 里用通配符取 jar，避免版本号写死：

```bash
cp "$(ls snowy-web-app/target/snowy-web-app-*.jar | grep -v -e sources -e javadoc | head -1)" /build/app.jar
```

### 2.2 单元测试需要 MySQL + Redis

`snowy-web-app/src/test/java/vip/xiaonuo/MainTest.java` 是 `@SpringBootTest`，
启动完整 Spring 上下文，会连数据库和 Redis。因此 CI 的 backend job 用
GitHub Actions 的 **service 容器**提供依赖：

```yaml
services:
  mysql: { image: mysql:8.0, ... }
  redis: { image: redis:7.0, ... }
```

并通过环境变量切到仓库自带的 `docker` profile（`application-docker.yml`），
注意这个 job 直接跑在 runner 机器上，service 容器只能通过映射端口用 `127.0.0.1` 访问；

| 环境变量 | 值 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `3306` |
| `MYSQL_DB` / `MYSQL_USER` / `MYSQL_PWD` | `snowy` / `root` / `12345678` |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` |

建表脚本 `snowy-web-app/src/main/resources/_sql/snowy_mysql.sql` 在跑测试前导入。

### 2.3 前端 API 地址必须走同源

`snowy-admin-web/src/utils/request.js` 里：

```js
const isDirectMode = !import.meta.env.DEV && sysConfig.API_URL && /^https?:\/\//.test(sysConfig.API_URL)
baseURL: isDirectMode ? sysConfig.API_URL : '/api'
```

只要 `VITE_API_BASEURL` **不是**完整的 http(s) 地址，就走同源的 `/api`。
仓库里的 `.env.production` 写的是 `http://127.0.0.1:82`（绝对地址，会走直连模式）。
所以构建时显式覆盖：

```bash
VITE_API_BASEURL=/api npm run build
```

Vite 中真实环境变量优先级高于 `.env` 文件，实测构建产物里已无 `127.0.0.1:82` 残留。

Nginx 侧用 `proxy_pass http://snowy_backend/;`（带尾部斜杠）把 `/api/xxx` 改写成 `/xxx`，
与开发环境 `vite.config.mjs` 里的 `rewrite` 行为保持一致。

### 2.4 后端没有 actuator

Snowy 未引入 `spring-boot-starter-actuator`，所以健康检查用「能应答 HTTP 即算健康」：

```dockerfile
HEALTHCHECK CMD curl -s -o /dev/null http://localhost:82/ || exit 1
```

`curl` 不带 `-f`，后端返回 404 也算成功，只有连不上才失败。

### 2.5 端口约定

沿用 Snowy 自己的约定：后端容器内 82，前端容器内 80，宿主机映射到 **81**。

---

## 3. CI（持续集成）— `.github/workflows/ci.yml`

| Job | 内容 |
| --- | --- |
| `backend` | Temurin JDK 17 + `mvn -B verify`，带 MySQL/Redis service 容器 |
| `frontend` | Node 20 + `npm ci` + `npm run build`（`VITE_API_BASEURL=/api`） |
| `docker` | 构建后端与前端镜像（**不推送**），依赖上面两个 Job 全绿 |
| `security` | CodeQL 静态分析（`java` 用 `build-mode: manual` 显式编译，`javascript-typescript` 用 `none`） |

---

## 4. CD（持续交付）— `.github/workflows/cd.yml`

- 镜像命名：`ghcr.io/<组织名小写>/snowy-backend`、`ghcr.io/<组织名小写>/snowy-frontend`
- 标签：`sha-<完整提交 SHA>`（部署用，可回滚）、`latest`、`1.2.3` 语义化版本
- 部署：`scp` 上传 `deploy/` 与建表脚本 → `deploy.sh` 拉镜像重启 → 轮询健康检查 →
  失败则回滚到 `.last-deployed-tag` 记录的版本
- 冒烟测试：容器状态、`/healthz`、前端首页、后端 HTTP 响应

---

## 5. 需要配置的 Secrets 与 Variables

### Secrets

| 名称 | 说明 |
| --- | --- |
| `DEPLOY_HOST` | 服务器地址 |
| `DEPLOY_USER` | SSH 用户名（需在 `docker` 用户组） |
| `DEPLOY_SSH_KEY` | SSH 私钥 |
| `DEPLOY_SSH_KNOWN_HOSTS` | 服务器 host 公钥（`ssh-keyscan -p 22 <host>`） |

选填：`DEPLOY_PORT`(22)、`DEPLOY_PATH`(/opt/snowy)、`GHCR_PAT`、`GHCR_USER`。

### Variables

| 名称 | 说明 |
| --- | --- |
| `DEPLOY_ENABLED` | 设为 `true` 才会执行部署，默认只构建推送镜像 |
| `DEPLOY_HEALTH_URL` | 健康检查地址，如 `http://<host>:81/healthz`。**必须是 Variable 而不是 Secret**：`jobs.<id>.environment.url` 的可用上下文里没有 `secrets` |

---

## 6. 服务器准备

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"
sudo mkdir -p /opt/snowy && sudo chown -R "$USER" /opt/snowy
```

服务器不需要装 JDK / Node / Nginx，运行环境全在容器里。

---

## 7. 本地验证

```bash
# 后端（测试需要 MySQL + Redis）
mvn -B verify

# 前端
cd snowy-admin-web && npm ci && VITE_API_BASEURL=/api npm run build

# 镜像
docker build -t snowy-backend:local .
docker build -t snowy-frontend:local -f snowy-admin-web/Dockerfile snowy-admin-web

# 完整编排
cd deploy
REGISTRY=ghcr.io IMAGE_NAMESPACE=local IMAGE_TAG=latest \
  docker compose -f docker-compose.yml up -d
curl http://localhost:81/healthz
```

> 首次启动时 MySQL 容器会自动导入 `initdb/snowy_mysql.sql` 建表，
> 该脚本只在数据卷为空时执行一次。
