# 事件契约（M00.02）

## 1. 信封

字段定义见 [`envelope.schema.json`](envelope.schema.json)（JSON Schema 2020-12）。

| 字段 | 必填 | 规则 |
|---|---|---|
| `eventId` | 是 | 同一事实只生成一次；重放沿用原值 |
| `type` | 是 | `campus.<域>.<事实>`，例如 `campus.order.created` |
| `version` | 是 | 结构版本，从 1 开始 |
| `aggregateId` | 是 | 字符串形式的聚合编号 |
| `aggregateVersion` | 是 | 聚合版本，用于乱序判定 |
| `schoolId` | 是 | 学校范围；跨校事件必须显式说明范围 |
| `occurredAt` | 是 | RFC 3339 且带时区偏移 |
| `traceId` | 否 | 产生事件的请求追踪号 |
| `payload` | 是 | 业务载荷，禁止敏感值 |

## 2. 载荷禁止项

证件号与取件码一律不能进入事件表。键名归一化（去下划线、连字符并转小写）后命中以下任一即拒绝：

`idcard`、`idcardno`、`idcardnumber`、`idno`、`idnumber`、`certno`、`pickupcode`

拒绝时返回错误码 `CAMPUS_EVENT_PAYLOAD_FORBIDDEN`，消息里给出具体键名，事件进入可观察失败而不是被静默丢弃。

## 3. 版本策略

- 消费者声明自己支持的「类型 → 版本集合」，由 `CampusEventVersionPolicy` 判定 `SUPPORTED`、`UNSUPPORTED_VERSION`、`UNKNOWN_TYPE`。
- 未知类型或未知版本必须进入死信与人工处理（错误码 `CAMPUS_EVENT_VERSION_UNSUPPORTED`，`requiredAction=OPERATOR`）。
- 不允许按旧结构猜测字段继续执行；已成功的消费者在重放时跳过。

## 4. 与 M00.05 的关系

`V0005__M00_05_transactional_outbox.sql` 与 `campus-common` 的 `outbox` 包已经实现事件存储、
短事务领取、租约与 fencing token、退避重试、死信和定向重放。数据库是投递状态的唯一事实来源，
定时器或 Quartz 只负责唤醒扫描，不能承载重试正确性。

消费者仍必须保证「内部数据库效果与成功标记同事务」，本契约不替业务决定幂等键。外部网络副作用
只能保证至少一次，适配器必须使用稳定业务号或第三方幂等键，并通过查询/对账处理不确定结果。
