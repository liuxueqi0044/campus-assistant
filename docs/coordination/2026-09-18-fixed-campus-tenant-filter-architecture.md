# 固定校园管理端 platform tenant 前置覆盖

## 目标

修复 M01.01 管理端 session/query 在伪造 `tenant-id=OTHER` 请求头下被全局
`TenantSecurityWebFilter` 提前拒绝的问题。校园管理端的 tenant 必须由服务端固定为
`campus.platform-tenant-id`；客户端 header 不能选择租户。

## 冻结边界

- 只匹配 `/admin-api/campus/v1/session/**` 与已冻结的只读管理查询路径
  `/admin-api/campus/v1/context`、`/admin-api/campus/v1/verifications`、
  `/admin-api/campus/v1/verifications/**`。
- 在 `TenantContextWebFilter` 读取客户端 header 后、`TenantSecurityWebFilter` 校验 user/tenant
  之前，临时设置服务端 platform tenant；请求结束必须 `finally` 恢复原上下文，防止线程污染。
- 过滤器只改变租户上下文，不授予匿名访问、不绕过 Spring Security、不绕过
  `CampusPlatformPrincipalGuard` 或查询层学校/校区授权。platform token 通过；异租户 token 仍因
  user tenant 与 platform context 不一致而拒绝；普通非校园路径的 header mismatch 行为不变。
- 禁止修改全局 `TenantSecurityWebFilter` mismatch 规则、迁移、controller 契约和 M02.05/V0012；
  禁止以 `@TenantIgnore` 作为唯一修复。

## 验收

1. platform ADMIN token + `tenant-id=OTHER`：session logout、context、queue/detail 进入 controller，
   返回正常校园 envelope，并保留既有 platform principal/范围授权。
2. 其他租户 ADMIN token + `tenant-id=OTHER` 访问上述路径：仍返回拒绝，不能读取平台数据。
3. 普通 system/admin 路径携带不匹配 tenant header：仍由 `TenantSecurityWebFilter` 拒绝。
4. 过滤器单测覆盖 path matcher、上下文覆盖与 finally 恢复；真实 FilterChain 集成测试覆盖上述三条路径。
5. 本包不改变 M01.01/M01.03/M02.02/M02.03 B 状态，直到远端 MySQL/Valkey CI 完整通过。

## 文件所有权

- Terra：固定 tenant 前置过滤器、配置注册、对应窄单测；不得改业务 controller/service。
- Sol：`M0203TenantBoundaryIntegrationIT` 回归、文档/契约、审查、CI 与最终集成。
