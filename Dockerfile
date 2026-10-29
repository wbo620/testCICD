# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# 后端：Spring Boot 3.5 / JDK 17
# ---------------------------------------------------------------------------

# ---- 构建阶段 ----------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只复制 pom 与仓库配置，让依赖层可以被 Docker 缓存复用
COPY pom.xml maven-settings.xml ./
RUN mvn -B -s maven-settings.xml dependency:go-offline

# 再复制源码与数据文件
COPY src ./src
COPY gfs_20260701_00z_f000.grib2 ./data/gfs_20260701_00z_f000.grib2

RUN mvn -B -s maven-settings.xml -DskipTests package \
    && cp target/testProject.jar /build/app.jar

# ---- 运行阶段 ----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# curl 供 HEALTHCHECK 使用；tzdata 让容器内时区可配置
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

# 以非 root 用户运行
RUN groupadd --system app && useradd --system --gid app --create-home app

WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
COPY --from=build /build/data /app/data

ENV TZ=Asia/Shanghai \
    GRIB_FILE=/app/data/gfs_20260701_00z_f000.grib2 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
USER app

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
