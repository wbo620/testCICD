# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Snowy 后端：Spring Boot 3.5 / JDK 17，多模块 Maven 工程
# 启动模块为 snowy-web-app，产物是可执行 jar
# ---------------------------------------------------------------------------

# ---- 构建阶段 ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先复制 pom, 让依赖层可以被缓存复用；go-offline 对个别插件元数据比较敏感,
# 失败不阻断构建, 真正需要的构件会在下面的 package 阶段补齐。
COPY pom.xml ./
RUN mvn -B -e dependency:go-offline || true

# 再复制全部源码
COPY . .

# 跳过测试: 测试需要 MySQL + Redis, 由 CI 的 backend job 用 service 容器提供
RUN mvn -B -DskipTests package \
    && cp "$(ls snowy-web-app/target/snowy-web-app-*.jar | grep -v -e sources -e javadoc | head -1)" /build/app.jar

# ---- 运行阶段 ----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# curl 用于 HEALTHCHECK；tzdata 让容器内时区可配置
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --create-home app

WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar

# Snowy 的 logback-spring.xml 把日志写到 ${log.path:app-log}, 默认是工作目录下的
# 相对路径 app-log/。容器以非 root 用户运行, 而 /app 归 root 所有, Logback
# 建不出这个目录就会直接让应用启动失败:
#     Logback configuration error detected:
#     openFile(app-log/log_error.log,true) call failed. No such file or directory
# 这里提前建好并把属主交给 app 用户, 同时用 LOG_PATH 环境变量固定成绝对路径
# (Spring 的宽松绑定: LOG_PATH -> log.path), 方便部署时挂卷。
RUN mkdir -p /app/app-log && chown -R app:app /app

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=docker \
    LOG_PATH=/app/app-log \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 82
USER app

# Snowy 未引入 actuator, 这里只要后端能应答 HTTP 即视为健康
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD curl -s -o /dev/null http://localhost:82/ || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
