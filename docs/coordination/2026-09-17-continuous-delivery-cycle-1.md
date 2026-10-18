# 校园助手持续交付第 1 周期协调基线

日期：2026-09-17。本文是三任务继续开发的公共冻结记录；若与聊天描述冲突，以本文件后续提交版本和公开契约为准。

## 1. 基线与分支策略

- 集成基线：`main@8cbf1aa97ab622dfea2371da7687ca5994800abf`，GitHub CI #16 五项通过。
- L 工作区：`work/campus-assistant`，唯一写 `main`、共享契约、错误码、根依赖、CI、迁移顺序和状态矩阵。
- A 旧分支 `feature/m0103-membership-verification@1a228f3` 已等价集成进 `main`，必须保留。A 在原独立 worktree 新建 `feature/cycle1-identity-authorization`，从本协调提交所在的 `main` 开始，不重放旧提交。
- B 旧分支 `feature/student-real-api@72b093b` 已等价集成进 `main`，必须保留。B 在原独立 worktree 新建 `feature/cycle1-auth-ui-admin`，从本协调提交所在的 `main` 开始，不重放旧提交。
- A/B 不直接改主工作区；L 不在 A/B 所有权目录重复实现。三条写线之外不再增加实现智能体。

## 2. 本周期文件所有权

| 执行线 | 子模块 | 独占文件范围 | 禁止修改 |
|---|---|---|---|
| L | M02.01—.03 | `backend/campus-module-media/**`、M02 测试、M02 迁移、公共启动装配；经提交统一修改 `contracts/**`、根 `pom.xml`、`.github/workflows/**` | A/B 的 identity 领域实现与前端页面 |
| A | M01.04、M01.06，随后 M01.05；M01.03 调用方适配 | `backend/campus-module-identity/**` 中授权、账户生命周期、跑腿资格及适配测试 | `campus-module-media/**`、`contracts/**`、根构建、前端 |
| B | M10.04、M11.01—.02、上一轮真实运行时证据 | `frontend/apps/student/**`、既有管理端应用内本周期页面/测试、`frontend/docs/**` | 后端、`contracts/**`、根 CI、数据库迁移 |

公共契约变更由执行者提交“建议字段/失败语义”，L 落盘。若实际文件跨越所有权，先发消息取得单文件授权。

## 3. 迁移编号中央分配

历史 `V0001`—`V0006` 不修改。第 1 周期保留以下连续编号：

| 迁移 | 负责人 | 用途 |
|---|---|---|
| `V0007__M01_04_school_scope_authorization.sql` | A | `campus_operator_grant`、授权变更审计所需结构 |
| `V0008__M01_06_account_resource_authorization.sql` | A | 账户生命周期扩展/追加日志；不得复制 member 主表 |
| `V0009__M02_01_media_asset.sql` | L | 媒体资产、上传票据/generation 与存储元数据 |
| `V0010__M02_02_media_binding.sql` | L | 文件验证结果、业务绑定和并发唯一约束 |
| `V0011__M02_03_media_access_log.sql` | L | 私有访问审计 |
| `V0012__M01_05_runner_qualification.sql` | A | 跑腿资格、状态日志与版本 |

编号保留不代表可以修改别人的迁移。若某项最终无需 schema，保留号由 L 明确释放，不能自行占用。

## 4. M01/M02 内部接口冻结

### 4.1 M01.03 调用 A 的授权与账户端口

- 将现有端口最小升级为 `ReviewAuthorizationPort.assertAllowed(operatorId, schoolId, campusId, action)`；M01.03 的 probe 已持有服务端 `campusId`，调用方不得从请求体补传授权范围。
- action 精确使用现有值：审核为 `VERIFICATION_REVIEW`，撤销为 `MEMBERSHIP_REVOKE`。未知 action 默认拒绝。
- M01.04 的授权事实来自后台操作者、上游 RBAC 和 `campus_operator_grant` 的交集；菜单可见性、客户端 schoolId 或平台管理员猜测不能代替授权。`CAMPUS` grant 只命中同一 campus，绝不能放宽成整校；`SCHOOL` grant 可覆盖该校已启用校区；`PLATFORM` 必须是显式 scope type。
- `AccountStatusPort.assertActive(userId)` 的权威 member 存在性/停用状态继续复用上游 `MemberUserService`；M01.06 可以扩展生命周期语义，但不得新建同义用户主表。敏感动作对未知或不可识别状态默认拒绝。

### 4.2 M01.03 调用 L 的媒体证据端口

