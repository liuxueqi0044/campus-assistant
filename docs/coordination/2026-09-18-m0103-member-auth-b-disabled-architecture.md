# M01.03 成员认证真实 B 状态拒绝小包

## 目标

补足真实 HTTP + MySQL/Valkey 认证闭环中“已存在成员被禁用后再次登录必须失败且不得新增 token”
的 B 证据。复用既有 M01.01 B 隔离环境与真实 `WechatIdentityAdapter` mock 边界；不引入真实微信凭据，
不改认证生产逻辑除非测试证明状态校验缺口。

## 冻结场景

1. 通过真实 `/app-api/campus/v1/auth/wechat/login` 首次创建并绑定成员，记录 member、binding、token 行数。
2. 在同一事务隔离 DB 中把该成员 `status` 更新为 DISABLE。
3. 使用同一有效 app/openId 再次登录；期望返回既有 AUTH_LOGIN_USER_DISABLED 4xx，成员/binding 行数不变，
   disabled 前 token 不被伪造新增，且不产生新的 access token。
4. 恢复/清理由测试 fixture 完成，不能影响其它用户；跨租户/普通失败路径继续由现有 M01.01 B 覆盖。

## 文件所有权

- 唯一 Terra：仅修改
  `backend/yudao-server/src/test/java/cn/iocoder/yudao/server/integration/M0101MemberOAuth2IntegrationIT.java`
  及必要的 M01.03 进度/验收记录，不改生产、迁移、workflow、M02.05/V0012。
- Sol：持有公共契约、审查 diff、运行窄测、真实 CI dispatch、CSV 状态及最终集成。

## 验收门槛

- A：测试可编译，既有首次/重复/失败登录测试保持通过。
- B：隔离 MySQL/Valkey 真实 FilterChain 场景通过；disabled 重登 4xx，成员/binding/token 计数无新增，
  原 token 行状态符合现有上游撤销语义。
- C：真实微信 code2session、真机与云凭据继续 `BLOCKED_EXTERNAL`。

不得把单测或随机 mock 结果写成 B PASS；完整 CI 的 M01.01 B/M01.03 真实选择器通过后再更新状态。
