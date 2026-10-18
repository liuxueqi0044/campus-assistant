# M02.04 media/identity boundary audit

审查基线为 `f82d954ca47978f050f782cb05e6f86e77813466`（`integration/sol-auth-closure`）。本审查在独立 worktree `campus-assistant-gpt6-media-identity-audit`、分支 `review/gpt6-media-identity-audit` 完成，所有权限限定在媒体/身份实现与本记录。

## 审查模块与验收标准

1. `ContentInspectionResult`：自动回调只接受同一 `contentVersion` 和期望 `version`；终态不可重复回调；人工决定只允许 `REVIEW_REQUIRED|ERROR`，且必须为 `PASS|REJECT`、正数操作员和 1..500 字符理由；`MANUAL+PENDING` 构造必须失败。
2. `VerificationEvidencePort`/identity 应用服务：证据声明在幂等事务中绑定并写入申请；失败时绑定、申请、审核历史、membership、outbox 与幂等预留一起回滚；相同 key/集合只重放一次。
3. media evidence adapter：资产集合规范化为升序且无重复；旧 proof 仅在完整集合、同 owner/context/purpose/resource、同窗口和当前 generation/version 下复用；部分重合、子集、交集或跨上下文拒绝；拒绝后的申请不释放旧 binding，重提应形成新资产集/新 proof。

## 结论

未发现有明确证据支持的可修复高风险缺陷，因此没有修改业务代码。旧 proof 的复用检查位于 `MembershipVerificationEvidenceAdapter`，而 `campus_verification.proof_reference` 的 V0011 唯一约束会拒绝 REJECTED 后同资产集重提；该失败发生在同一幂等事务内并回滚，符合“重提必须新资产集”的 fail-closed 约束。集合检查、锁定顺序、generation/version 二次校验、幂等事务和 `MANUAL+PENDING` 构造均有对应实现及测试。

## 实际验证

- `python contracts/tools/check_contracts.py`：通过（OpenAPI 最小语义、YAML/JSON、`$ref`、错误码注册一致）。
- `mvn -pl campus-module-media,campus-module-identity -am '-DskipTests' '-Dcheckstyle.skip=true' test-compile`，Temurin 17.0.20.1、Maven 3.9.16、`MAVEN_OPTS=-Xmx768m`：`BUILD SUCCESS`。
- `mvn -pl campus-module-media,campus-module-identity -am '-Dtest=ContentInspectionResultTest,MembershipVerificationEvidenceAdapterTest,VerificationApplicationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：12 tests，0 failures/errors，`BUILD SUCCESS`。

第一次未带 `-am` 的窄测因陈旧 `campus-common` target 产生 `NoSuchFieldError: MEDIA_ALREADY_BOUND`；随后按依赖闭包用 `-am` 重跑通过，该现象不构成产品缺陷。

## A/B/C 状态与下一步

- A：本次目标域单元/适配器窄测 PASS（12/12）；M02.04 完整验收仍未接入服务/持久化渠道。
- B：NOT_RUN；未启动 Docker/MySQL/VM，也未宣称真实数据库或 HTTP 集成证据。
- C：BLOCKED_EXTERNAL；真实检查渠道、回调认证、队列和发布消费者尚未在本包接入。

下一步是在补齐 M02.04 持久化、outbox、渠道回调和权限入口后，分别补跑 B 级真实数据库/HTTP 测试与 C 级真实渠道回调；在此之前保持当前领域边界和错误状态，不把窄测结果扩写为渠道验收完成。