- A 保留 `VerificationEvidencePort` 接口及 identity 侧调用适配；L 提供媒体内核实现和启动 bean 装配。
- 输入：服务端当前 `userId/schoolId/campusId`、契约枚举 `method`、opaque `evidenceRefs`。
- 输出：不含 URL 的 `VerificationProof(reference, validFrom, expiresAt)`。
- 只有 owner 匹配、purpose=`MEMBERSHIP_VERIFICATION`、generation 为已验证当前版本、未删除且未被其他资源占用时才返回证明。
- `MEDIA_NOT_OWNED`、`MEDIA_NOT_READY`、`MEDIA_PURPOSE_MISMATCH`、`MEDIA_ALREADY_BOUND`、`FILE_INVALID` 均 fail closed；不得用空列表或固定 PASS 适配器上线。

### 4.3 M01.05 锁序

跑腿敏感动作统一把资格行作为 eligibility barrier：`runner qualification → 账户/成员权威检查或变更 → order → runner load → fund guard`。接单先锁资格再读账户与 membership；账户停用或成员撤销若要求立即禁接单，也必须先经资格 barrier，再改变账户/成员事实。接单先提交的订单按既有订单善后，停用先提交则新接单必拒绝。

M01.03 现有普通审核继续使用 `verification → membership`；不涉及跑腿资格时不得无故扩锁。M01.05 资格停用只锁资格，不反锁订单。M01.03 撤销接入 M01.05 后，由公开 eligibility-barrier 端口在取得 verification probe 后、进入写事务前按上述顺序协调；在该端口完成前不得宣称“撤销与接单”B 级并发通过。后续 M05/M08 若需要不同顺序，必须先由 L 更新本记录并补并发验收。

## 5. HTTP 契约冻结

现有 M01.03 路径继续有效：

- `POST /app-api/campus/v1/verifications`：当前用户提交申请，要求 `Idempotency-Key`。
- `GET /app-api/campus/v1/membership?schoolId&campusId`：当前用户 `SELF_CHECK`，不是申请详情接口。
- `POST /admin-api/campus/v1/verifications/{id}/review`：后台审核，要求 `Idempotency-Key` 和 `expectedVersion`。

本周期 M02.01—.03 新路径冻结为：

- `POST /app-api/campus/v1/media/upload-tickets`
- `PUT /app-api/campus/v1/media/assets/{assetId}/content`：仅本地私有存储开发适配器使用；生产云适配器返回受限直传票据，不暴露此代理上传能力。
- `POST /app-api/campus/v1/media/assets/{assetId}/complete`
- `POST /app-api/campus/v1/media/assets/{assetId}/bindings`
- `POST /app-api/campus/v1/media/assets/{assetId}/downloads`
- `GET /app-api/campus/v1/media/downloads/{deliveryToken}`：短期、单资产、重新检查状态的代理流。

JSON ID 一律为十进制正整数字符串；owner 从登录事实取得；客户端文件名不参与对象 key；任何响应/事件/日志不得携带永久私有 URL、签名或临时密钥。

管理端申请列表、详情、学校授权查询尚未冻结为已实现路径。B 可以建立类型化 client 和明确的“接口未就绪/失败恢复”页面状态，不得伪造成功；A 提交查询能力建议后由 L 统一落 OpenAPI。

## 6. M02 错误语义

必须加入公共错误注册表且保持稳定语义：

- 上传：`UPLOAD_PURPOSE_DENIED`、`FILE_SIZE_EXCEEDED`、`UPLOAD_QUOTA_EXCEEDED`、`STORAGE_UNAVAILABLE`
- 完成/绑定：`MEDIA_NOT_OWNED`、`MEDIA_NOT_READY`、`MEDIA_PURPOSE_MISMATCH`、`MEDIA_ALREADY_BOUND`、`FILE_INVALID`
- 下载：未获授权统一 `RESOURCE_NOT_ACCESSIBLE`；已获权但对象不可用为 `MEDIA_UNAVAILABLE`

数值码由 L 在实现提交中分配，A/B 不硬编码新数值。HTTP 层按公共注册表映射；不能靠 200 响应中的自由文本区分失败。

## 7. 首批独立验收和重型任务时段

- L：M02 确定性单测、真实 MySQL 锁/唯一约束验收、本地私有存储真实字节测试、HTTP/契约测试。COS/OSS 仅在真实凭据和隔离桶具备时记 C。
- A：授权跨校零变更、未知动作拒绝、撤权即时生效、账户停用旧 Token 敏感动作拒绝；M01.03 接真实授权/账户/媒体适配后做 B 级联调。
- B：认证上传/处理中/失败恢复/重试/撤销状态，管理页服务端 403/隐藏资源/版本冲突；H5 与微信构建不等于真机 C。
- 重型 MySQL/完整 Reactor 构建由 L 汇总合并时执行。A/B 先跑模块级测试；需要真实 MySQL 时提前通知，避免三个工作区同时争抢同一服务。

## 8. 已知外部条件

截至本基线，没有可核实的真实微信 `code2session`、微信开发者工具/真机、COS/OSS 私有隔离桶、内容审核/OCR 或支付渠道证据。相关 C 级状态保持 `NOT_RUN/BLOCKED_EXTERNAL`，其余 A/B 级工程继续推进。
