# 2026-09-17 三任务并行基线与所有权

## 固定基线

- 代码基线：`1de4061cd37a3b053d33577ab94d0d79306de6dd`
- 远端：`https://github.com/liuxueqi0044/campus-assistant.git`
- 公共 HTTP 契约版本：`2026.08-campus.1`
- 新迁移编号：M01.03 独占 `V0006__M01_03_membership_verification.sql`
- 两个执行分支必须从上述已提交基线开始，不复制主工作区的未提交文件。

## 主集成任务

工作目录：`C:\Users\xiayu\Documents\Codex\2026-09-17\22-c-users-xiayu-documents-codex\work\campus-assistant`

主任务独占 M01.01 B、真实 Member/OAuth2、MySQL/Valkey CI、安全护栏、公共验收聚合器和最终合并。当前未提交修改必须保留：`.github/workflows/ci.yml`、`backend/yudao-server/pom.xml`、`scripts/ci/check_acceptance_reports.py`、`tests/acceptance/test_acceptance_report_gate.py`、`backend/yudao-server/src/test/java/cn/iocoder/yudao/server/**`、`scripts/ci/m0101_b_acceptance.py`。

主任务统一决定公共错误码、OpenAPI、公共依赖版本、迁移顺序和最终状态文档。合并顺序为：M01.01 B → M01.03 的小提交 → 学生端真实适配；每次合并后运行相关验收，最后运行完整 GitHub CI。

## 任务 A：M01.03 成员认证生命周期

- worktree：`C:\Users\xiayu\Documents\Codex\2026-09-17\campus-assistant-m0103`
- 分支：`feature/m0103-membership-verification`
- 首个小交付：V0006、领域状态机、DO/Mapper/Repository、确定性单测和真实 MySQL schema/并发测试；先不等待后台 UI、真实媒体或真实校方渠道。
- 允许修改：`backend/campus-module-identity/` 内新增的 membership/verification 文件及对应测试、`database/migrations/V0006__M01_03_membership_verification.sql`、本模块验收文档和本分支专用测试脚本。
- 共享文件例外：只可按下方已分配名称/编号提出最小错误码和 OpenAPI 补丁；不得修改既有条目、契约版本、根 CI、`yudao-server` 或登录/context 实现。主任务保留最终取舍权。
- 禁止修改：M01.01 登录与绑定文件、M01.02 schools/context 文件、`frontend/`、根 CI、主任务未提交文件。

### 固定端口契约

```java
interface MembershipApi {
    MembershipCheckResult check(long userId, long schoolId, long campusId, String action);
}

record MembershipCheckResult(
        boolean effective,
        java.time.OffsetDateTime expiresAt,
        long scopeVersion) {}

interface VerificationEvidencePort {
    VerificationProof verify(long userId, long schoolId, long campusId,
                             String method, java.util.List<String> evidenceRefs);
}

interface ReviewAuthorizationPort {
    void assertAllowed(long operatorId, long schoolId, String action);
}

interface AccountStatusPort {
    void assertActive(long userId);
}
```

`VerificationEvidencePort` 只返回标准化证明结果，不写 membership。生产缺少媒体、授权或账户状态实现时必须拒绝敏感操作；A 级测试可注入显式 stub，但报告必须与真实 B/C 证据分开。

HTTP 目标固定为：`POST /app-api/campus/v1/verifications`、`GET /app-api/campus/v1/membership?schoolId&campusId`、`POST /admin-api/campus/v1/verifications/{id}/review`。ID 对外为字符串；提交返回 `applicationId/status/version`，检查返回 `effective/expiresAt/scopeVersion`，审核携带 `decision/reason/expectedVersion`。响应不得包含原始材料 URL。

预留错误名称与编号：`MEDIA_NOT_OWNED=1_040_001_020`、`MEDIA_NOT_READY=1_040_001_021`、`VERIFICATION_CONFLICT=1_040_001_022`、`MEMBERSHIP_REQUIRED=1_040_001_023`、`MEMBERSHIP_EXPIRED=1_040_001_024`、`MEMBERSHIP_REVOKED=1_040_001_025`、`VERIFICATION_METHOD_DISABLED=1_040_001_026`、`VERIFICATION_REVIEW_FORBIDDEN=1_040_001_027`。如现有注册表出现冲突，停止修改共享文件并报告主任务。

事件固定为 `identity.membership_changed.v1`；状态、审核追加记录和 outbox 必须同事务。payload 只含 `userId/schoolId/campusId/membershipId/status/scopeVersion`，不得包含材料、证件、手机号或私有 URL。

## 任务 B：学生端真实接口适配

- worktree：`C:\Users\xiayu\Documents\Codex\2026-09-17\campus-assistant-student-api`
- 分支：`feature/student-real-api`
- 首个小交付：仅 schools/context 的真实 DTO 映射、选择校区、匿名/网络失败语义及相关测试；登录恢复与退出清理作为第二个独立提交。
- 允许修改：`frontend/apps/student/src/services/**`、`state.ts`、`types.ts`、`pages/index/index.vue`、`pages-account/login.vue`、`pages-account/settings.vue`、`pages/me/index.vue` 及直接相关前端测试。新增文件必须位于 `frontend/apps/student/`。
- 禁止修改：任何 `backend/`、`database/`、`contracts/`、`.github/`、根验收脚本、公共依赖版本和视觉主题；不得把演示模式删除或把后端缺失能力伪造成成功。

### 固定 HTTP 契约

- 基址 `VITE_API_BASE_URL=/app-api/campus/v1`，统一响应 `{code:0,data:T,message?|msg?,traceId?}`；HTTP 非 2xx 或 `code != 0` 都失败。
- `GET /schools?keyword?&cursor?` → `{items:[{schoolId,name,timezone,status,configVersion,campuses:[{campusId,schoolId,name,timezone,status,configVersion}]}],nextCursor}`。
- `GET /context` → `{loggedIn,browseMode:'PUBLIC',school?,campus?}`；它不返回完整目录，也不授予 membership。
- `PUT /context` 请求严格为 `{schoolId,campusId}`；匿名请求只返回校验后的上下文，不能持久化或产生认证资格。
- `POST /auth/wechat/login` 请求 `{code,clientContext?}`，只依赖响应 `accessToken`；真实微信 code/真机不足时明确待验。
- `POST /auth/logout` 无请求体，服务端幂等；前端必须先使本地私有状态和旧请求世代失效，再清 token，晚到响应不能恢复前一账号。
- Bearer Token 只由 `transport.ts` 发送，不记录、不展示、不落入演示数据；真实模式不得静默回退到 demo 成功。

任务 B 不承担 M01.02 完整 B：停用校区后的业务发布、历史订单善后、后端缓存失效不在此分支。验收至少包含 typecheck、相关 Vitest、H5/小程序构建；真实服务不可用时报告 `EXTERNAL_NOT_READY`，不能让跳过测试代表通过。

## 隔离与交付

- M01.03 使用独立、名称以 `_it` 或 `_acceptance` 结尾的 MySQL schema；学生端使用不同服务端口和报告目录。禁止对共享 MySQL/Valkey 执行全局清理或 `FLUSHALL`。
- 两个执行任务不得再创建实现型子智能体；全项目保持主任务加两个执行任务三条写线。
- 每个任务按小提交交付：提交 SHA、变更清单、测试命令/结果、真实依赖缺口。禁止强推、操作其他 worktree 或直接合并到 `main`。
