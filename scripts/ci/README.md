# 模块验收入口

M00.06 提供按模块编号执行的统一入口。当前可运行的 A 级选择器是：

```text
python scripts/ci/m00_acceptance.py --module M00.04 --level A --report artifacts/acceptance/m00.04-a.json
python scripts/ci/m00_acceptance.py --module M00.05 --level A --report artifacts/acceptance/m00.05-a.json
python scripts/ci/m01_acceptance.py --module M01.01 --level A --report artifacts/acceptance/m01.01-a.json
```

命令从仓库根目录执行，并通过 `CAMPUS_MYSQL_IT_URL`、`CAMPUS_MYSQL_IT_USERNAME`、
`CAMPUS_MYSQL_IT_PASSWORD` 连接专用空测试库。该库会被测试重建，禁止指向开发、共享或生产 schema。

退出码非零表示 `FAIL` 或 `EXTERNAL_NOT_READY`，两者都不能作为发布通过。报告包含源码 SHA、迁移文件与
哈希、关键依赖清单、实际用例计数、层级、状态和未验证项。验收入口还会执行唯一约束破坏测试；只有正常
用例通过、破坏后并发用例确实失败，M00.04 A 才会报告 `PASS`。M00.05 显式执行事务 outbox
的真实数据库场景；M01.01 同时执行身份边界单元证据和真实 MySQL 并发事务场景。三者都会把测试跳过、
测试数为零、报告缺失或 Maven 异常判为失败。

CI 共用一个隔离 MySQL 服务顺序执行三个选择器，并在归档报告后用以下命令做最终 fail-closed 汇总：

```text
python scripts/ci/check_acceptance_reports.py \
  --report M00.04=artifacts/acceptance/m00.04-a.json \
  --report M00.05=artifacts/acceptance/m00.05-a.json \
  --report M01.01=artifacts/acceptance/m01.01-a.json
```

固定样本和生成证据的敏感数据扫描命令：

```text
python scripts/ci/scan_test_data.py --report artifacts/security/test-data-scan.json
```

扫描失败必须阻断流水线。报告只记录规则、文件和行号，不复制命中的敏感值。
