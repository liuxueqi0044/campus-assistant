# M01.01 logout 真实线审计边界

## 目标

解释 CI run `35324983794` 中 `campusAdminSessionPinsPlatformTenantAndRejectsRefreshTokenAsBearer`
唯一剩余失败：`assertSuccess(loggedOut)` 通过后，`data` 被解析为 `false`。当前源码的
`CampusAdminSessionController.logout` 在 `sessionService.logout(...)` 成功后明确返回
`CampusResponse.success(true)`，因此先核对真实 HTTP body 与请求实际映射，再决定是否需要生产修复。

## 冻结范围

- 允许修改：`backend/yudao-server/src/test/java/cn/iocoder/yudao/server/integration/M0203TenantBoundaryIntegrationIT.java`
  的脱敏诊断断言，以及本文件的证据记录。
- 禁止修改：controller、service、filter、迁移、OpenAPI wire、M02.05/V0012 和任何旧 A/B worktree。
- 不启动本地 MySQL/Valkey，不为诊断重复 dispatch CI；诊断提交只在下一次既有 CI 需要时验证。

## 诊断证据

1. 记录 logout 成功请求的 HTTP status、`code/message/data/traceId` 形状和脱敏 body；不得打印
   access/refresh token、Authorization header 或数据库凭据。
2. 通过 Spring 集成测试的实际响应路径核对是否命中固定校园 session handler；禁止以测试夹具把
   `false` 改写成 `true`，失败时必须保留原始响应证据。
3. 验收要求：refresh token 作为 bearer 仍为 `AUTH_REQUIRED`；当前 access token logout 返回
   `code=0` 且 `data=true`，并删除对应 access/refresh 行。若真实 body 已是 `data=true` 而断言仍失败，
   则记录为解析/测试问题；若 body 为 `data=false` 或命中其他 handler，才提出最小生产修复包。

## 当前状态

CI run `35324983794` 的 M01.01 B 仍为 `PARTIAL`，仅此 logout 断言失败；媒体回滚、集合异序幂等、
新材料子集拒绝和全部 A selectors 已通过。此审计不改变 A/B/C 状态。

最终诊断 run `35327096247`（head `609b65f`）仍失败，但诊断断言没有执行：
`assertSuccess(loggedOut)` 先在 `is2xxSuccessful()` 处失败，门禁摘要只显示通用
`AssertionFailedError: Expecting value to be true but was false`，未到达 body/data 断言。因此此前把问题
描述为 `data=false` 不成立，真实响应 body 尚未被观测。当前最小可行假设是伪造
`tenant-id=OTHER` 时 FilterChain 在 controller 之前拒绝 logout，需下一次有明确授权的诊断把
status/body 读取放在 `assertSuccess` 之前；在此之前不得修改 controller 或把 B 升为 PASS。

源码核对已给出高可信根因：`TenantSecurityWebFilter` 在
`backend/yudao-framework/yudao-spring-boot-starter-biz-tenant/src/main/java/cn/iocoder/yudao/framework/tenant/core/security/TenantSecurityWebFilter.java`
中先比较已认证 user 的 tenant 与请求 `tenant-id`；不匹配时直接写上游 `CommonResult.error(403, ...)` 并返回，
而不是交给 `CampusAdminSessionController`。测试使用 `tenant-id=OTHER`，所以 HTTP status 仍可能是 200，
但 `code` 非 0，正好对应 `assertSuccess` 的第二断言失败。`@TenantIgnore` 只影响后续缺失租户校验，不能绕过
这条已登录租户不匹配分支。若要修复，边界必须是固定校园 admin session/query 路径在该过滤器前由服务端覆盖/忽略
客户端租户头，并继续执行 platform principal/token guard；不得放宽全局租户 mismatch。
