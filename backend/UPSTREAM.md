# 后端上游来源

- 官方仓库：`https://github.com/YunaiV/ruoyi-vue-pro.git`
- 分支：`master-jdk17`
- 固定提交：`8e43004cf68a405cd3485f98f8a539b97ca6544a`
- 许可证：MIT，原始 `LICENSE` 保留在本目录。
- 导入方式：从固定提交导出源码到 `backend/`，不保留嵌套 `.git`。

## 保留范围

保留 `yudao-framework`、`yudao-module-system`、`yudao-module-infra`、`yudao-module-member`、
`yudao-module-pay`、`yudao-server` 和 MySQL SQL。ERP、CRM、商城、工作流、报表、物联网、IM、
通用 AI 等非首期模块未导入。

## 本地补丁

1. 根 POM 与 BOM 使用固定本地版本 `2026.08-campus.1`，并记录上游仓库、分支、提交和许可证属性。
2. `yudao-server` 只依赖保留模块并接入 `campus-common`。
3. `application.yaml` 增加开发环境显式模拟网关标识；新增无默认凭据的 `application-prod.yaml`。
4. 新增 `campus-common`，实现启动配置保护和最小 readiness 探针。
5. readiness 对所有调用者只公开总体状态；数据库或缓存缺失、未知或失败时返回 `DOWN`。
6. 本地与开发配置中的上游公开第三方测试凭据改为环境变量或无效占位值。
7. MySQL 初始化脚本移除已删除的第三方社交客户端演示凭据种子。

上游 Java 源码没有就地修改；自有代码放在 `campus-common` 和后续 `campus-*` 模块中。
