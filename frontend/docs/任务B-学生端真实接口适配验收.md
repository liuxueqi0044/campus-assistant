# 任务 B：学生端真实接口适配验收记录

验收日期：2026-09-17（Asia/Shanghai）

worktree：`C:\Users\xiayu\Documents\Codex\2026-09-17\campus-assistant-student-api`

分支：`feature/student-real-api`

代码基线：`1de4061cd37a3b053d33577ab94d0d79306de6dd`（另含协调文档提交）

最终代码提交：`31aae72b22e0522459fc80b218da317bc31be282`

## 结论

任务范围内的前端独立实现与自动化证据通过；真实后端进程、真实 Member/OAuth2、微信 code2session、微信开发者工具及真机证据仍未就绪，因此不宣称 M01.01/M01.02 的 B/C 级联调通过。

真实模式请求失败不会回退演示身份、演示目录或演示业务数据。学校浏览选择仅改变公开浏览上下文，不生成 membership、学生认证或跑腿资格。

## 交付提交

| 提交 | 内容 |
| --- | --- |
| `06afc7db3d6b28df43d068e729425b187b0954d3` | schools/context 映射、服务端搜索与 cursor 分页、业务接口故障隔离、快速切换顺序保护、相关 UI 与测试 |
| `a9ddbbf78bd48acff32ffc96df3d014c45af0201` | 小程序登录、内存 Token、过期信息、H5 能力区分、401/403、退出及晚到响应隔离 |
| `7dc7f0eaf102a8ea8cfd87fc2648d4176a906a46` | canonical `/schools` fail-closed、登录响应最小依赖、校区选择器窄屏布局修复 |
| `31aae72b22e0522459fc80b218da317bc31be282` | 固化退出顺序：请求世代和私有状态先失效、共享 Token 随后清理、最后远端撤销 |

## 已实现边界

1. `GET /context` 与 `GET /schools` 并行加载；context 成功后，尚未部署的二手、跑腿、活动或 workspace 接口失败不会覆盖校园目录和浏览上下文。
2. `/schools` 只接受 canonical 字符串 `schoolId/campusId` 和 `status=ENABLED`；legacy `id` 兼容仅保留给旧 context 回退。数字、缺失、未知状态或错误父子归属均 fail-closed。
3. 校区选择发送严格的 `{schoolId,campusId}`。并发切换按写入顺序串行，较旧意图不得在较新选择后刷新页面或成为最终服务端偏好。
4. 学校搜索使用后端 `keyword`，翻页原样传递不透明 `cursor`，UI 明确提供“加载更多学校”，不把第一页当完整目录。
5. 小程序登录只以 `accessToken` 作为建立会话的必要字段；`expiresAt/userId/profileCompletion` 在合法时用于内存展示，不成为权限来源。Token 不写本地存储、不进入 Vue state、不展示或记录。
6. H5 不调用或伪造微信 code，登录页明确提示只支持公开浏览；小程序使用 `uni.login(provider:'weixin')`。
7. 退出先让旧请求世代失效、清理私有状态和共享 Token，再由传输层闭包携带被清理前的 Token 调用幂等 `/auth/logout`。网络失败时本地仍保持退出，并明确提示服务端撤销结果未知。
8. 401 与 403 保留不同 HTTP 语义，不自动无限重试；退出、重登录后的晚到 login/workspace/context 结果不能恢复旧账号私有状态。

## 可复现自动化证据

在 `frontend/apps/student` 执行：

```powershell
npm ci
npm run typecheck
npm test -- --run
npm run build:h5
npm run build:mp-weixin
```

结果：

- TypeScript / Vue 类型检查通过。
- Vitest：3 个测试文件、38 项通过、0 失败。
- H5 正式构建通过，产物位于 `apps/student/dist/build/h5`。
- 微信小程序正式构建通过，产物位于 `apps/student/dist/build/mp-weixin`；构建通过不等于微信开发者工具或真机通过。
- `git diff --check` 通过，仅有仓库既有的 Windows 行尾转换提示。

测试重点覆盖 canonical/legacy 边界、超大字符串 ID、搜索与 cursor 编码、空目录、真实模式不回退 demo、未部署业务接口隔离、快速切换顺序、H5 登录门控、小程序 code/Token、过期信息、401/403、退出服务端未知、退出后晚到登录与 workspace 响应隔离。

## 本地布局检查

使用独立开发端口 `5183`、H5 演示模式进行可视检查：

- 320 × 800 窄屏；
- 1.125 倍较大字号；
- 校区选择器、搜索框、选中标记、列表滚动；
- 不存在学校的搜索空态。

新增选择器在上述场景可操作，选中标记不再挤压普通校名。真实超长学校名称、微信 WebView 字体与真机安全区仍需真实目录和真机补验。

## 外部条件未就绪

- 主集成任务尚未提供 M01.01 B 的已提交 SHA 或可访问 live 地址。
- 本机 Docker 客户端为 29.8.0，但 Docker daemon 未运行，无法在不影响其他任务的前提下启动隔离 MySQL/Valkey 和完整后端；未连接或清理任何共享数据库/缓存。
- `real-mode.test.ts` 使用受控响应验证前端适配器，只是前端独立证据，不是前后端真实 HTTP 证据。
- 尚无真实微信 AppID、一次性 code、微信开发者工具或真机证据。
- 仍需主任务提供隔离测试账号/校区后补验：真实 schools 多页/搜索、匿名与登录态 context、浏览偏好持久化、Token 过期、幂等 logout、旧会话撤销、403 以及网络未知恢复。

上述条件补齐前，本任务状态应记录为“前端实现与独立测试通过，真实 B/C 联调待验”。
