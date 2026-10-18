# M01.05 跑腿资格纯领域小包

## 目标

先冻结可被接单事务复用的资格状态机和范围断言，避免 M05 直接读取缓存或自行解释资格状态。
本包只实现内存领域模型；真实仓储、事务锁和事件在后续独立 B 包完成。

## 冻结模型

- `RunnerQualification` 保存正数 `qualificationId/userId/schoolId/campusId/version`、状态、
  `effectiveFrom` 与 `expiresAt`。状态仅为 `PENDING|ACTIVE|SUSPENDED|REVOKED`；过期由服务端
  `Clock` 判断，不写回状态。
- `PENDING -> ACTIVE` 需要有效起止时间且 `expiresAt > effectiveFrom`；`ACTIVE -> SUSPENDED`
  保留原有效期；`SUSPENDED -> ACTIVE` 仅在当前时刻仍未过期时允许；任何其他转换拒绝。
- `assertForAccept(userId, schoolId, campusId, now)` 只允许 ACTIVE、有效期内、完全匹配
  school/campus 的资格，返回 `qualificationId/version`；用户、学校或校区不匹配统一拒绝，
  不扩大为学校级或跨校授权。过期、PENDING、SUSPENDED、REVOKED 均拒绝。
- 领域断言只表达资格事实；`TRANSACTION_REQUIRED`、固定锁序“资格 → 订单 → runner_load”、
  写日志/事件和并发提交留给后续 service/repository 包。

## 文件所有权

- Terra：`backend/campus-module-identity/src/main/java/**/identity/domain/runner/**` 中的状态、
  结果和异常最小模型及对应纯单测。
- Sol：本架构包、公共错误语义审查、集成/复验/进度记录。
- 禁止修改：数据库迁移、仓储、M05 订单、M08/M09、HTTP/OpenAPI、根 CI、M02.05/V0012。

## 验收

- A：纯内存窄测覆盖合法批准/停用/恢复、过期、PENDING/SUSPENDED/REVOKED、跨用户/跨校/跨校区
  和非法转换；结果只返回 ID/version 或明确拒绝，无副作用。
- B：`NOT_RUN`；需后续真实 MySQL、资格锁与 M05 100 并发接单/停用混合测试。
- C：`BLOCKED_EXTERNAL`；真实部署和跨环境会话不在本包范围。
