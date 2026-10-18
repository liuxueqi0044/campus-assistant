# 校园助手后端（M00 工程底座）

本目录是校园助手后端基线：在芋道 `ruoyi-vue-pro` 的 `master-jdk17` 分支固定提交上裁剪出的最小可构建底座，
加上校园自有模块 `campus-common`。M00.01 只做“依赖组合与裁剪”，不包含任何校园业务表与业务规则。

## 1. 上游来源与版本锁定

| 项 | 取值 |
|---|---|
| 官方仓库 | `https://github.com/YunaiV/ruoyi-vue-pro` |
| 分支 | `master-jdk17` |
| 固定提交 | `8e43004cf68a405cd3485f98f8a539b97ca6544a` |
| 上游版本号 | `2026.08-SNAPSHOT`（浮动，不可直接作为发布版本） |
| 本仓版本号 | `2026.08-campus.1`（`pom.xml` 与 `yudao-dependencies/pom.xml` 必须一致） |
| 许可证 | MIT，见 `LICENSE` |
| Java 基线 | 17（`java.version`） |
| Spring Boot | 3.5.15（`yudao-dependencies` 中 `spring.boot.version`） |
| 数据库 | MySQL 8.4 / InnoDB（基线脚本见 `sql/mysql`） |

上游提交与本地版本号同时写在根 `pom.xml` 的 `campus.upstream.*` 属性中，构建产物可据此追溯到源码。

## 2. 保留与裁剪的模块

保留（参与构建与运行）：

`yudao-dependencies`、`yudao-framework`、`yudao-module-system`、`yudao-module-infra`、
`yudao-module-member`、`yudao-module-pay`、`yudao-server`、`campus-common`、`sql/mysql`。

裁剪（既不参与构建，也不作为运行依赖）：

`bpm`、`report`、`mp`、`mall`、`crm`、`erp`、`iot`、`mes`、`wms`、`hrm`、`fms`、`pms`、`im`、`ai`，
以及非 MySQL 方言的 `sql/{db2,dm,highgo,kingbase,opengauss,oracle,postgresql,sqlserver}`。

裁剪优先采用“不启用模块、不作为依赖”的方式，没有改写上游框架内部实现；后续如需引入某个被裁剪模块，
必须同时更新本文件、根 `pom.xml` 注释与依赖清单，不允许直接把目录复制回来。

## 3. 相对上游的本地修改（需要在上游升级时逐条重放）

1. `pom.xml`：模块列表裁剪为上述集合；`revision` 由 `2026.08-SNAPSHOT` 固定为 `2026.08-campus.1`；
   新增 `campus.upstream.*` 追溯属性。
2. `yudao-dependencies/pom.xml`：`revision` 同步为 `2026.08-campus.1`。
3. `yudao-server/pom.xml`：启用 `yudao-module-member`、`yudao-module-pay`，新增 `campus-common` 依赖。
4. 新增 `campus-common` 模块：启动配置守卫与就绪检查（M00.01 交付物）。
5. 新增 `yudao-server/src/main/resources/application-prod.yaml`：生产 profile，数据库/缓存/支付全部由环境变量提供，
   不给任何默认凭据。
6. `yudao-server/src/main/resources/application.yaml`：新增 `campus.payment.gateway: mock`（开发/测试默认值），
   并把上游示例密钥改为环境变量或无效占位值。
7. `sql/mysql/ruoyi-vue-pro.sql`：移除上游已删除的第三方社交客户端演示凭据种子。
8. 未修改任何上游 Java 源码。

## 4. 环境变量

开发（`local` / `dev`）沿用上游本地默认值，无需外部变量；生产（`prod`）必须显式提供下列变量。

| 变量 | 生产必填 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 是 | 取值 `prod`（覆盖 `application.yaml` 中的 `local` 默认值） |
| `CAMPUS_DB_URL` | 是 | JDBC 地址，例如 `jdbc:mysql://host:3306/campus_assistant?...` |
| `CAMPUS_DB_USERNAME` | 是 | 数据库账号 |
| `CAMPUS_DB_PASSWORD` | 是 | 数据库口令；禁止使用 `123456`/`root` 等开发默认口令 |
| `CAMPUS_REDIS_HOST` | 是 | 缓存服务地址（Valkey/Redis 协议） |
| `CAMPUS_REDIS_PORT` | 否 | 默认 `6379` |
| `CAMPUS_REDIS_DATABASE` | 否 | 默认 `0` |
| `CAMPUS_REDIS_PASSWORD` | 是 | 缓存口令；生产不允许空默认值 |
| `CAMPUS_MYBATIS_ENCRYPTOR_PASSWORD` | 是 | MyBatis 字段加密密钥，不得复用示例值 |
| `CAMPUS_PAYMENT_GATEWAY` | 是 | 支付网关；取值 `mock` 在生产会被启动守卫拒绝 |
| `CAMPUS_PAYMENT_ORDER_NOTIFY_URL` | 是 | 支付结果公网回调 URL |
| `CAMPUS_PAYMENT_REFUND_NOTIFY_URL` | 是 | 退款结果公网回调 URL |
| `CAMPUS_PAYMENT_TRANSFER_NOTIFY_URL` | 是 | 转账结果公网回调 URL |
| `CAMPUS_WECHAT_MP_APP_ID` | 是 | 微信公众号 App ID（上游 starter 的启动依赖） |
| `CAMPUS_WECHAT_MP_SECRET` | 是 | 微信公众号 Secret |
| `CAMPUS_WECHAT_MINIAPP_APP_ID` | 是 | 微信小程序 App ID |
| `CAMPUS_WECHAT_MINIAPP_SECRET` | 是 | 微信小程序 Secret |
| `CAMPUS_SERVER_PORT` | 否 | 默认 `48080` |

