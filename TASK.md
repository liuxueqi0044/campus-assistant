# M01.03 执行任务

你在独立 worktree `C:\Users\xiayu\Documents\Codex\2026-09-17\campus-assistant-m0103`、分支 `feature/m0103-membership-verification` 工作。代码基线固定为 `1de4061cd37a3b053d33577ab94d0d79306de6dd`，迁移编号独占 `V0006__M01_03_membership_verification.sql`。

完整规则见主任务的 `docs/coordination/2026-09-17-three-task-ownership.md` 和外部交接文件 `C:\Users\xiayu\Documents\Codex\2026-09-16\new-chat-2\outputs\校园助手三任务并行分工.md`。开工前必须读取 `docs/specification/详细模块架构/00-工程约定与独立验收规范.md`、`docs/specification/详细模块架构/M01-身份与校园.md`、现有 V0001–V0005、M0101/M0102 MySQL 测试以及 outbox/transaction 模式。

首个小交付只做 V0006、领域状态机、DO/Mapper/Repository、确定性单测和真实 MySQL schema/并发测试。允许修改 `backend/campus-module-identity/` 内新增的 membership/verification 文件与测试、V0006、本模块验收文档和专用测试脚本；禁止修改登录、schools/context、frontend、根 CI、yudao-server。共享错误码/OpenAPI 只能按主任务已分配名称和编号提交最小补丁，不得改既有条目或契约版本。

核心不变量：`(user_id,school_id)` 唯一；学校/校区复合归属；每次申请独立且审核日志 append-only；Clock 到期即时失效；相反审核并发只有一个版本成功；状态、审核记录和 `identity.membership_changed.v1` outbox 同事务；他人/待检查材料、无授权、账户停用、缺失生产依赖一律拒绝。stub 只能算 A 级隔离证据。

不要创建额外实现型子智能体，不要操作其他 worktree，不要合并 main 或强推。每个小交付提交后报告 SHA、文件、测试与缺口，等待主集成任务审查。
