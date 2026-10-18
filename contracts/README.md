# 校园助手对外契约（M00.02）

- 契约版本：`2026.08-campus.1`（与后端 `CampusApiContract.VERSION` 一致）
- 状态：M00.02 已实现公共机制并通过 A 级验收；业务字段清单由各模块在 M01–M11 追加
- 变更规则：本目录是 HTTP、事件与适配器契约的唯一入口；代码中的常量、错误码、事件字段都必须能在这里找到对应条目

## 1. 入口

| 入口 | 前缀 | 使用者 | 鉴权 |
|---|---|---|---|
| 学生端 | `/app-api/campus/v1` | 小程序（M10） | Bearer Token（`Authorization`） |
| 管理端 | `/admin-api/campus/v1` | 后台（M11） | Bearer Token + 权限注解 |
| 回调 | 各渠道独立定义（M08） | 微信、支付 | 渠道验签，不使用用户 Token |
| 运维探针 | `/health/readiness`（M00.01） | 部署平台 | 匿名可访问，只给聚合状态 |

上游 `system`、`infra` 等既有接口保持 yudao 原有信封（`code/msg/data`），不在本契约范围内。

## 2. 响应信封

校园入口固定返回：

```json
{
  "code": 0,
  "message": "",
  "data": {},
  "traceId": "3a513e8f2123fd3e87f82803508c2bce"
}
```

- `code`：0 表示成功；错误码取值见第 3 节。
- `message`：用户可阅读的提示，成功时为空字符串。
- `data`：业务数据；错误时为 `CampusErrorDetail`（可重试性、字段错误、冲突版本）。
- `traceId`：**成功与失败都必须非空**，同时通过响应头 `X-Trace-Id` 回显。客户端自带的
  `X-Trace-Id` 不可信且不会直接沿用；可信网关应使用标准 trace context 建立 OpenTelemetry 链路，
  服务端优先使用活动链路号，否则生成新编号。

字段类型规则：

| 项 | 规则 | 强制方式 |
|---|---|---|
| 标识 ID | JSON 中恒为字符串；数字 token 直接 400 | 公开 DTO 使用 `String` 并标注 `@CampusIdString` |
| 金额 | 整数分（`CNY_FEN`），禁止小数、字符串与元 | 字段类型使用 `MoneyFen` |
| 时间 | RFC 3339 且带偏移，如 `2026-09-17T10:15:30+08:00` | 字段标注 `@CampusTimestamp` |
| 分页 | 默认 `pageNo=1`、`pageSize=20`，硬上限 100 | `CampusPageParam` |

> 时间格式必须显式标注：上游 `application.yaml` 打开了
> `spring.jackson.serialization.write-dates-as-timestamps`，未标注的时间字段会输出 epoch 数字。

## 3. 错误码与 HTTP 状态

数值编码冻结在 [`openapi/campus-error-codes.csv`](openapi/campus-error-codes.csv)；
后端登记表 `CampusErrorCodes` 与文件逐条一致，由 `CampusErrorCodesContractTest` 自动校验，
不允许只改文档或只改代码。

HTTP 状态映射：

| HTTP | 语义 | 错误码 |
|---|---|---|
| 400 | 参数错误、金额单位错误、超过分页上限、事件信封不合规 | `CAMPUS_REQUEST_INVALID`、`CAMPUS_MONEY_FORMAT_INVALID`、`CAMPUS_PAGE_SIZE_EXCEEDED`、`CAMPUS_EVENT_*` |
| 401 | 未认证 | `CAMPUS_AUTH_REQUIRED` |
| 403 | 无操作权限 | `CAMPUS_PERMISSION_DENIED` |
| 404 | 资源不存在或调用方不可见（不区分，避免探测存在性） | `CAMPUS_RESOURCE_NOT_FOUND` |
| 409 | 版本冲突、幂等同键异体、命令处理中、有界事务重试耗尽 | `CAMPUS_VERSION_CONFLICT`、`CAMPUS_IDEMPOTENCY_CONFLICT`、`CAMPUS_COMMAND_IN_PROGRESS`、`CAMPUS_RETRYABLE_CONFLICT` |
| 429 | 限流 | `CAMPUS_RATE_LIMITED` |
| 500 | 未捕获异常（脱敏，只给 traceId） | `CAMPUS_INTERNAL_ERROR` |
| 501 | 事件类型或版本未被消费者支持 | `CAMPUS_EVENT_VERSION_UNSUPPORTED` |

