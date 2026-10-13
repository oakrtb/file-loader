# file-loader

从 **本地 FS / 阿里云 OSS / 火山 TOS** 加载文件资源到内存，并通过 `MemoryView` 适配业务数据结构；支持定时刷新、本地 watch + 轮询兜底、滚动日期前缀、本地缓存兜底、大文件落盘清理、并行下载、manifest 原子切换、Micrometer 指标与 Spring Boot starter。

- **Java**: 21  
- **GroupId**: `com.oakrtb`  
- **版本**: `0.1.0-SNAPSHOT`

## 模块

| 模块 | 说明 |
|------|------|
| `file-loader-api` | SPI / 契约（`ContentSource`、`MemoryView`、`InMemoryStore`、`PrefixResolver`…） |
| `file-loader-core` | 内存存储、View、本地源、`FileLoader` |
| `file-loader-oss` | OSS 实现（ServiceLoader） |
| `file-loader-tos` | TOS 实现（ServiceLoader） |
| `file-loader-metrics` | Micrometer `RefreshListener` + Prometheus Exporter |
| `file-loader-spring-boot-starter` | Spring Boot 3 自动配置（含 oss/tos/metrics/prometheus） |

业务侧推荐：

```xml
<!-- 纯代码 -->
<dependency>
  <groupId>com.oakrtb</groupId>
  <artifactId>file-loader-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>com.oakrtb</groupId>
  <artifactId>file-loader-oss</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- 或 Spring Boot -->
<dependency>
  <groupId>com.oakrtb</groupId>
  <artifactId>file-loader-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 文件变更自动更新

| 场景 | 做法 |
|------|------|
| **本地目录** | `.watchLocalChanges(true)`：`WatchService` 监听增删改，防抖后 `refresh()`；默认再开 **1 分钟轮询兜底**（`watchPollFallback`，可关） |
| **OSS / TOS** | `.refreshEvery(Duration.ofSeconds(30))`：定时轮询 + ETag 增量 |
| **云事件** | OSS/TOS 事件通知 → MQ → 业务里调用 `loader.store().refresh()` |

```java
FileLoader.builder()
    .local(Path.of("."))
    .prefix("conf")
    .watchLocalChanges(true)                 // WatchService
    // .watchPollFallback(false)             // 默认 true → 自动 refreshEvery(1m)
    .addView("json", new JsonNodeMapView())
    .build();
```

## 滚动日期目录

按天无限涨目录时，用 `rollingDatePrefix`：每次 refresh 解析为「今天」路径（例 `task/2026-10-12/`），watch 挂在 `task/` 根上。

```java
FileLoader.builder()
    .local(Path.of("."))
    .rollingDatePrefix("task/", "yyyy-MM-dd")
    .watchLocalChanges(true)
    .build();
```

环境变量：`FILE_LOADER_ROLLING_DATE_PREFIX` + 可选 `FILE_LOADER_DATE_PATTERN`（默认 `yyyy-MM-dd`）。

## 快速开始

```java
try (FileLoader loader = FileLoader.builder()
        .local(Path.of("."))
        .prefix("conf")
        .watchLocalChanges(true)
        .cacheDir(Path.of(".file-loader-cache"))
        .cacheFallback(true)
        .addView("json", new JsonNodeMapView())
        .addView("app", new JsonObjectView<>("conf/app.json", AppConfig.class))
        .build()) {
    loader.start();
    Optional<SourcedValue<AppConfig>> app = loader.<Optional<SourcedValue<AppConfig>>>view("app").get();
    // app.get().path()  -> "conf/app.json"
    // app.get().value() -> AppConfig
}
```

OSS / TOS（超时与重试走 `ObjectStoreClientOptions`）：

```java
FileLoader.builder()
    .oss(ObjectStoreConfig.oss(endpoint, ak, sk, bucket,
            new ObjectStoreClientOptions(10_000, 60_000, 3, 200)))
    .prefix("conf/")
    .refreshEvery(Duration.ofSeconds(30))
    .build();
```

## Spring Boot

```yaml
oakrtb:
  file-loader:
    enabled: true
    source: local          # local | oss | tos
    local-root: .
    prefix: conf/
    # rolling-date-prefix: task/
    # date-pattern: yyyy-MM-dd
    watch-local-changes: true
    watch-poll-fallback: true
    cache-dir: .file-loader-cache
    cache-fallback: true
