# M01.03/M02.02/M02.03 真实 B 重申拒绝小包

## 场景

现有跨模块 FilterChain 测试已覆盖首次提交、同幂等键异序重放和 application insert 失败回滚，
但没有证明同一真实媒体集合在首次成功后使用新幂等键提交子集/重申时，会在 evidence adapter
边界拒绝并保持零新增行。该场景直接验证 V0010 单资产绑定与 V0011 proof 唯一性的组合，而不是
用随机 proof fixture 绕过 adapter。

## 冻结输入与输出

- 测试从真实 MySQL/Valkey、真实 `MembershipVerificationEvidenceAdapter` 和真实 HTTP FilterChain
  开始；只允许复用当前 `CAMPUS_M0101_B_*` 隔离环境。
- 首次提交两个本人有效 asset，记录 application、binding、proof 计数。
- 用新幂等键提交其中一个 asset（子集）或同集合重申；服务端必须在 evidence 边界返回明确 4xx
  （优先现有 `MEDIA_ALREADY_BOUND`/对应公共错误），不得创建第二 application、binding 或 proof。
- 断言失败后原 application/binding/proof 计数、资产状态与 validFrom/expiresAt 不变；同一失败请求
  不得污染幂等记录到可伪装成功的状态。

## 文件所有权

- Terra（唯一执行者）：仅修改
  `backend/yudao-server/src/test/java/cn/iocoder/yudao/server/integration/M0203TenantBoundaryIntegrationIT.java`
  及必要的该测试内部辅助方法；不得改生产代码、迁移、workflow、其他测试或契约。
- Sol：本架构包、workflow dispatch、公共错误语义审查、复验、进度记录、推送。

## 验收

- A：测试编译通过；现有异序幂等与 rollback 断言保持通过。
- B：隔离 MySQL/Valkey 下真实 FilterChain 用例通过，证明子集/重申 4xx 与零新增行。
- C：真实部署/云对象存储仍为 `BLOCKED_EXTERNAL`；本包使用本地私有存储适配器，不把 C 当作 B。

## CI

当前 workflow 的自动 push 触发仅包含 `main`；integration 分支需用已有授权通过
`workflow_dispatch` 指定 `ref=integration/sol-auth-closure`。不修改 workflow 触发范围，避免扩大 CI 成本。
