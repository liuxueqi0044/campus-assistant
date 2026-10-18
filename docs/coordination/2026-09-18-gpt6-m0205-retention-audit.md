# M02.05 retention and evidence-preservation audit

审查基线为 `94618bbb0aea7538cdd0d7cffe7df2c4f72d9b8d`（`integration/sol-auth-closure`），工作树为独立的 `review/gpt6-media-identity-audit`。本轮只审查 `backend/campus-module-media/**` 和本记录，未修改数据库迁移、契约、CI 或其他模块。

## 审查边界与验收标准

- 清理必须先在同一事务中锁定资产，检查 binding/hold/期限，再以 CAS 抢占 `DELETE_PENDING`，之后只对冻结的 object key/generation 做事务外删除；旧 worker 不能覆盖新终态。
- `hold(assetId, caseRef, reason)` 必须与 M09.03 案件证据关系同事务；hold 成功前不得把证据报为已保全。删除已抢占或对象缺失时必须返回 `DELETE_IN_PROGRESS`/`EVIDENCE_UNAVAILABLE`，不写成功关系。
- 多个 hold 按 assetId/caseRef 唯一，释放指定 reason/版本；仍有其他 hold 时不能删除。删除成功但数据库确认丢失时，重试必须按同 key/generation 收敛为 `DELETED`。

## 现状与风险

M02.05 当前只有设计稿，没有 `RetentionPolicyService`、`MediaHoldService`、清理任务、删除任务表或持久化 hold。V0009 仅保存当前 `object_key/generation/status/version/ticket_expires_at`；UploadTicketService 在票据续期时把 key 从 `g1` 换成新的 `g2`，但没有历史 key 或删除任务引用。旧对象因此无法从现有资产行枚举，未来清理器若只按当前 key 删除会留下不可追踪孤儿；若按猜测路径删除又会违反“旧 worker 不删新对象”。这需要新的持久化历史/任务设计，不能在本轮限定目录内安全修补。

V0010 只提供 binding 的 `asset_id` 唯一约束，现有 `MediaBindingService` 只接受 `VALID` 资产，尚未有 `DELETE_PENDING`/hold 互斥状态。V0011 的访问日志和 delivery grant 都没有删除租约、hold 或对象代际 fencing 字段。M09.03 设计要求先锁订单/案件和资金，再按 assetId 顺序调用 M02 hold；当前 M09 实现和跨域事务均不存在，不能把设计文字当作保全完成证据。

因此本轮未发现能在现有字段和事务边界内以最小补丁安全修复的单一缺陷；保持只读审查，记录上述实现前置风险。

## 实际验证

- `python contracts/tools/check_contracts.py`：通过；本轮未修改 contracts。
- 使用 Temurin 17.0.20.1、Maven 3.9.16、`MAVEN_OPTS=-Xmx768m` 执行：
  `mvn -pl campus-module-media -am '-Dtest=LocalPrivateStorageAdapterTest,UploadTicketServiceTest,MediaUploadServiceTest,MediaCompletionServiceTest,MediaBindingServiceTest,DownloadAuthorizationServiceTest,MembershipVerificationEvidenceAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  结果：34 tests，0 failures/errors，`BUILD SUCCESS`。
- 未启动 Docker/MySQL/VM；未声称真实对象存储、hold/删除 worker 或 M09.03 集成通过。

## A/B/C 状态与下一步

- A：现有媒体上传、绑定、下载和本地存储回归窄测 PASS（34/34）；M02.05 retention/hold/delete 状态机 A 验收仍 `NOT_RUN`，因为实现尚不存在。
- B：`NOT_RUN`；需要真实数据库事务、锁/CAS、任务恢复和 M09.03 案件事务。
- C：`BLOCKED_EXTERNAL`；需要受控真实桶验证删除、拒删、缺失和访问失效。

下一步先冻结 M02.05 schema/状态机：保存每一代 object key、delete lease/fence、hold reason/version 和可查询异常；再实现同事务 hold、清理抢占、事务外删除及重试收敛，最后补 M09.03 跨域 A/B/C 证据。 