错误响应的 `data` 只包含恢复所需信息，例如：

```json
{
  "code": 1040000010,
  "message": "数据已被其他操作修改，请刷新后重试",
  "data": {
    "retryable": false,
    "requiredAction": "USER_FIX",
    "currentVersion": "7",
    "currentState": "ACCEPTED"
  },
  "traceId": "3a513e8f2123fd3e87f82803508c2bce"
}
```

- `retryable` / `requiredAction`：区分「原样重试」与「必须用户修正」，客户端不得自行猜测。
- 409 在调用方已有权限时返回 `currentVersion` / `currentState`。
- 响应体不含异常类名、堆栈、数据库地址或请求体原文。

## 4. 幂等与版本

- 敏感写操作必须带 `Idempotency-Key` 请求头，必要时在请求体带 `expectedVersion`。
- 同键同体返回首次结果并标记重放；同键异体返回 409 `CAMPUS_IDEMPOTENCY_CONFLICT`。
- 版本冲突返回 409 `CAMPUS_VERSION_CONFLICT`，并在 `data` 中给出当前版本与状态。
- 有界数据库锁重试耗尽返回 409 `CAMPUS_RETRYABLE_CONFLICT`（`requiredAction=RETRY`）；
  响应不暴露内部 operation、SQL 或异常链，调用方先按业务查询接口核实结果。
- 幂等实现细节与表结构由 M00.04 交付，本目录只冻结对外语义。

## 5. 事件契约

- 信封字段：`eventId`、`type`、`version`、`aggregateId`、`aggregateVersion`、`schoolId`、`occurredAt`、`traceId`、`payload`。
- JSON Schema：[`events/envelope.schema.json`](events/envelope.schema.json)；规则说明：[`events/README.md`](events/README.md)。
- 载荷禁止证件、取件码等敏感值；键名归一化后匹配即拒绝。
- 未知类型或版本必须进入可观察失败（501 + 死信 + 人工处理），不得按旧结构猜测执行。

## 6. 存根与契约用例

[`stubs/`](stubs/README.md) 提供版本化样例：成功、拒绝、冲突、超时、重复、乱序。
相邻模块未完成时用它独立验收；真实实现必须通过同一组用例（B 级验收要求）。

## 7. 客户端生成

`openapi/app.yaml` 与 `openapi/admin.yaml` 是 M10／M11 生成 TypeScript 客户端的唯一输入，
前端不得手写第二套金额与 ID 类型。

```bash
# 需要 Node 工具链；本机未验证（属 B 级验收项，见 backend/docs/M00.02-verify.md）
npx --yes @openapitools/openapi-generator-cli generate \
  -i contracts/openapi/app.yaml -g typescript-fetch -o apps/generated/app-client
```

提交前必须先通过语义检查；仓库自检还会检查 `operationId` 唯一及外部示例确为 OpenAPI
`Example Object`，避免仅能解析 YAML 却无法被标准工具消费：

```bash
python contracts/tools/check_contracts.py
npx --yes @redocly/cli lint contracts/openapi/app.yaml contracts/openapi/admin.yaml
```

## 8. 兼容规则

1. 新增可选字段、新增错误码：向后兼容。
2. 删除字段、改变字段含义、改变错误码数值、调整分页上限：破坏性变更，必须提升契约版本并提供迁移窗口。
3. 契约变更需同步更新：本文档、`openapi/*.yaml`、`campus-error-codes.csv`、后端常量与测试。
