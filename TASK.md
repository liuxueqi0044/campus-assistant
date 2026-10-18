# 学生端真实接口适配执行任务

你在独立 worktree `C:\Users\xiayu\Documents\Codex\2026-09-17\campus-assistant-student-api`、分支 `feature/student-real-api` 工作。代码基线固定为 `1de4061cd37a3b053d33577ab94d0d79306de6dd`，保留既有视觉和 demo 模式。

完整规则见主任务的 `docs/coordination/2026-09-17-three-task-ownership.md` 和外部交接文件 `C:\Users\xiayu\Documents\Codex\2026-09-16\new-chat-2\outputs\校园助手三任务并行分工.md`。开工前读取 `frontend/docs/后端联调说明.md`、`frontend/apps/student/src/types.ts`、`services/api.ts`、`services/transport.ts`、现有测试，以及 `contracts/openapi/app.yaml` 和 `components/schemas.yaml`。

首个小交付只做 `/schools`、`/context` 的真实 DTO 映射、校区选择、匿名/网络失败语义和测试；登录恢复、退出清理作为第二个提交。允许修改 `frontend/apps/student/src/services/**`、`state.ts`、`types.ts`、`pages/index/index.vue`、`pages-account/login.vue`、`pages-account/settings.vue`、`pages/me/index.vue` 及直接相关测试。禁止修改 backend、database、contracts、根 CI、公共依赖版本和视觉主题。

canonical 路径固定为 `GET /schools`、`GET/PUT /context`、`POST /auth/wechat/login`、`POST /auth/logout`；ID 使用后端 `schoolId/campusId`，浏览选择不产生认证资格；真实模式不允许回退 demo 成功；退出先使旧请求世代失效并清私有状态/token，晚到响应不得恢复旧账号。微信真 code/真机不足必须明确待验，skip 不能算通过。

不要创建额外实现型子智能体，不要操作其他 worktree，不要合并 main 或强推。每个小交付提交后报告 SHA、文件、typecheck/Vitest/H5/小程序构建结果、运行时证据与缺口，等待主集成任务审查。
