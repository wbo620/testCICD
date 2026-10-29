# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Snowy 前端：Vue3 + Vite 构建产物 + Nginx 静态托管与反向代理
# ---------------------------------------------------------------------------

# ---- 构建阶段 ----------------------------------------------------------
FROM node:25-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# 生产环境用同源相对地址 /api，由 Nginx 转发到后端容器。
# 注意：真实环境变量优先级高于 .env.production，这里能覆盖掉里面的 127.0.0.1:82。
ARG VITE_API_BASEURL=/api
ENV VITE_API_BASEURL=${VITE_API_BASEURL}

RUN npm run build

# ---- 运行阶段 ----------------------------------------------------------
FROM nginx:stable-alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
