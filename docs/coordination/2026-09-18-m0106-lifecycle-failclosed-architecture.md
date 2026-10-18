# M01.06 账户生命周期持久化值 fail-closed 小包

## 目标

把现有账户生命周期行映射收口为安全边界：未知 `status`、负数或不一致的
`authVersion/version`、以及与状态不匹配的闭合时间，不能以 `IllegalArgumentException`
或错误的 ACTIVE 推断穿透到授权调用方。敏感动作必须统一拒绝为
`AccountLifecycleFailure.ACCOUNT_ACTION_RESTRICTED`。

## 冻结输入与输出

- 输入：现有 `AccountLifecycleRepository.find/lock` 返回的 `AccountLifecycleDO`；无新增列、迁移或外部接口。
- 已知状态：`ACTIVE/LIMITED/SUSPENDED/CLOSURE_PENDING/CLOSED`，沿用 `AccountLifecycle` 的版本与时间形状约束。
- 空行仍表示隐式 ACTIVE（版本均为 0）；仅空行允许该推断。
- 非法持久化行在 `snapshot`, `assertAllowed`, `changeRestriction`, `requestClosure` 读/锁路径统一转换为 `ACCOUNT_ACTION_RESTRICTED`；不得写入、追加日志或触发会话撤销。
- 合法状态的既有转换、版本冲突和闭环行为保持不变。

## 安全边界

- 未知状态绝不能默认 ACTIVE，也不能把 `valueOf` 异常暴露为 500。
- 负数版本、`version/authVersion` 不一致、CLOSURE_PENDING/CLOSED 缺失时间或普通状态带闭合时间均拒绝。
- 本包不改变 `ResourceAuthorizationService`、M02.05/V0012、数据库 schema、HTTP 契约或管理端能力开关。

## 文件所有权

- Terra：`backend/campus-module-identity/src/main/java/**/application/account/**` 与
  `domain/account/**` 中完成映射/异常边界的最小实现；对应 account 单测。
- Sol：本架构包、公共错误语义审查、集成分支 cherry-pick、复验和进度记录。
- 禁止：迁移、媒体、认证会话控制器、M02.05/V0012、根 CI 和其他模块。

## 验收

- A：窄 Maven 单测覆盖未知 status、非法版本、闭合时间形状；assertAllowed/snapshot/transition
  均 fail-closed，且无写入、日志或撤权副作用；既有 account 测试全绿。
- B：本包不声称真实 DB 通过；需后续真实 MySQL 生命周期读写、撤权和并发回归。
- C：真实部署/跨环境会话凭据缺失时保持 `BLOCKED_EXTERNAL`，不阻塞本地 A。
