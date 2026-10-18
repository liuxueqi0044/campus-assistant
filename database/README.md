# 数据库迁移入口

`database/migrations/` 是校园自有生产 SQL 的唯一源码入口。Flyway 在构建时将这里的
`V<单调版本>__Mxx_<说明>.sql` 复制到发布 JAR 的 `classpath:db/migration`；副本不是第二个可编辑来源。
已在任何环境执行的迁移不可改名、删除或修改，修复只能追加更高版本的前向迁移。

## 当前版本线

`V0001__M00_03_migration_policy_baseline.sql` 只建立 checksum 保护的自有迁移历史，不创建业务表。
后续迁移按模块归属单调追加：`V0002` 为 M01.02 学校/校区浏览上下文，`V0003` 为 M00.04
持久化幂等命令，`V0004` 为 M01.01 微信身份映射，`V0005` 为 M00.05 transactional outbox
及消费去重记录，`V0006` 为 M01.03 成员资格、认证申请和审核记录。`V0007`、`V0008` 由
M01.04/M01.06 保留，`V0009` 为 M02.01 私有媒体资产与上传票据；后续编号按
`docs/coordination/2026-09-17-continuous-delivery-cycle-1.md` 分配。M00.03 的事务示例 DDL 仅在
`backend/campus-common/src/test/resources/m003-fixtures/` 供自动化验收使用，绝不随生产 JAR 发布。

## 空 schema 与完整应用新库

应用的 `spring.flyway` 配置固定为 checksum 校验、禁止 clean、禁止自动 baseline、禁止 out-of-order。
纯空 schema 的首次启动会按序执行当前全部已审核校园迁移；这仅用于验证校园迁移链，
**不是完整 yudao 应用的新库部署方案**。完整应用依赖的上游表不会由校园迁移重复创建，也不得启用 Hibernate、
Flowable 或 Spring SQL 初始化来补建。

完整应用的新实例应先从已固定 commit 与 SHA-256 的 `backend/sql/mysql/ruoyi-vue-pro.sql` 和 `quartz.sql`
导入上游基线，再按下节对该非空 schema 显式执行一次 V0 baseline，之后才由应用执行 V0001 及后续校园迁移。

## 上游 59 表既有库的一次性接管

上游 `ruoyi-vue-pro.sql` / `quartz.sql` 已导入而没有 `flyway_schema_history` 的非空库，会被应用以
`MIGRATION_BLOCKED` 拒绝。这是保护而非故障，绝不通过 `baseline-on-migrate=true` 绕过。

接管必须由发布负责人在**隔离维护窗口**执行以下明确步骤；运行账号需要 Flyway history 的 DDL/DML 权限，且
不得把生产密码写入命令历史或仓库。

1. 核对目标 schema 的上游来源、固定 commit 与 SQL SHA-256，确认恰好是仓库 `backend/sql/mysql/ruoyi-vue-pro.sql`
   和 `quartz.sql` 对应的已审核基线（当前验收为 59 张上游表）。若有手工表、未知补丁或表数不符，停止并先出具差异报告。
2. 完成可恢复备份并在独立实例做还原演练；记录备份位置、校验和、恢复时长和负责人。确认无应用实例写入目标库。
3. 使用 Flyway CLI/API 对该**指定 schema 一次性显式**执行 `baseline -baselineVersion=0`；记录操作者、时间、schema、
   上游 SHA 和命令输出。这个操作只允许已完成第 1–2 步的既有库，绝不能放进应用启动配置。
4. 以应用的同一 Flyway 版本和只读校验先运行 `validate`，再运行 `migrate`，使 V0001 及之后迁移落入 history；保存
   `flyway_schema_history`、schema diff 和启动日志。失败时停止写入，按备份恢复或审核后的前向修复处理。
5. 复跑 `validate` 和应用只读健康检查，再解除维护窗口。以后任何环境都不再 baseline，只接受单调增量迁移。

可重复命令的参数模板（在受控终端以秘密管理工具注入 URL/账号/口令）：

```powershell
# 仅用于第 3 步已经审核的既有库；不要把它加到 application.yaml。
flyway -url=$env:CAMPUS_DB_URL -user=$env:CAMPUS_DB_USERNAME baseline -baselineVersion=0
# 之后从发布 JAR/同版本 Flyway 使用 database/migrations 的构建副本进行 validate/migrate。
```

生产程序仍保持 `baseline-on-migrate=false`；第 3 步是人为、可审计、不可默认的接管动作。
