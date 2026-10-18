# 2026-09-18 M02.03 独立 B 验收入口架构边界

## 目标

为私有证明读取、授权范围和审计事实建立独立真实 B gate。M02.03 A 已覆盖模块单测与 `M0203MySqlIntegrationTest`，M02.02 B 将复用其中的跨模块写入场景；本包只验证 M02.03 的读取授权、跨租户拒绝、审计和数据库持久化，不重复 M02.02 的绑定/回滚 gate。

## 固定输入与执行顺序

专用环境：`CAMPUS_M0203_B_MYSQL_URL/USERNAME/PASSWORD`、`CAMPUS_M0203_B_VALKEY_URL`、`CAMPUS_M0203_B_SCHEMA_GUARD=campus_m0203_b_acceptance`、`CAMPUS_M0203_B_PREPARE=IMPORT_FIXED_BASELINE`。缺少变量、schema guard 或准备令牌错误返回 `EXTERNAL_NOT_READY`，不得运行业务 selector。

先运行现有 `M0101BDatabasePreparationIT` 导入固定基线；准备失败、跳过、零测试或异常即停止。随后依次运行：

1. `M0203TenantBoundaryIntegrationIT#otherTenantAdminCannotBorrowHeaderOrIdentityToReadOrReviewCampusProof`：异租户身份、伪造 header 与跨校读取/审核均拒绝。
2. `M0203TenantBoundaryIntegrationIT#platformOperatorNeedsCampusScopeReviewPermissionAndCaseReasonBeforePrivateProofOpens`：平台 operator 必须具备校园范围权限并提供真实查看原因，私有证明才可打开。
3. `M0203TenantBoundaryIntegrationIT#adminQueriesRespectCampusScopeAndReturnOnlyRedactedVerificationFields`：查询范围与脱敏字段约束有效。
4. `M0203MySqlIntegrationTest`：V0011 proof 唯一约束、grant/audit 同事务回滚、过期时间与快照重放稳定。

Java server 夹具固定读取 `CAMPUS_M0101_B_*`，media 夹具读取 `CAMPUS_M0203_MYSQL_*`；新脚本只在子进程内将专用 `CAMPUS_M0203_B_*` 映射到两组变量，禁止共享 schema 回退。每个 selector 必须有新鲜 Surefire XML，tests>0 且 failures/errors/skipped=0。

## 输出与文件所有权

报告 `artifacts/acceptance/m02.03-b.json` 固定包含 module/level/source SHA、readiness flags、preparation 证据、requiredTests、各 selector 计数与脱敏 reasons。DeepSeek 仅新增 `scripts/ci/m0203_b_acceptance.py` 及 `tests/acceptance/test_m0203_b_acceptance.py`；Sol 持有 workflow、CSV、进度记录和最终 CI 接线。

## 验收门槛

- A：纯 Python 自测、py_compile、diff check 通过；映射、selector 顺序、fail-closed 行为可审计。
- B：远端隔离 MySQL/Valkey 中 preparation、三条 HTTP 边界、三条 M02.03 数据库场景全绿，报告扫描与最终 gate 通过后才将 M02.03 B 从 PARTIAL 升为 PASS。
- C：真实云对象、外部微信/邮箱渠道保持原状态，不因本包改变。

## 非目标与互斥

不修改生产 Java、M02.02 脚本或专用 schema、M02.05/V0012、管理端能力开关；不在 M02.02 CI 运行期间启动另一套数据库或 dispatch 第二个重型验收。
