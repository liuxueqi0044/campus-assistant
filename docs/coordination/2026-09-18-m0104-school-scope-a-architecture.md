# M01.04 学校范围授权：A 级断言边界小包

基线：`integration/sol-auth-closure@4e9945eacf682d5c260ef02afa1f55dfc4994ca3`。
本包只处理服务端已生成授权证明的结构断言与批量预检，不改 M02.05/V0012，不启动真实数据库，
不引入新的角色、租户或缓存实现。

## 目标与冻结输入输出

输入必须来自 `OperatorAuthorizationApi.authorize(operatorId, action, requestedScope)` 返回的
`AuthorizedSchoolScope`；客户端 header、菜单可见性和自造的 school/campus 条件不能直接进入查询。
`SchoolScopeQueryGuard.require(proof, expectedAction)` 只输出 `AuthorizedSchoolQuery`（schoolId、
可选 campusId、grantId、authorizationVersion），不携带 operator 可改写的原始请求字段。
批量命令统一先调用 `ScopePolicyCompiler.authorizeAll`，所有 scope 证明成功后才允许业务 handler
第一次写入。

## 安全边界

1. 持久化的未知 `scope_type` 或未知 action 必须 fail closed，映射为 `SCOPE_DENIED`/`ACTION_DENIED`，
   不能冒泡成 500，也不能当成 PLATFORM。
2. proof 缺失、过期、授权版本非正数、未知 `grantScopeType` 或 action 与调用方期望不一致时，guard
   不返回任何查询条件。
3. SCHOOL/CAMPUS 授权只覆盖同校请求；CAMPUS 还必须匹配同一校区。跨校混合批量必须在 handler 前拒绝，
   并断言业务写入计数为零。
4. 本包只保证授权断言和预检；实际 SQL、脱敏、案件权限与财务权限仍由业务模块执行，不能由 guard
   自动扩大范围。

## 文件所有权与禁止范围

| 执行者 | 可修改文件 | 禁止修改 |
| --- | --- | --- |
| Terra（唯一执行者） | `backend/campus-module-identity/src/main/java/**/application/authorization/**` 中与断言/未知 scope 处理直接相关的类；同目录及 `domain/authorization/**` 的单测 | `backend/campus-module-media/**`、M02.05/V0012、M09/M08、数据库迁移、根 CI、OpenAPI |
| Sol | 本公共契约文档、跨模块错误语义、最终整合与回归 | 不覆盖 Terra 的在途工作 |

若实现需要新增错误码，先停在 Terra 分支并向 Sol 提交契约变更说明；本包优先复用已有
`OperatorAuthorizationFailure`，避免扩大公共错误表。

## A 级验收用例

- `OperatorGrant.covers`：SCHOOL 只覆盖同校，CAMPUS 只覆盖同校同校区，PLATFORM 仅按服务端明确授权
  返回；跨校或缺校区请求拒绝。
- `SchoolScopeQueryGuard`：null proof、非正版本/ID、未知 scope 类型和 action 不匹配均拒绝且不产生
  `AuthorizedSchoolQuery`；合法 proof 仅产出服务器字段。
- `ScopePolicyCompiler`：S1/S2 混合批量在 handler 前拒绝，handler 调用次数和业务写入计数均为零；
  空批量和重复/未知 scope 不能默认为全校。
- `OperatorAuthorizationService`：数据库夹具中的未知 `scope_type`/action 默认拒绝并映射稳定失败，
  不抛出未处理 `IllegalArgumentException` 或选择 PLATFORM。

A 级只接受窄单测/纯内存授权替身证据；B 真实 MySQL 锁/CAS 与业务列表、导出、附件尚未执行，C 真实
部署/跨环境 Token 尚未执行。状态初始为 A `IN_PROGRESS`、B `NOT_RUN`、C `BLOCKED_EXTERNAL`。
