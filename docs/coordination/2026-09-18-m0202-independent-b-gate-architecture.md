# 2026-09-18 M02.02 独立 B 验收入口架构边界

## 目标

为 M02.02 文件验证与占用绑定建立独立真实 B gate。M02.02 A 已覆盖模块单测和 `M0202MySqlIntegrationTest`，M01.03 B 已间接证明两条跨模块事务路径；本包将这些证据放入 M02.02 自己的隔离 schema 和报告，避免把别的模块 gate 代替 M02.02 B。

## 固定输入与执行顺序

入口要求专用环境：

- `CAMPUS_M0202_B_MYSQL_URL/USERNAME/PASSWORD`，schema 必须匹配 `campus_m0202_b_acceptance`；
- `CAMPUS_M0202_B_VALKEY_URL`；
- `CAMPUS_M0202_B_SCHEMA_GUARD=campus_m0202_b_acceptance`；
- `CAMPUS_M0202_B_PREPARE=IMPORT_FIXED_BASELINE`。

缺少变量、schema guard 或准备令牌错误时返回 `EXTERNAL_NOT_READY`，不得运行测试或报告 PASS。业务 selector 前先运行现有 `M0101BDatabasePreparationIT` 导入固定基线；准备阶段失败、跳过、零测试或异常时停止后续 selector。

随后按以下顺序执行，每个 selector 都必须产生新鲜 Surefire 证据：

1. `M0203TenantBoundaryIntegrationIT#memberVerificationUsesRealMediaBindingsAndSetOrderIdempotency`：同一材料集合异序重放幂等，子集/重叠材料拒绝且无额外写入。
2. `M0203TenantBoundaryIntegrationIT#mediaBindingsRollBackWhenVerificationInsertFails`：认证申请插入失败时 binding、proof、asset 状态回滚。
3. `M0202MySqlIntegrationTest`：V0010 迁移、同 asset 并发唯一约束、旧 generation CAS、业务资源与 binding 同事务回滚。

Java 夹具当前分别读取 `CAMPUS_M0101_B_*`（server integration）和 `CAMPUS_M0202_MYSQL_*`（media integration）。脚本只能在子进程内把专用 `CAMPUS_M0202_B_*` 映射到这两组变量，禁止回退到 M01.01/M02.02 共享 schema。

## 输出与错误语义

报告 `artifacts/acceptance/m02.02-b.json` 固定含 `module=M02.02`、`level=B`、源码 SHA、`mysqlConfigured`、`valkeyConfigured`、准备证据、requiredTests、各 selector 的 tests/failures/errors/skipped/exitCode/reason。零测试、跳过、失败、错误、陈旧 XML、Maven 异常或非 0 退出均为 FAIL；报告和日志脱敏，不写入 token、密码或私有对象值。

## 文件所有权

- DeepSeek executor：仅新增 `scripts/ci/m0202_b_acceptance.py` 及该脚本的纯 Python 自测；不得修改生产 Java、迁移、已有 A 脚本或 workflow。
- Sol：`.github/workflows/ci.yml`、CSV、接管进度、最终 gate 接线、审查、集成和远端 CI。

## 验收门槛

- A：脚本自测、`py_compile`、`git diff --check` 通过；固定环境、selector 顺序、映射和 fail-closed 行为可审计。
- B：远端 CI 在专用 MySQL/Valkey 中 preparation、两条跨模块事务场景和四条 M02.02 数据库场景全部通过，敏感数据扫描和最终 gate 通过；此后才将 CSV 的 M02.02 B 从 PARTIAL 升为 PASS。
- C：真实云对象存储/压缩 worker、微信或外部凭据继续保持原状态，本包不改变。

## 非目标

不改 M02.05/V0012、生产绑定/事务逻辑、M01.03 gate、管理端能力开关或外部云资源；不启动本地重型数据库。