```

存在 `MeterRegistry` bean 时自动挂上 `MicrometerRefreshListener`。

业务自定义 View 用 `ViewRegistration` Bean 注册（starter 自动收集），无需改组装代码：

```java
@Bean
ViewRegistration appView() {
    return ViewRegistration.of("app", new JsonObjectView<>("conf/app.json", AppConfig.class));
}
```

非 Spring 也可：`FileLoader.builder().addView(reg).addViews(list)`。

## Micrometer / Prometheus

### 非 Spring：内嵌 Exporter（`/metrics`）

```java
try (PrometheusMetricsExporter exporter = PrometheusMetricsExporter.start(9464)) {
    try (FileLoader loader = FileLoader.builder()
            .local(root)
            .addListener(new MicrometerRefreshListener(exporter.registry()))
            .build()) {
        loader.start();
        // scrape: http://host:9464/metrics
    }
}
```

指标示例：`file_loader_refresh_total{result="success|failure|skipped"}`、`file_loader_refresh_duration_*`。

### Spring Boot：Actuator（推荐）

依赖 `micrometer-registry-prometheus` + Actuator，暴露：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
```

抓取地址：`/actuator/prometheus`（file-loader 在存在 `MeterRegistry` 时自动挂 `MicrometerRefreshListener`）。

### Spring Boot：可选独立 scrape 端口

需要经典 exporter 端口（如火山 VMP 拉 `host:9464/metrics`）时：

```yaml
oakrtb:
  file-loader:
    prometheus:
      enabled: true
      host: 0.0.0.0
      port: 9464
      path: /metrics
```

要求 classpath 上有 `PrometheusMeterRegistry`（引入 `micrometer-registry-prometheus`）。

## 配置项（环境变量）

| Key | 说明 |
|-----|------|
| `FILE_LOADER_SOURCE` | `local` / `oss` / `tos` |
| `FILE_LOADER_LOCAL_ROOT` | 本地根目录 |
| `FILE_LOADER_ENDPOINT` / `ACCESS_KEY` / `ACCESS_SECRET` / `BUCKET` / `REGION` | 对象存储 |
| `FILE_LOADER_PREFIX` | 固定前缀 |
| `FILE_LOADER_ROLLING_DATE_PREFIX` | 滚动日期前缀基路径 |
| `FILE_LOADER_DATE_PATTERN` | 默认 `yyyy-MM-dd` |
| `FILE_LOADER_REFRESH_INTERVAL` | 如 `30s` / `5m` |
| `FILE_LOADER_WATCH_LOCAL` | 本地 WatchService |
| `FILE_LOADER_WATCH_POLL_FALLBACK` | watch 时无配 interval 则默认 1m 轮询，默认 `true` |
| `FILE_LOADER_CACHE_DIR` / `CACHE_FALLBACK` / `FAIL_FAST` | 缓存与启动策略 |
| `FILE_LOADER_HEAP_THRESHOLD_BYTES` / `SPILL_DIR` | 大文件落盘；刷新后清理孤儿 spill |
| `FILE_LOADER_RETAIN_RAW_CONTENT` | 默认 `true`；`false` 时 View 解析后 Store 只留 meta，丢原始 bytes |
| `FILE_LOADER_DOWNLOAD_CONCURRENCY` | 并行下载数 |
| `FILE_LOADER_MANIFEST_ENABLED` / `MANIFEST_NAME` | manifest 原子切换 |
| `FILE_LOADER_CONNECT_TIMEOUT_MS` / `SOCKET_TIMEOUT_MS` / `MAX_ATTEMPTS` / `RETRY_BACKOFF_MS` | 客户端超时重试 |

## Manifest（多文件原子切换）

```json
{
  "version": "20261012-1",
  "files": [
    { "path": "conf/a.json" },
    { "path": "conf/b.json" }
  ]
}
```

## 构建

**必须使用 JDK 21+**。

```bash
sdk env
java -version
mvn clean install
```

可选真实桶集成测试（默认跳过；mock 契约测试始终运行）：

```bash
FILE_LOADER_IT=true FILE_LOADER_SOURCE=oss \
  FILE_LOADER_ENDPOINT=... FILE_LOADER_ACCESS_KEY=... \
  FILE_LOADER_ACCESS_SECRET=... FILE_LOADER_BUCKET=... \
  FILE_LOADER_PREFIX=conf/ \
  mvn -pl file-loader-oss test -Dtest=OssLiveIT
```
