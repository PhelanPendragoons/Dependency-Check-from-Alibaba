# 上线问题库

> 用于追踪依赖安全检查平台从"开发可用"到"生产就绪"的所有待办事项。
>
> 状态标记：`🔴 阻断` `🟡 待办` `🟢 低优` `✅ 已完成`

---

## 目录

1. [安全](#1-安全)
2. [数据层](#2-数据层)
3. [异步任务](#3-异步任务)
4. [可观测性](#4-可观测性)
5. [API 规范](#5-api-规范)
6. [测试](#6-测试)
7. [部署与 CI/CD](#7-部署与-cicd)

---

## 1. 安全

### 1.1 认证与授权 🔴

- **状态**：🟡 待办
- **标签**：`security` `auth`
- **关联文件**：全部 Controller（[ProjectController][proj-ctrl]、[ScanTaskController][scan-ctrl]、[ReportController][rpt-ctrl]）
- **描述**：当前所有 API 无需登录即可访问，无 token 校验、无权限控制。
- **建议**：
  - 最小方案：加一个简单的 API Key / JWT 校验拦截器
  - 后续：接入企业 SSO（OAuth2 / OIDC），按角色区分读写权限

### 1.2 NVD API Key 外部化 🔴

- **状态**：🟡 待办
- **标签**：`security` `config`
- **关联文件**：[application.yml][app-yml]
- **描述**：NVD API Key 目前写在配置文件里明文存储，源码泄露即 Key 泄露。
- **建议**：改为从环境变量 `${NVD_API_KEY}` 或 K8s Secret 读取。

### 1.3 文件上传安全 🔴

- **状态**：🟡 待办
- **标签**：`security` `upload`
- **关联文件**：[ProjectController.java][proj-ctrl]
- **描述**：ZIP 上传无文件大小限制、无类型校验、无 zip bomb 防护。
- **建议**：
  - `spring.servlet.multipart.max-file-size` 设置上传上限
  - 校验 `Content-Type` 为 `application/zip`
  - 解压时限制总大小和文件数（防 zip bomb）

### 1.4 CORS 收窄 🟡

- **状态**：🟡 待办
- **标签**：`security` `cors`
- **关联文件**：[CorsConfig.java][cors-config]
- **描述**：开发阶段 CORS 通常是 `*` 全放行，上线需限制为前端的实际域名。

### 1.5 自身依赖安全扫描 🟢

- **状态**：🟡 待办
- **标签**：`security` `dogfooding`
- **描述**：平台自身跑一遍 OWASP Dependency-Check，确认自己的依赖没有已知漏洞。

---

## 2. 数据层

### 2.1 数据库切换 🔴

- **状态**：🟡 待办
- **标签**：`database` `production`
- **关联文件**：[application.yml][app-yml]、[schema.sql][schema-sql]
- **描述**：当前使用 H2 嵌入式内存数据库，服务重启数据全部丢失。
- **建议**：切换为 MySQL 8.x 或 PostgreSQL 15+，配置 HikariCP 连接池参数。

### 2.2 Schema 版本化迁移 🔴

- **状态**：🟡 待办
- **标签**：`database` `migration`
- **关联文件**：[schema.sql][schema-sql]
- **描述**：目前只有一份手写 `schema.sql`，没有 Flyway / Liquibase 做版本化数据库迁移。
- **建议**：引入 Flyway，把 `schema.sql` 转为 `V1__init.sql`，后续变更通过增量脚本管理。

### 2.3 连接池参数调优 🟢

- **状态**：🟡 待办
- **标签**：`database` `performance`
- **描述**：HikariCP 的连接数、超时、最大生命周期等参数目前全默认，需根据线上负载调整。
- **建议**：
  - `maximum-pool-size` → 根据数据库 `max_connections` 计算
  - `connection-timeout`、`idle-timeout`、`max-lifetime` 显式配置

---

## 3. 异步任务

### 3.1 任务状态持久化 🔴

- **状态**：🟡 待办
- **标签**：`async` `reliability`
- **关联文件**：[ScanTaskExecutorService.java][task-exec]
- **描述**：扫描任务用 `ConcurrentHashMap` 在内存中跟踪状态，服务重启后所有进行中任务丢失，已完成任务的状态也不可查。
- **建议**：任务状态写入数据库，启动时从数据库恢复未完成的任务。

### 3.2 扫描失败重试 🟡

- **状态**：🟡 待办
- **标签**：`async` `retry`
- **描述**：扫描失败后没有自动重试机制，需要用户手动重新创建任务。
- **建议**：引入 Spring Retry，对临时性错误（网络超时、NVD 暂时不可用）自动重试 3 次。

### 3.3 并发上限控制 🟡

- **状态**：🟡 待办
- **标签**：`async` `concurrency`
- **关联文件**：[ScanTaskExecutorService.java][task-exec]
- **描述**：没有限制同时执行的扫描任务数，大量项目同时上传可能打满线程池和 CPU。
- **建议**：配置 `ThreadPoolTaskExecutor` 的 `queueCapacity` 和 `maxPoolSize`，超出时排队而非无限制创建线程。

---

## 4. 可观测性

### 4.1 日志配置补全 🟡

- **状态**：🟡 待办
- **标签**：`logging` `production`
- **关联文件**：[application.yml][app-yml]
- **描述**：当前无 `logback-spring.xml`，全走 Spring Boot 默认控制台输出，无文件留存。
- **建议**：
  - 新建 [logback-spring.xml](#) 配置：控制台 + 文件滚动（按天/按大小）
  - 开发 Profile：DEBUG 级别
  - 生产 Profile：INFO 级别，第三方依赖压制到 WARN

### 4.2 请求 TraceId 🟡

- **状态**：🟡 待办
- **标签**：`logging` `tracing`
- **描述**：多请求并发时无法串联同一链路的日志。
- **建议**：用 MDC + Spring `HandlerInterceptor` 自动为每个请求注入 `traceId`，在日志 pattern 里输出。

### 4.3 MyBatis-Plus SQL 日志修复 🟡

- **状态**：🟡 待办
- **标签**：`logging` `mybatis`
- **关联文件**：[application.yml][app-yml]（第 27 行）
- **描述**：当前配置 `log-impl: StdOutImpl` 直接写 stdout，绕过了 Logback，无法用 `logging.level` 控制，也无法写入文件。
- **建议**：删除该行配置，改为用 `logging.level.com.alibaba.dependencycheck.mapper=DEBUG` 统一管理。

### 4.4 潜在 Logback 版本冲突 🔴

- **状态**：🟡 待办
- **标签**：`logging` `dependency`
- **关联文件**：[pom.xml][web-pom]
- **描述**：OWASP `dependency-check-core` 内部锁定 `logback 1.2.13`，而 Spring Boot 3.2.5 带 `logback 1.4.x`。Maven 仲裁会用 Spring Boot 版本，但 API 有差异，可能运行时报错。
- **建议**：在全量测试中重点覆盖扫描引擎链路，或显式在 pom 中排除 dependency-check-core 传递来的旧版 logback。

### 4.5 Actuator 端点审查 🟡

- **状态**：🟡 待办
- **标签**：`monitoring` `actuator`
- **描述**：确认 Actuator 暴露了哪些端点，生产环境只应暴露 `/actuator/health`。
- **建议**：配置 `management.endpoints.web.exposure.include: health`。

### 4.6 指标与告警 🟢

- **状态**：🟡 待办
- **标签**：`monitoring` `metrics`
- **描述**：无 Micrometer 对接 Prometheus/Grafana，无扫描任务成功率/耗时/积压量统计。
- **建议**：引入 `micrometer-registry-prometheus`，自定义指标：`scan.task.count`、`scan.task.duration`、`scan.task.failure.rate`。

---

## 5. API 规范

### 5.1 ResponseBodyAdvice 自动包装 🟡

- **状态**：🟡 待办
- **标签**：`api` `response`
- **关联文件**：全部 Controller、[Result.java][result-java]
- **描述**：目前每个 Controller 手动调用 `Result.success()`，无架构级约束，新人可能遗漏。
- **建议**：写 `ResponseBodyAdvice` 实现类，自动把返回值包进 `Result<T>`；`ResponseEntity` 类型跳过不包装。

### 5.2 文件下载的错误协议统一 🟡

- **状态**：🟡 待办
- **标签**：`api` `response`
- **关联文件**：[ReportController.java][rpt-ctrl]
- **描述**：`viewReport()` 正常返回文件流，异常时 `GlobalExceptionHandler` 返回 JSON，前端需按 `Content-Type` 分支判断。
- **建议**：异常时返回 HTTP 错误码 + `X-Error-Message` 响应头，或保持 JSON body 但约定前端按状态码判断。

### 5.3 PageResult 启用或删除 🟢

- **状态**：🟡 待办
- **标签**：`api` `cleanup`
- **关联文件**：[PageResult.java](src/main/java/com/alibaba/dependencycheck/model/vo/PageResult.java)
- **描述**：`PageResult<T>` 定义了但从未使用，要么在分页接口里用起来，要么删除避免误导。

---

## 6. 测试

### 6.1 Service 层单元测试 🟡

- **状态**：🟡 待办
- **标签**：`testing` `unit`
- **描述**：核心扫描逻辑、报告生成逻辑无单元测试覆盖。
- **建议**：优先覆盖 [ScanEngineService][scan-engine-svc]、[ReportService][rpt-svc]、[ProjectService][proj-svc]。

### 6.2 Controller 层集成测试 🟡

- **状态**：🟡 待办
- **标签**：`testing` `integration`
- **描述**：无 `@WebMvcTest` 覆盖，API 的请求/响应格式、参数校验逻辑未经自动化验证。

### 6.3 端到端冒烟测试 🟢

- **状态**：🟡 待办
- **标签**：`testing` `e2e`
- **描述**：上项目 → 执行扫描 → 查看报告 的完整链路无自动化验证。
- **建议**：用 Testcontainers 或固定测试数据跑一条完整链路。

---

## 7. 部署与 CI/CD

### 7.1 容器化 🔴

- **状态**：🟡 待办
- **标签**：`deploy` `docker`
- **描述**：没有 Dockerfile，无法标准化构建和部署。
- **建议**：写多阶段 Dockerfile（构建阶段用 Maven 镜像，运行阶段用 JRE 精简镜像）。

### 7.2 外部化配置 🔴

- **状态**：🟡 待办
- **标签**：`deploy` `config`
- **关联文件**：[application.yml][app-yml]
- **描述**：数据库连接、API Key、日志路径等硬编码在 application.yml，不同环境需重新打包。
- **建议**：改为 `application-prod.yml` + 环境变量覆盖，或对接 Spring Cloud Config / K8s ConfigMap。

### 7.3 CI/CD 流水线 🟢

- **状态**：🟡 待办
- **标签**：`deploy` `cicd`
- **描述**：无自动化构建、测试、镜像推送、部署流水线。
- **建议**：GitHub Actions 或 Jenkins：`编译 → 测试 → 镜像构建 → 推送镜像仓库 → 部署`

---

## 概览总表

| # | 类别 | 条目 | 优先级 |
|---|---|---|---|
| 1.1 | 安全 | 认证与授权 | 🔴 阻断 |
| 1.2 | 安全 | NVD API Key 外部化 | 🔴 阻断 |
| 1.3 | 安全 | 文件上传限制 | 🔴 阻断 |
| 1.4 | 安全 | CORS 收窄 | 🟡 待办 |
| 1.5 | 安全 | 自身依赖扫描 | 🟢 低优 |
| 2.1 | 数据层 | H2 → MySQL/PG | 🔴 阻断 |
| 2.2 | 数据层 | Schema 迁移工具 | 🔴 阻断 |
| 2.3 | 数据层 | 连接池调优 | 🟢 低优 |
| 3.1 | 异步任务 | 任务状态持久化 | 🔴 阻断 |
| 3.2 | 异步任务 | 失败重试 | 🟡 待办 |
| 3.3 | 异步任务 | 并发上限控制 | 🟡 待办 |
| 4.1 | 可观测性 | logback-spring.xml | 🟡 待办 |
| 4.2 | 可观测性 | TraceId | 🟡 待办 |
| 4.3 | 可观测性 | MyBatis SQL 日志 | 🟡 待办 |
| 4.4 | 可观测性 | Logback 版本冲突 | 🔴 阻断 |
| 4.5 | 可观测性 | Actuator 端点审查 | 🟡 待办 |
| 4.6 | 可观测性 | 指标与告警 | 🟢 低优 |
| 5.1 | API 规范 | ResponseBodyAdvice | 🟡 待办 |
| 5.2 | API 规范 | 文件下载错误协议 | 🟡 待办 |
| 5.3 | API 规范 | PageResult 清理 | 🟢 低优 |
| 6.1 | 测试 | Service 单元测试 | 🟡 待办 |
| 6.2 | 测试 | Controller 集成测试 | 🟡 待办 |
| 6.3 | 测试 | E2E 冒烟测试 | 🟢 低优 |
| 7.1 | 部署 | Dockerfile | 🔴 阻断 |
| 7.2 | 部署 | 外部化配置 | 🔴 阻断 |
| 7.3 | 部署 | CI/CD 流水线 | 🟢 低优 |

---

## 文件索引

[app-yml]: ../src/main/resources/application.yml
[web-pom]: ../pom.xml
[schema-sql]: ../src/main/resources/schema.sql
[result-java]: ../src/main/java/com/alibaba/dependencycheck/model/vo/Result.java
[proj-ctrl]: ../src/main/java/com/alibaba/dependencycheck/controller/ProjectController.java
[scan-ctrl]: ../src/main/java/com/alibaba/dependencycheck/controller/ScanTaskController.java
[rpt-ctrl]: ../src/main/java/com/alibaba/dependencycheck/controller/ReportController.java
[task-exec]: ../src/main/java/com/alibaba/dependencycheck/service/ScanTaskExecutorService.java
[scan-engine-svc]: ../src/main/java/com/alibaba/dependencycheck/service/ScanEngineService.java
[rpt-svc]: ../src/main/java/com/alibaba/dependencycheck/service/ReportService.java
[proj-svc]: ../src/main/java/com/alibaba/dependencycheck/service/ProjectService.java
[cors-config]: ../src/main/java/com/alibaba/dependencycheck/config/CorsConfig.java
