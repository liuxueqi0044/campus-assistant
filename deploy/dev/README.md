# 本地开发依赖

本目录只提供数据库和缓存依赖；应用凭据、微信、对象存储和支付均使用后续定义的显式测试适配器。生产环境不得复用这里的密码或模拟网关。

## 启动前提

1. 复制 `.env.example` 为 `.env`，把三个示例值替换为仅本机使用的随机密码。
2. 启动 Docker Desktop 或兼容的 Docker Engine。
3. 在本目录运行 `docker compose config` 检查配置，再运行 `docker compose up -d`。

## 固定依赖

- MySQL 8.4.11，镜像由版本和多架构 digest 双重固定。
- Valkey 8.1.10 Alpine，镜像由版本和多架构 digest 双重固定。
- 端口只绑定 `127.0.0.1`。
- MySQL 技术时间使用 UTC；课表本地日期与学校时区由业务模型另行保存。

## 本机验证结果（2026-09-17）

- `docker compose config` 通过；MySQL 与 Valkey 容器均达到 healthy。
- MySQL 应用账号 TCP 登录、UTC 时区、`utf8mb4` 与基线 SQL 导入通过。
- Valkey 鉴权、读写以及 Redisson 4.7 的 TTL、Lua 和分布式锁兼容用例通过。
- local 与 prod profile 均能连接真实依赖，`GET /health/readiness` 返回 `UP`。

尚未执行：卷删除后的恢复演练、备份恢复、多机与网络故障注入。这些属于后续运维模块，不得由本记录推定通过。
