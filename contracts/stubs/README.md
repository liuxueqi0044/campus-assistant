# 相邻模块替身样例（M00.02）

契约用例需要的固定样例，包含成功、拒绝、冲突、超时、重复与乱序六类。样例随契约版本演进：
当前版本 `2026.08-campus.1`。

## 目录

| 文件 | 场景 | 用途 |
|---|---|---|
| [`http/success.json`](http/success.json) | 成功响应 | 断言信封、字符串 ID、整数分金额、带偏移时间 |
| [`http/rejected.json`](http/rejected.json) | 参数拒绝（400） | 断言字段级原因，不泄漏内部信息 |
| [`http/conflict.json`](http/conflict.json) | 版本冲突（409） | 断言 `currentVersion` / `currentState` 与不可重试提示 |
| [`adapters/timeout.json`](adapters/timeout.json) | 外部适配器超时 | 断言「结果未知」必须按业务单号核实，而不是自动重试下单 |
| [`events/duplicate.json`](events/duplicate.json) | 事件重复投递 | 断言同一 `eventId` 只产生一次内部效果 |
| [`events/out-of-order.json`](events/out-of-order.json) | 事件乱序 | 断言按 `aggregateVersion` 判定，不倒退状态 |

## 使用规则

1. 相邻模块未完成时用这些样例独立验收；真实实现必须通过同一组用例（B 级验收）。
2. 样例中的编号、学校、用户一律使用设计文档的固定样本：学校 S1／S2，学生 U1／U2／U3，跑腿员 R1／R2，运营 O1／O2，财务 F1。
3. 不得把真实证件、手机号、令牌或商户号写入样例。
4. 改变样例含义等于改变契约：必须提升契约版本并同步更新 `openapi/`、`events/` 与后端常量。
