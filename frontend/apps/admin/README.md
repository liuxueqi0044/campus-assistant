# 校园助手运营台

此应用从 `UPSTREAM.md` 记录的芋道管理端固定提交中提取登录、令牌刷新、布局思路与 Element Plus 技术栈，并只保留校园运营入口。未接入的接口明确失败关闭，不生成演示学校、申请或账号。

当前交付是 M11.01A 安全壳。校园后台专用登录契约已经冻结，客户端只调用 `/admin-api/campus/v1/session/*`，不查询、保存或发送 `tenantId`；真实后端实现和 HTTP 联调仍待完成。管理端上下文、认证申请队列和详情接口已有冻结的类型化客户端，但后端实现与真实联调尚未完成，因此能力开关继续关闭，M11.01 和 M11.02 均不能标记为完成。已经冻结的审核写命令保留类型化客户端，页面在读接口落地前不会开放操作。

```powershell
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build
```

开发服务器仅把 `/admin-api` 代理到 `CAMPUS_ADMIN_BACKEND`，默认 `http://127.0.0.1:48080`。登录表单不包含默认账号、密码、租户或演示数据。

查询能力默认关闭。只有审查远端 B 级 `context → queue → detail` HTTP 证据后，才可在构建环境设置 `VITE_ADMIN_ENABLE_CAMPUS_QUERIES=true` 开启学校范围、审核队列和详情请求；未设置时页面保持安全壳状态，不会请求这些接口。
