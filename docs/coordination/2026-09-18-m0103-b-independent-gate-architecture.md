# 2026-09-18 M01.03 独立 B 验收入口架构边界

## 目标

为 M01.03 建立独立、可复现的真实 B 级 gate。已有 CI 35337566202 在 M01.01 B 套件中证明了禁用成员重登拒绝和 M02.03 跨模块绑定场景，但 workflow 的 `M01_03_OUTCOME` 仍只代表 A 级选择器，不能据此把 M01.03 聚合 B 标为 PASS。

## 固定输入与测试范围

新入口只调用三个已存在的真实 HTTP/DB/Valkey 用例：

1. `M0101MemberOAuth2IntegrationIT#disabledMemberCannotLoginAgainOrCreateAnotherToken`：成员禁用后同 openId 登录返回校园 `ACCOUNT_DISABLED`，且 member/binding/access-token 不新增。
2. `M0203TenantBoundaryIntegrationIT#memberVerificationUsesRealMediaBindingsAndSetOrderIdempotency`：认证申请与真实媒体 binding 同事务；同一材料集合异序重放幂等；子集/重叠材料按 V0011 拒绝且无额外写入。
3. `M0203TenantBoundaryIntegrationIT#mediaBindingsRollBackWhenVerificationInsertFails`：申请插入失败时 binding、proof、asset 状态全部回滚。

入口必须要求专用 M01.03-B MySQL schema、Valkey URL、schema guard 和固定准备令牌；缺少任一项返回 `EXTERNAL_NOT_READY`，不能当作 PASS。业务 selector 前必须先运行现有 `M0101BDatabasePreparationIT`，将固定基线导入同一专用 schema；准备失败、跳过、零测试或异常时不得运行后续 selector，也不能把空 schema 结果当作认证证据。每个业务 selector 都必须产生新鲜 Surefire 报告，零测试、跳过、失败、错误或 Maven 异常均为 FAIL。报告只含脱敏状态、用例计数、源码 SHA 和原因，不记录 token/密码。

## 文件所有权

- Terra：`scripts/ci/m0103_b_acceptance.py` 及同文件的纯 Python 自测；不得改生产 Java、迁移或 workflow。
- Sol：`.github/workflows/ci.yml` 的单一 M01.03-B step、最终 gate 接线、CSV/进度记录和集成复验。

## 验收门槛

- A：脚本静态自测通过；固定 selector、环境变量、脱敏和 fail-closed 规则可审计。
- B：远端 CI 在隔离 MySQL/Valkey 中三 selector 全部真实运行并通过，报告扫描通过，最终 gate 显式要求 `M01_03_B_OUTCOME=success`；只有该证据齐全才把 CSV 的 M01.03 B 从 PARTIAL 升为 PASS。
- C：真实微信 code2session、邮箱/校方渠道及真机流程仍为 `BLOCKED_EXTERNAL`，本包不改变。

## 非目标

不改任何 controller/service、tenant guard、M02.05/V0012、管理端能力开关或外部凭据；不启动本地数据库或重型构建。
