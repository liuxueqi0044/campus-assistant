# 数据库迁移与事务约定（M00.03）

## 迁移制度

- `database/migrations/` 是自有增量 SQL 的唯一源码入口。文件格式固定为
  `V<单调序号>__Mxx_<说明>.sql`；已进入任一环境的文件绝不编辑、改名、删除，修正只能追加更高版本的前向迁移。
- Flyway 在应用启动时从发布 JAR 的 `classpath:db/migration` 执行。该目录是 Maven 从上述唯一源码入口复制的构建产物，运行不依赖工作目录。
- `validate-on-migrate=true`、`baseline-on-migrate=false`、`out-of-order=false`、`clean-disabled=true`。历史缺失、checksum 不符、顺序异常或执行失败均以 `MIGRATION_BLOCKED` 阻断启动。
- 数据库变更采用“扩字段/表 → 双版本兼容读写 → 切换应用 → 后续版本清理”。DDL 不假定可回滚；失败后先保存 `flyway_schema_history` 和实际 schema，再做经复核的前向修复。
- 每一次生产迁移都要有 `MigrationPlan(fromVersion, toVersion, backupPrerequisite)`，备份可恢复性、影响表、预计锁时间、回退替代方案和负责人都写入发布记录。`clean` 仅允许隔离测试库。
- 所有自有表使用 InnoDB；技术时间为 UTC，金额为整数分。唯一键、版本列、外键和索引写在 SQL 中，不能只依赖应用检查。

## 事务边界和锁顺序

应用用例使用 Spring `@Transactional(propagation = REQUIRED)` 或 M00.03 的
`TransactionExecutor.required(...)`，跨域同步命令加入调用方事务。禁止在业务调用链中用
`REQUIRES_NEW` 偷偷提交一半状态。

`CampusTransactionConfiguration` 在应用上下文提供唯一的
`campusTransactionExecutor` Bean，业务应用服务通过构造器按 `TransactionExecutor` 类型注入后调用
`required(policy, callback)`；它不是领域服务，也不拥有业务表或事务策略。每个用例必须自己选择
`TransactionPolicy`，并在代码评审中证明唯一键/幂等键和无外部副作用条件。

固定锁顺序如下；后续实现不需要的锁直接跳过，但不得倒序：

1. 资金控制/冻结记录；
2. 身份资格、库存或名额记录；
3. 订单/任务聚合记录；
4. 当前聚合的附属记录和本地 outbox。

业务写入与 M00.05 的 `campus_outbox` 插入必须处于同一事务。M00.03 的
`campus_m00_03_transaction_example_outbox` 仅为验收 stub，不能替代正式 outbox 表或由业务模块复用。

## 有界重试

默认 `TransactionPolicy.withoutRetry(operation)`。只有已经具备业务唯一键/幂等键、且回调内没有任何外部
副作用的命令，才可显式使用 `retryMySqlLockConflicts(operation, maxAttempts)`；总尝试次数为 1–3。

- 仅 MySQL deadlock (1213) 与 lock wait timeout (1205) 可重跑整个 REQUIRED 事务；保存同一幂等键。
- 乐观锁失败不自动重试，交由入口映射为 `VERSION_CONFLICT`。
- 重试耗尽抛出稳定语义 `RETRYABLE_CONFLICT`，调用方可查询/提示重试；不会伪报成功。
- HTTP、消息、支付、邮件、短信、对象存储等外部副作用不得放进自动重试回调；先在本地事务写 outbox，事务提交后由 M00.05 投递。

大批量任务使用有序游标分批提交，避免大事务持锁；消费者标记和其内部效果同事务。
