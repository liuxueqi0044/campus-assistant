# M01.01 logout token revocation 修复包架构边界

## 目标

解释并修复 `CampusAdminSessionService.logout` 成功响应后，当前 access token 仍存在于
`system_oauth2_access_token` 的真实 MySQL 证据缺口。保持固定 platform tenant、ADMIN audience、
refresh-token-as-bearer 拒绝和普通租户边界不变。

## 允许范围

- `backend/campus-module-identity/src/main/java/**` 中 CampusAdminSessionService 及其紧邻 session
  基础设施；只有被 SQL/事务证据直接证明必要时才修改。
- `backend/yudao-server/src/test/java/cn/iocoder/yudao/server/integration/M0203TenantBoundaryIntegrationIT.java`
  及 identity session 单测/集成测试。
- 本文档与接管进度记录。

禁止修改全局 `TenantSecurityWebFilter`、全局 OAuth 表结构/迁移、其它租户清理逻辑、全表 delete、
M02.05/V0012 或前端能力开关。

## 调查顺序

1. 追踪 `CampusAdminSessionService.logout -> PlatformTenantExecutor.execute -> AdminAuthService.logout ->
   OAuth2TokenServiceImpl.removeAccessToken` 的实际 tenant context、mapper SQL、事务边界和返回值。
2. 用同一 current access token 在真实 DB 中证明：查询前存在、logout 后应删除 access 与对应 refresh；
   其它用户 token 不受影响；未知/异租户 token 仍拒绝且不删除任何行。
3. 若 mapper 删除返回 0，先证明是 tenant context/插件过滤问题；若删除返回 1 但测试仍查到，
   先证明查询上下文或缓存问题，禁止直接改测试断言。

## 验收标准

- current access token 与其 refresh token 在 logout 后均不存在。
- 其它用户的 access/refresh token 保持存在。
- refresh token 作为 Bearer、未知 token、异租户 token 均按既有 Campus AUTH_REQUIRED/租户拒绝契约失败，
  且不删除有效 token。
- 真实 FilterChain + MySQL/Valkey 回归通过；窄单测覆盖删除返回值与 tenant restore。
- A/B/C 状态按证据更新；未通过完整 B 前保持 `PARTIAL`。

## 文件所有权与执行顺序

Sol 持有公共契约、架构、最终审查、集成和 CI。唯一 Terra 只修改上述 identity/session 生产文件
及指定回归测试；不得触碰其它模块或重建数据库。Terra 交付后由 Sol 审查 diff、运行窄测，再决定
是否推送和进行一次远端完整 CI。