变量模板见 `.env.example`。缺少任一生产必填变量时，启动会以 `CONFIGURATION_INVALID` 失败，
日志与异常消息只出现变量名与原因标识，不出现取值。

## 5. 构建

Windows 前置：仓库中上游源码存在超过 260 字符的相对路径，需在本仓库启用长路径支持
（该仓库已设置，换机器克隆后需重新执行一次）：

```powershell
git config core.longpaths true
```

需要 JDK 17 与 Maven 3.9+：

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk-17
cd backend
mvn -B -DskipTests package          # 构建全部保留模块
mvn -B -pl campus-common -am test   # 只跑 M00.01 的 A 级用例
```

```powershell
# Windows PowerShell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
Set-Location backend
mvn -B -DskipTests package
mvn -B -pl campus-common -am test
```

构建产物：各模块 `target/*.jar`，其中 `yudao-server/target/yudao-server.jar` 为可执行应用。
完整依赖解析结果见 `docs/dependency-baseline.txt`（供“两次全新目录构建，依赖解析版本一致”比对）。

## 6. 启动与健康检查

```bash
# 开发
SPRING_PROFILES_ACTIVE=local java -jar yudao-server/target/yudao-server.jar

# 生产（环境变量齐全时）
SPRING_PROFILES_ACTIVE=prod java -jar yudao-server/target/yudao-server.jar
```

- `GET /health/readiness`：就绪检查。所有调用者都只返回 `{"status":"UP|DOWN"}`，数据库和缓存均明确
  可用时才返回 `UP`。该接口通过 `@PermitAll` 放开，供登录前探活；内部诊断信息由后续运维模块通过
  专用后台权限提供，普通学生登录不会增加可见信息。

## 7. 当前状态与已知限制

- 已验证：完整模块构建、20 个 A 级单元用例、生产配置拒绝、MySQL 8.4、Valkey 鉴权/读写、
  Redisson 的 TTL/Lua/锁兼容，以及 local/prod 的 HTTP readiness。证据见 `docs/M00.01-verify.md`。
- 未验证：小程序真机、真实微信账号、真实支付商户与公网回调；这些需要前端或外部正式凭据，不得用占位值记为通过。
- 本目录不含部署用真实密钥；本地与开发 profile 只保留无效占位值，正式配置必须经环境变量注入。

## 8. M00.02 API 契约与错误规范

校园入口的公共协议在仓库根 `contracts/`：统一信封 `code/message/data/traceId`、错误码登记表
`contracts/openapi/campus-error-codes.csv`、OpenAPI 文档、事件 Schema 与版本化 stub。

后端机制在 `campus-common`：

| 机制 | 位置 |
|---|---|
| 信封与错误规范 | `...common.api.CampusResponse`、`CampusErrorCodes`、`CampusApiException` |
| 统一异常映射（按包限定） | `...common.api.CampusExceptionHandler` |
| 未匹配地址 404 | `...common.api.CampusUnmappedPathExceptionHandler` |
| traceId 生成与回显 | `...common.api.CampusTraceContext`、`CampusTraceFilter` |
| 类型规则 | `...common.api.json.MoneyFen`（整数分）、`CampusIdString`（ID 为字符串）、`CampusTimestamp`（带偏移时间） |
| 分页 | `...common.api.CampusPageParam`（默认 20，硬上限 100）、`CampusPageResult` |
| 事件信封 | `...common.event.CampusEventEnvelope` 及其校验与版本策略 |

规则要点：

- 校园入口为 `/app-api/campus/v1`、`/admin-api/campus/v1`；上游 `system`／`infra` 入口仍使用 yudao 原有信封。
- 金额必须是整数分，小数与字符串一律 400；时间必须带时区偏移，`@CampusTimestamp` 不可省略
  （上游 Jackson 会把未标注的时间输出成 epoch 数字）。
- 错误响应区分「可重试」与「需用户修正」（`data.requiredAction`），409 冲突给出 `currentVersion`／`currentState`。
- 错误码只改代码或只改文档都会失败：`CampusErrorCodesContractTest` 强制两边一致。

验收记录：`docs/M00.02-verify.md`（A 级通过；已覆盖真实安全链层 401/403 与 OpenAPI 语义校验；
TypeScript 客户端生成以及后续业务模块的真实联调仍留到对应模块验收）。
