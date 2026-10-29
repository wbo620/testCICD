# GRIB2 要素查询服务（Spring Boot 3.5.9 / JDK 17）

读取 `gfs_20260701_00z_f000.grib2`，通过 **经度 + 纬度 + 时间** 查询 `Visibility_surface`（地面能见度）等要素的数值。

## 文件内容

netCDF-Java CDM 解析该 GRIB2 文件后得到 7 个变量、3 个维度：

| 变量 | 维度 | 说明 |
| --- | --- | --- |
| `Visibility_surface` | [time, lat, lon] | 地面能见度（m），要素主变量 |
| `Low_cloud_cover_low_cloud` | [time, lat, lon] | 低云量（%） |
| `lat` | [lat] | 纬度轴，721 点，90 → -90（0.25°） |
| `lon` | [lon] | 经度轴，1440 点，0 → 359.75（0.25°） |
| `time` | [time] | 时次轴，1 个时次：`2026-07-01T00:00:00Z`（f000） |
| `reftime` | - | 参考时间 |
| `LatLon_Projection` | - | 网格投影信息 |

文件内含 2 条 GRIB2 message（Visibility 与 Low cloud cover），打包方式为
`grid_complex_spatial_differencing`（模板 5.3），1440×721 全球 0.25° 经纬网。

## 运行

```bash
# 使用项目内的本地仓库与镜像配置（阿里云镜像在本机 JDK 证书链下不可用）
mvn -s maven-settings.xml -Dmaven.repo.local=.m2repo spring-boot:run
```

或在 IDE 中直接运行 `org.example.grib.GribApplication`（默认端口 8080）。

配置文件 `src/main/resources/application.yml`：

```yaml
grib:
  file: gfs_20260701_00z_f000.grib2   # 相对运行目录，或写绝对路径
  default-variable: Visibility_surface
```

> 提示：netCDF-Java 会在 GRIB2 文件旁生成 `.gbx9` / `.ncx3` 索引文件（加速后续读取），可随时删除重建。

## 接口

### 按经纬度 + 时次取值

```bash
curl "http://localhost:8080/api/grib/value?lat=39.9&lon=116.4&time=2026-07-01T00:00:00Z"
```

```json
{
  "variable": "Visibility_surface",
  "requestedLat": 39.9, "requestedLon": 116.4,
  "requestedTime": "2026-07-01T00:00:00Z",
  "matchedLat": 40.0, "matchedLon": 116.5,
  "matchedTime": "2026-07-01T00:00:00Z",
  "latIndex": 200, "lonIndex": 466, "timeIndex": 0,
  "value": 24135.11328125,
  "units": "m",
  "exact": false
}
```

也可用 POST：

```bash
curl -X POST http://localhost:8080/api/grib/query \
  -H "Content-Type: application/json" \
  -d '{"variable":"Visibility_surface","lat":39.9,"lon":116.4,"time":"2026-07-01T00:00:00Z"}'
```

### 其它接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/grib/variables` | 列出文件中的全部变量及元信息 |
| GET | `/api/grib/grid/{variable}` | 网格范围、格点数与时次（`?axes=true` 返回完整经纬度轴） |
| GET | `/api/grib/value?lat=&lon=&time=` | 查询要素值，`time` 可省略（取文件第一个时次） |
| POST | `/api/grib/query` | 同上，JSON 请求体 |

时次支持 `2026-07-01T00:00:00Z`、`2026-07-01 00:00:00`、`2026-07-01` 或 epoch 毫秒。

## 实现要点

- **读取方式**：`ucar.nc2.NetcdfFile.open()` 直接打开 GRIB2 文件（netCDF-Java CDM 4.6.21 + `edu.ucar:grib` 模块，纯 Java，无 native 依赖）。
- **定位规则**：经纬度为**最近邻**匹配（本文件网格 0.25°，即 39.9° 会落到 40.0°）；响应里同时返回请求值与实际命中的格点，`exact` 标识是否精确命中。
- **时次**：CDM 把时次表示为 `Hour since 2026-07-01T00:00:00Z`，由 `TimeUnits` 换算为 `Instant`；时次优先精确匹配，否则取最近时次并回传 `matchedTime`。
- **性能**：文件解析一次后常驻内存，坐标轴缓存；取值走 CDM 的按段读取（`read(origin, shape)`），失败时回退到整块读取。

## 依赖说明（踩坑记录）

- 原 `pom.xml` 中的 `edu.ucar:netcdf:4.3.22` 与 `edu.ucar:cdm:4.6.21` **版本错配**：两个 jar 内含同名的 `ucar.nc2.*` 类，classpath 冲突会导致编译期报
  “找不到符号 ucar.nc2.Variable”。纯 Java 读 GRIB2 不需要 JNI 原生库，故已移除 `netcdf` 依赖，仅保留 `cdm` + `grib`。
- `edu.ucar:cdm:4.6.21` 中 GRIB 读取代码已拆分到独立构件 `edu.ucar:grib:4.6.21`，两者都要引入。
- 4.6.x 的 `ucar.nc2.Variable` 不是泛型；`ucar.nc2.time.DateHelper` / `DateUnit` 已被 `CalendarDateUnit` 取代，本项目自实现 `TimeUnits` 处理时间单位。

## 测试

```bash
mvn -s maven-settings.xml -Dmaven.repo.local=.m2repo test
```

`Grib2ServiceTest` 校验了变量清单、网格范围与时次，并比对了具体格点上的能见度数值
（期望值取自 ECMWF eccodes 对同一文件的解码结果）。
---

## CI/CD

项目已接入 GitHub Actions 全流程自动化：

| 工作流 | 文件 | 触发条件 | 内容 |
| --- | --- | --- | --- |
| CI | `.github/workflows/ci.yml` | PR、推送到 `main`/`master`/`develop` | 后端测试（JDK 17）、前端 lint/类型检查/单测/构建、镜像构建验证、CodeQL 扫描 |
| CD | `.github/workflows/cd.yml` | 推送到 `main`/`master`、打 `v*` 标签、发布 Release | 构建并推送镜像到 GHCR，SSH 部署到服务器，健康检查失败自动回滚 |
| Dependabot | `.github/dependabot.yml` | 每周 | 自动检查 Maven / npm / Docker / Actions 依赖更新 |

部署架构：Nginx（前端镜像）静态托管 Vue 产物并把 `/api` 反向代理到 Spring Boot 后端容器，
两个容器由 `deploy/docker-compose.yml` 编排，镜像在 CI 中构建，服务器只负责拉取与重启。

**完整的配置步骤、Secrets 清单与排错说明见 [`docs/CI_CD.md`](docs/CI_CD.md)。**

### 本地构建

```bash
# 后端
mvn -B -s maven-settings.xml verify

# 前端
cd frontend && npm ci && npm run build

# 镜像
docker build -t grib-backend:local .
docker build -t grib-frontend:local -f frontend/Dockerfile frontend
```
