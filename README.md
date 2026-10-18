# 校园助手

面向大学生的校园生活助手，首期覆盖课表、同校区二手、校园跑腿和线下活动。学生端以微信小程序为目标载体，配套运营管理端；后端采用模块化单体。

## 当前状态

- 架构基线：v1.1，12 个大模块、76 个可独立交付子模块。
- 当前实施阶段：M00 工程底座持续验收，M01 身份与校园开始实施。
- M00.01–M00.06、M01.01 与 M01.02 已有 A 级自动化证据；真实微信、真实 member/OAuth2 联调、业务域和外部渠道仍待 B/C 级验收。
- 学生端前端已纳入本仓库，保留 demo/real 双模式、18 个页面及 context 联调适配；页面演示不等同真实业务后端完成。

## 目录

```text
backend/                 固定上游提交导入的 Java 后端与校园模块
frontend/                uni-app / Vue 3 / TypeScript 学生端
contracts/               HTTP、事件和适配器契约
database/migrations/     自有增量迁移唯一入口
deploy/dev/              本地开发依赖
docs/development-status.md 实际开发与验收状态
docs/adr/                架构决策记录
docs/upstream-manifest/  上游来源、版本、许可与本地补丁
tests/fixtures/          脱敏固定测试样本
```

完整产品及模块规范位于 `docs/specification/`。本地 PowerShell 会话可通过 `.\scripts\dev-env.ps1` 加载隔离的 Java 17 和 Maven 工具链。

## 实施规则

1. 每个 `Mxx.yy` 子模块由独立执行智能体负责明确目录，架构负责人复核契约与验收证据。
2. 公共权限、资金、事务、幂等和共享领域模型的变更必须经过架构复核。
3. `designed`、`implemented`、A/B/C 级验收状态分别记录，禁止用静态检查代替运行验收。
4. 不提交真实密钥、个人材料、支付凭据或生产数据。
