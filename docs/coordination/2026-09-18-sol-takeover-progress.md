# Sol 接管与认证闭环进度

日期：2026-09-18。整合分支：`integration/sol-auth-closure`，基线：`main@8893d4e`。

## 保护范围与旧成果

- 旧 A 分支 `feature/cycle1-identity-authorization@d01f61b` 和旧 B 分支
  `feature/cycle1-auth-ui-admin@828a153` 只作审查来源，不直接合并、不重置。
- 两分支共同基线是 `c544071`，早于 main 的 M02.01 与学生端失败恢复。正常三方合并不会因
  分支落后而删除 main 的新增文件；本批选择逐提交重放，是为了去掉已等价集成的提交、控制
  审查范围并逐级验证契约。A 只允许按 `426de6f`、`457968c`、`d01f61b` 顺序重放并逐级
  验收；M01.05 的锁序变更后置。
- B 的 `9bade4f` 已等价进入 main。`0f3fc5d` 需按最终 M02.02 契约改造后再集成。
  `795cf77` 一次导入完整管理端上游（1114 文件、约 12 万行），单独做来源、依赖、构建与
  裁剪审查，不能阻塞媒体闭环。

## 首批交付：M02.02 文件验证、占用绑定与认证证据

### 行为与复用

复用 M02.01 的 `campus_media_asset`、不可变对象 key、本地私有存储、上传票据和错误码，
把上传完成后的对象推进为可验证、可占用的资产，并为 M01.03 提供真实
`VerificationEvidencePort`。本批不引入云桶、微信或完整管理端。

### 冻结状态与端口

- 资产状态：`UPLOADING -> VALIDATING -> VALID`；确定性文件错误进入 `REJECTED`；存储暂时
  不可用不得伪装为 `REJECTED` 或 `VALID`。
- 完成输入：登录 owner、assetId、票据 generation。服务端读取不可变对象并核对实际长度、
  SHA-256、magic bytes 与安全解码结果。客户端 hash、扩展名和 MIME 声明都不是事实来源。
- 完成输出：assetId、generation、`VALIDATING|VALID|REJECTED`、assetVersion；重复完成同一
  generation 返回同一终态，不重复生成验证结果。旧 generation 的结果永远不能写入新 generation。
- 占用绑定输入：服务端 actor/owner、assetId、generation、purpose、资源类型与资源标识。
  事务内按 assetId 升序锁资产，核对 owner、purpose、generation、`VALID`、未删除且未占用。
- 每个资产默认只能绑定一次，数据库以 asset 唯一约束裁决并发；他人、未验证、跨用途、
  已占用分别映射 `MEDIA_NOT_OWNED`、`MEDIA_NOT_READY`、`MEDIA_PURPOSE_MISMATCH`、
  `MEDIA_ALREADY_BOUND`，格式失败为 `FILE_INVALID`。
- `VerificationEvidencePort.verify` 不能只查询验证状态。它必须在调用方 Spring 事务中，
  原子占用全部 evidenceRefs 为一个 `MEMBERSHIP_VERIFICATION_PROOF`，再返回 opaque proof。
  M01.03 随后写认证申请；申请写入失败时占用随同事务回滚。相同 owner、学校、校区、方式和
  完整 evidence 集合的重放返回原 proof；部分重合或不同上下文拒绝。
- proof 在首次占用时保存稳定的服务端 `validFrom/expiresAt` 和绑定 generation，重放与审核
  重查只能返回原时间窗口，不能自动续期。M01.03 审核时重新调用端口，只有同一 proof 的全部
  绑定仍匹配当前 generation、状态、完整 evidence 集合与上下文时才返回证明。

### 文件所有权

- Terra：只改 `backend/campus-module-media/**` 中存储读取、验证管线、完成服务、资产/绑定
  repository 与相应模块单测；不得改 identity、`contracts/**`、`database/migrations/**`、根 CI。
- Luna：在独立审查分支按顺序重放并验证 A 的 `426de6f`、`457968c`，只允许为集成兼容性
  修改 `backend/campus-module-identity/**` 的测试或实现；不得取 `d01f61b`，不得改公共契约/迁移。
- Sol：负责 `VerificationEvidencePort` 跨域事务语义、`V0010`、HTTP/OpenAPI、公共错误语义、
  MySQL 并发验收、旧分支最终重放、提交与集成。

### 首批验收

1. JPG 名称但非支持格式：完成返回 `FILE_INVALID`，资产不可绑定。
2. U1 操作 U3 资产：拒绝且 binding 零行；用途不符同样零行。
3. 同一 asset 两个资源并发绑定：真实 MySQL 下恰好一个成功。
4. 完成旧 generation 与续票/新 generation 竞态：旧结果不能使新内容 `VALID`。
5. M01.03 提交成功时认证申请与全部 proof binding 同事务提交；在申请插入处注入失败时两者
   都不提交。重复幂等申请不新增 proof/binding。
6. 模块单测、M02.02 MySQL A 级选择器、M01.03+M02 B 级跨模块测试通过；本地私有存储使用
   真实字节。云桶、真实微信与真机继续是 C 级 `BLOCKED_EXTERNAL/NOT_RUN`。

## 本地运行条件

- 便携 Temurin 17.0.20.1 与 Maven 3.9.16 位于原 main 工作目录的同级 `.tools`。新建的独立
  worktree 不能直接使用当前 `scripts/dev-env.ps1`（它按 worktree 父目录寻找 `.tools`）；本批
  通过显式设置 `JAVA_HOME` 与 Maven 路径运行。Node 22、npm 10、pnpm 可用，学生端依赖已安装。
- Docker Desktop 已安装但当前 Linux engine 不可连接，MySQL/Valkey 未监听；`deploy/dev/.env`
  尚未创建。启动 engine 并使用仅本地随机开发密码即可跑真实 DB/缓存验收。
- 本地私有存储无需 COS/OSS 凭据。微信 code2session、云桶和真机只阻塞各自 C 级证据。
- 本机约 31.4 GiB 物理内存，但接管期间可用量持续低于 6 GiB。同一时刻只允许一个重型构建或
  数据库验收；低于 6 GiB 时不新启本地 Maven 全构建、Docker 数据库或第二套数据库环境，改用
  窄范围静态检查与隔离 GitHub Actions。不得停止用户或其他项目的 VBox/Node 进程。

## 监督循环

Sol 直接执行：派工 -> 等待 -> 审查 -> 打回 -> 复验 -> 集成 -> 下一小交付。Terra/Luna
只向 Sol 交付；每个里程碑记录 SHA、实际命令、结果、跳过项和下一风险。

## 2026-09-18 首轮窄范围验证

- `campus-module-media` 及其依赖编译成功；媒体验证、完成、绑定、认证证明适配和本地私有存储
  共 26 个测试通过，失败、错误与跳过均为 0。运行时只启动一个 Maven 进程，堆上限 1536 MiB。
- 学生端 `media.test.ts` 与 `verification.test.ts` 共 14 个测试通过，`npm run typecheck` 通过。
- M02.02 真实 MySQL 并发唯一约束、事务回滚和 Flyway 证据仍待独立验收，不据此把模块状态改为
  `PASS`。本机 Docker engine 当前不可连接，且本轮空闲内存低于 6 GiB，暂不叠加数据库环境。
- 已加入失败关闭的 `scripts/ci/m02_acceptance.py` 与 `M0202MySqlIntegrationTest`。选择器要求显式
  的三项 M02.02 数据库配置，且只允许 `campus_m0202_test*` 或
  `campus_m0202_acceptance*` 隔离 schema，防止 Flyway `clean` 误作用于普通数据库。缺配置只记
  `EXTERNAL_NOT_READY`，不产生伪 PASS；URL 不满足隔离规则直接失败且不启动 Maven。
- 验收脚本自身 10 个 Python 测试通过，覆盖缺配置、缺/空/跳过/失败的 Surefire suite、启动器失败、
  敏感诊断脱敏和隔离 schema URL。Java 验收类尚未在真实 MySQL 上执行，因此模块仍为进行中。

## 下一交付冻结：M02.03 认证材料私有读取

首个 M02.03 增量只闭合 `MEMBERSHIP_VERIFICATION_PROOF`，不提前实现聊天、课表、跑腿证据或
云桶签名。它复用 M02.02 的稳定 proof resource 与 M01.06 的 `ResourceAuthorizationApi`：

- `POST /app-api/campus/v1/media/assets/{assetId}/downloads` 只从当前 member 登录事实构造 actor，
  服务端加载资产及唯一 binding，不接受客户端改写 owner、purpose、resourceType/resourceId。
- 管理审核通过独立 admin 入口传具体认证申请、caseReference 与 reason；operator actor 从管理会话
  取得。身份域 provider 以 proofReference 找到认证申请并校验学校/校区审核授权；未知 proof、
  普通运营、跨校或缺少案件理由一律返回同一不可探测响应。
- 签发前必须重查资产为当前 generation/version 的 `VALID`、binding generation 一致且 proof 窗口
  未过期，再调用资源策略。成功只返回短期、单 actor/单 asset/单 generation 的 opaque delivery
  token 与到期时间，不返回 objectKey、永久 URL、签名参数或临时密钥。
- `GET /app-api/campus/v1/media/downloads/{deliveryToken}` 仍要求同一登录 actor；重新检查 token 到期、
  当前资产/binding/资源策略后，通过本地私有存储代理真实字节，响应 `Cache-Control: no-store`。
  撤权、换 generation、过期、他人重放都在打开对象前拒绝。
- `V0011__M02_03_media_access_log.sql` 记录 actor 类型/ID、asset/generation/version、binding、资源、
  purpose、动作、allow/deny、caseReference/reason、策略审计标签、traceId 与发生时间；禁止记录 token、
  objectKey、URL 或内容。允许结果的审计无法持久化时不得签发或打开；存储打开失败不得记成已送达。

分工：Terra 只实现 media 模块内的签发/代理核心、token codec、访问审计 repository 及单测；Sol 集中
处理 V0011、OpenAPI、身份域 proof policy、app/admin HTTP 接线和跨模块集成；Luna 独立检查隐藏资源
响应、审计 fail-closed、撤权重放与真实本地字节验收。三个工作包不得分别启动数据库，重型验收继续串行。

## 2026-09-18 远端验收与 M02.03 首轮集成

- GitHub Actions `campus-assistant-ci #18` 针对 `d80623d` 实际完成：契约、后端测试/完整打包与学生端
  全部成功。Secret scan 当时失败，后续按原始日志和精确 fingerprint 单独审计，不把失败笼统归为
  “历史样例”或本批代码通过证据。
- M02.02 的真实 MySQL 选择器已运行 4 个 `M0202MySqlIntegrationTest` 用例；并发唯一约束、旧
  generation CAS 与业务/binding 回滚通过。唯一失败是验收代码把 Flyway 从文件名生成的 description
  `M02 02 media binding` 误写为 `M02.02 media binding`，不是迁移失败；断言已修正，仍须下一次真实
  MySQL 运行通过后才能把 M02.02 A 记为 PASS。
- 同一轮还暴露旧 A 集成后的运行时缺陷：M01.06 登录新增查询的校园全局表没有声明绕过上游 SaaS
  `tenant_id` 拦截，真实请求得到 `Unknown column 'tenant_id'`。所有 `campus_*` MyBatis DO 现统一标注
  `@TenantIgnore`；校园范围继续由 campus policy、owner 与学校授权约束，不借用上游租户列。该修复也
  防止 M02.01/M02.02/M02.03 的真实 HTTP 请求向无 `tenant_id` 的媒体表注入错误条件。
- Terra 的 M02.03 下载内核经两轮打回后已集成：operator 案件上下文随 grant 固定并在打开时重授权；
  unknown/expired/invalid token、跨 actor、撤权与 generation/version/binding 变化统一隐藏响应并写
  `OPEN_AUTHORIZED/DENY`；grant/audit 非单行写入 fail-closed；原始 token 只返回一次，数据库只存
  SHA-256，审计模型不含 token/hash/objectKey/URL/content。
- Sol 已接入 app/admin 签发与代理读取端点。令牌响应包含契约要求的 expiry、asset generation/version；
  代理在授权后核对真实存储长度，设置 `Cache-Control: no-store`、`nosniff` 与安全附件名，再读取本地
  私有对象。V0011 为 operator grant 保存规范化 caseReference/reason，并以数据库约束区分 member 与
  operator 上下文。当前只完成静态契约检查，因本机可用内存低于 6 GiB，Java/真实 MySQL 证据等待远端
  串行验收，M02.03 状态不得提前标为 PASS。

## 2026-09-18 CI #19/#20 打回与安全门禁收口

- CI #19（`9ff96e2`）在进入 identity/media 测试前编译失败：4 个 identity DO 已添加
  `@TenantIgnore` 但漏 import。`a7db023` 补齐 import，并用静态枚举确认所有已标注 DO 都有明确导入；
  本机空闲内存低于 6 GiB，未启动本地 Maven。
- CI #20（`dae75e0`）完成真实验证：契约与学生端通过；M01.01-B 全服务/真实 MySQL 已通过，证明
  `Unknown column tenant_id` 回归已消失。M02.02 的 4 个真实 MySQL 用例中并发绑定、旧 generation
  CAS、业务与 binding 回滚 3 项通过；迁移历史断言仍因 Flyway 展示文本格式失败，现改为按不可变
  migration script 名验证。M01.03 旧夹具为不同认证尝试复用同一 proofReference，被 V0011 唯一约束
  正确拒绝；夹具现为每次尝试生成独立 proof。以上修复须下一次远端数据库运行通过后才计验收。
- Luna 的 M02.03 独立验收提交 `cc1e94d` 已由 Sol 审查并以 `dae75e0` 集成：app/admin actor 来自
  服务端会话、operator 案件上下文固定、真实 `LocalPrivateStorageAdapter` 临时目录字节、缺失/长度变化
  fail-closed、安全下载响应头，以及 DTO 不暴露 objectKey。CI #20 实际运行这些新增测试均通过；后端
  唯一失败是旧策略拒绝用例误向 MEMBER 传 operator 专属 case/reason，已修为合法 MEMBER 输入，未放宽
  生产约束。
- Secret scan #20 原始日志共有 17 个 finding：16 个来自公开上游基线 `bcf6f57`，1 个来自验收报告
  布尔字段的通用规则误报；当前树中相应上游值已由 `249845c` 替换。`.gitleaksignore` 只登记这 17 个
  完整 fingerprint，不排除目录、不关闭规则、不缩小历史扫描；同文件的新值、新行或新提交仍会失败。
  历史 cloud/payment credential-shaped 样例不在本仓库控制的账户中，不能据“测试文件”推断仍有效，
  保留为上游公开历史风险且不回显值。
- 已新增独立 M02.03 MySQL A 级 gate：只接受 `campus_m0203_test*` / `campus_m0203_acceptance*` 隔离
  schema，验证 proof 唯一性、grant actor/case 约束、token hash 唯一与外键、审计无 token/objectKey/URL/
  content 列、allow audit 与 grant 同事务回滚，以及 grant expiry/generation/version 重读稳定。它与
  M02.02 串行使用同一 CI MySQL 服务，不新增本地数据库实例；下一轮远端结果出来前 M02.03 仍为进行中。
- `@TenantIgnore` 只说明 campus 表不使用上游 SaaS tenant 列，不能代替 HTTP 身份隔离。Sol 在
  `835ac60` 增加 `CampusPlatformPrincipalGuard`，所有认证申请/审核、媒体上传和私有下载入口都要求
  `LoginUser.userType` 与 app/admin 受众一致，且 `LoginUser.tenantId` 必须等于服务端配置的
  `campus.platform-tenant-id`；客户端 tenant header 不能改变该值。
- Terra 的真实 Spring FilterChain/HTTP 回归经一次打回后以 `4119b96` 集成，`60077c4` 又按校园错误
  契约收紧断言并接入 B 级 gate。测试给另一 tenant 的后台身份授予同样的 upstream RBAC 和完全匹配的
  campus school/action grant，再验证其无 header 的审核、签发和私有打开仍为 HTTP 403；另覆盖伪造
  tenant header、member/admin 受众互换、跨校/跨 action 拒绝与平台审核员真实本地字节成功路径。它复用
  M01.01-B 已准备的单个 MySQL/Valkey 环境，按顺序运行，不新增并发重型环境。远端结果出来前不得把该
  HTTP 门槛或 M02.03 标为 PASS。

## 2026-09-18 M02.02/.03 认证材料闭环收口

- CI #25（`8c69de6`，run `35271662162`）和最终 CI #26（`c9b35ff`，run `35272319236`）均完整
  通过：契约检查、学生端检查、secret scan、后端测试与完整打包、数据库验收五个 job 全部 success。
  数据库 job 在同一 MySQL 8.4/Valkey 环境串行通过 M00.04、M00.05、M01.01 A/B、M01.03 A、
  M02.02 A、M02.03 A，并通过验收报告敏感数据扫描与最终聚合门禁。
- M02.02 的 4 个真实 MySQL 用例已经通过：V0010 历史、同 asset 并发唯一绑定、旧 generation CAS、
  业务写入与 binding 同事务回滚。模块 A 记为 PASS；B 只记 PARTIAL，因为“认证申请使用真实 media
  adapter 提交后在业务插入点失败”的跨模块 MySQL 路径及恶意压缩 worker 恢复尚未独立实测。该限制
  不影响现有认证 proof 的本地读取闭环，也不能用模块单测冒充剩余 B 证据。
- M02.03 的 V0011 约束/回滚 gate 与真实 FilterChain B gate 已通过。服务端固定平台 tenant 和
  ADMIN/MEMBER audience；伪造 tenant header、等权他租户管理员、跨校/跨 action、缺案件理由、他人
  token 重放、撤权、过期、generation/version/binding 变化均失败关闭。合法平台审核员以具体案件原因
  签发短令牌并从 `LocalPrivateStorageAdapter` 代理读取真实字节，ALLOW 审计恰好覆盖签发与打开。
  当前只完成 `MEMBERSHIP_VERIFICATION_PROOF` 资源的 B 闭环，所以模块 B 记 PARTIAL；真实 COS/OSS
  与其他业务资源仍不计通过，C 保持 `BLOCKED_EXTERNAL`。
- 认证 evidenceRefs 在服务端按集合排序后参与幂等摘要；相同 key、相同集合（包括不同输入顺序）重放
  原申请且不再次占用。部分重叠拒绝。审核拒绝不释放不可变绑定，旧 proof 受 V0011 唯一约束保护；
  重提必须上传完整的新资产集并形成新 proof，学生端 REJECTED 状态已明确该要求。
- proof 的 validFrom/expiresAt 在首次绑定时固定，审核重查复用原 proof 和原窗口，不能自动续期。
  管理端材料读取的 `caseReference` 现必须等于该 proof 实际解析出的 applicationId；`reason` 是操作者
  本次填写的真实查看原因。任意案件号不能再通过授权后写成错误审计归因。
- Secret scan 继续使用 17 个完整历史 fingerprint 的精确例外，未排除目录、规则或扫描范围；#25/#26
  对新泄漏检测均通过。旧 A/B 分支仍只作审查来源，未直接合并、重置或标为已验收。

## 下一最小交付：校园后台固定租户会话

M11.01 当前真实登录被上游 tenant filter 阻塞。下一小包采用独立
`/admin-api/campus/v1/session/login|refresh|logout`：请求不接收 tenantId，服务端固定
`campus.platform-tenant-id`，token 必须为 ADMIN audience 与平台 tenant。login 只收
`username/password/captchaVerification?`；refresh 以 JSON 收 `refreshToken`；二者返回
`userId/accessToken/refreshToken/expiresAt`，logout 使用当前 Bearer 且无 body。仅对这三个路径做精确
filter 豁免，随后立即固定/校验平台租户；伪造 tenant header 不能改变认证上下文。Sol 持有后端、
公共契约与真实 HTTP 验收，管理端安全壳由 Sol2 按冻结契约接线。

## 2026-09-18 M11.01 会话首包实现

- `5752c7b` 在 identity 模块新增固定平台租户的 `/admin-api/campus/v1/session/login|refresh|logout`，
  复用上游后台认证与 OAuth2 存储，但每次操作都在服务端 tenant executor 中执行，并逐项校验
  `tenant_id=campus.platform-tenant-id`、`user_type=ADMIN`、默认 client 与相同 user/refresh 关联。
  客户端不提交或保存 tenantId；logout 只接受精确 access-token 行，即使上游把 refresh token 当作
  bearer fallback 也会被拒绝。响应 userId 为十进制字符串，expiresAt 为 RFC3339。
- identity 单测 6 项、OpenAPI/验收 Python 4 项通过；`yudao-server` test-compile 通过。真实
  `M0203TenantBoundaryIntegrationIT` 已扩展固定租户登录、伪造 header refresh、member/异租户 refresh
  拒绝及 refresh-as-bearer logout 拒绝/正常 logout 撤销路径，需下一次 CI 的隔离 MySQL/Valkey B 环境执行。
  本机未配置 `CAMPUS_M0101_B_*`，因此不把该 HTTP 证据标为 PASS。
- Sol2 的管理端会话与审核工作台提交 `f8e7b8b`、`0a20c0f`、`1a1eebb` 均保持能力开关关闭，待本包
  后端 query 接线和同一轮真实 HTTP 后再逐项启用；旧 A/B 分支仍不直接合并。
- `1d72eb4` 又把下一项 B 证据冻结到同一真实 FilterChain 测试：成员提交使用实际
  `MembershipVerificationEvidenceAdapter` 和本地私有媒体行，集合顺序反转以同一幂等 key 重放只得到
  一个 application/两条 binding；对 application insert 故障用真实 `MembershipVerificationRepository`
  spy 强制抛错，断言事务回滚此前 adapter 写入的 binding。该测试已编译通过，仍待远端 MySQL/Valkey
  运行；失败前不得把 M02.02 B 从 PARTIAL 提升为 PASS。
- `3f7ba18` 实现了三条后台只读接线：`GET /admin-api/campus/v1/context` 由当前 ADMIN 会话读取
  服务端授权版本、有效学校/校区 grant、上游 permission 与菜单；`GET /verifications` 先按状态和
  固定 `submittedAt ASC, applicationId ASC` 查询，再逐行以 `OperatorAuthorizationService` 做学校/校区
  范围判断并分页；详情先加载再授权，仅返回申请、状态/版本、RFC3339 submittedAt 与 `{assetId}`，不
  泄露 userId、proofReference、objectKey 或 URL。OpenAPI 已同步冻结，学生/管理端前端来自 Sol2 的
  `d7206d8`、`23e84a6`、`9b921d4`，其 14 项测试、vue-tsc、build 通过。
- 本包后端已通过 identity/server test-compile 与 OpenAPI 4 项静态验收；尚未在真实 B DB 上执行，
  因而管理端能力开关保持关闭，M11.01 不标 PASS。下一步是单一远端环境跑 session + context/queue/detail
  HTTP 回归，失败时先收口权限/分页证据再启用前端。
- `758c302` 在同一 B 级类中补充了真实 `context → queue → detail` HTTP 证据：平台 ADMIN 仅带匹配
  campus grant 时可见目标学校/校区和 APPROVED 队列；详情字段集合严格限制为冻结的申请/状态/版本/时间/
  evidence.assetId，不出现 userId、proofReference、objectKey 或 URL。该用例与 session、media rollback
  用例共用单个远端 schema/Valkey，仍需 CI 执行后才能改变 M11.01/M02.02 B 状态。
- `1a755c3` 将 login/refresh 的匿名放行写入最终 admin-api matcher；logout、context、queue、detail
  继续要求认证。这样不依赖带前缀 MVC 的 PermitAll 扫描，同时不扩大匿名范围。

## 2026-09-18 M01.04 学校范围授权 A 级断言收口

- 架构包 `ad22f2b` 冻结了本小包边界：只处理服务端授权证明的结构断言、批量预检和未知持久化值的
  fail-closed，不改 M02.05/V0012、迁移、业务 SQL 或管理端菜单。
- Terra 提交 `49fa5a7` 经 Sol 复核后以 `45ab901` 集成：未知 `scope_type/status` 映射
  `SCOPE_DENIED`，未知/损坏/重复/空 action 映射 `ACTION_DENIED`；guard 校验 grant/authorization
  版本、到期瞬间、scope 类型白名单及 CAMPUS 校区条件，并允许平台/学校 grant 携带服务端请求校区；
  批量编译器校验证明非空、action/请求 scope 一致及跨校拒绝。
- Sol 补充 `fd3227d`：错配 proof 与未知 proof scope 在业务 handler 前拒绝，handler 不被调用。
- 窄验收命令（显式 Temurin 17/Maven 3.9.16，无数据库）：
  `mvn -pl campus-module-identity -am -Dtest=OperatorAuthorizationServiceTest,SchoolScopeQueryGuardTest,ScopePolicyCompilerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  首次 Terra 代码复验 17 项通过；补充 proof 测试初次发现夹具 action 不匹配导致预期错误，修正后
  完整重跑 19 项通过。M01.04 A 记 `PASS`（纯内存/窄单测），B `NOT_RUN`（真实
  MySQL 列表、JOIN、导出、附件和撤权任务尚未跑），C `BLOCKED_EXTERNAL`（真实部署/跨环境 Token）。

## 下一小包：M02.04 内容检查状态与人工复核边界（草案）

本包只冻结媒体域的检查状态机与人工复核输入，不接入真实渠道、数据库表、M09 队列或业务发布命令。
`ContentInspectionResult` 是不含原文、字节、objectKey 或 URL 的不可变状态：每个结果固定
`subjectType/subjectId/contentVersion/contentHash/policyVersion/providerRequestId`，并以 `version`
做乐观并发控制。

- 自动回调只能提交同一 `contentVersion`，状态为 `PASS|REJECT|REVIEW_REQUIRED|ERROR`；旧版本回调
  返回 `CONTENT_VERSION_CHANGED`，状态版本不符返回 `INSPECTION_CONFLICT`。
- 人工决定只允许 `REVIEW_REQUIRED|ERROR`，输入为 `PASS|REJECT + operatorId + 1..500 字符 reason`；
  成功后来源改为 `MANUAL`、版本递增，并保留原内容版本/hash。已完成的自动 PASS/REJECT 不可被人工
  直接改写，未就绪状态返回 `INSPECTION_NOT_READY`。
- 未来 HTTP 读取只返回 inspectionId、subject 引用、contentVersion、状态、policyVersion、source、
  reasonCode、version、时间与脱敏审计字段；不返回内容快照或渠道凭据。人工审核权限由 M09/M01 的
  既有 operator 授权服务判定，M02 不新增第二套租户或角色体系。

当前交付仅为纯领域实现及 8 项状态机单测；M02.04 A/B/C 仍分别为 `NOT_RUN/NOT_RUN/BLOCKED_EXTERNAL`，
不得据此标记内容渠道或真实审核队列通过。审查期间未启动本地 MySQL；首次检查时当前 shell 的 `PATH`
未加载便携工具，但已核实并使用主工作区同级 `.tools` 的 Java 17.0.20.1 与 Maven 3.9.16。随后以进程级
`JAVA_HOME/MAVEN_HOME/PATH`、`MAVEN_OPTS=-Xmx768m` 执行窄测，不能将先前 target 报告误记为本次证据。

本轮只读契约审查已执行 `python contracts/tools/check_contracts.py`（通过）：CSV 中三个 M02.04 错误码与
Java `CampusErrorCodes` 的 code/HTTP 409/retry action 一致，且已纳入公共错误规格注册。当前 `app.yaml`
与 `admin.yaml` 尚未声明 M02.04 inspection HTTP 端点，符合本包“只冻结领域边界、不接入渠道/队列/HTTP”范围；
不能把现有媒体上传/下载控制器当作内容检查接口证据。具备 Java 17/Maven 的 CI 可先运行
`mvn -B -pl campus-module-media -am -Dtest=ContentInspectionResultTest -Dsurefire.failIfNoSpecifiedTests=false test`
已实际执行并通过（Maven 3.9.16 / Java 17.0.20.1；8 tests, 0 failures, 0 errors, 0 skipped），再按
`.github/workflows/ci.yml` 的 backend job 执行完整模块测试；两者均不能改变 M02.04 A/B/C 状态，除非补齐
真实渠道、审核队列与目标环境证据。

## 2026-09-18 M01.06 账户生命周期 fail-closed 小包

- 架构边界由 `1b14460` 冻结：只处理现有生命周期行映射与域状态形状，不改迁移、HTTP、M02.05/V0012
  或管理端。空行仍代表版本 0 的隐式 ACTIVE；任何未知状态、负数或不一致 `authVersion/version`、
  闭合时间形状错误均必须拒绝。
- Terra 实现 `1396303`：`AccountLifecycleService.toDomain` 将 malformed persisted state 统一映射为
  `ACCOUNT_ACTION_RESTRICTED`；`AccountLifecycle` 强制 auth/row 版本相等，并要求 CLOSED 的
  `closedAt >= closureRequestedAt`。异常发生在读/锁路径，不写入、不追加日志、不撤销会话。
- Sol 使用显式 Temurin 17/Maven 3.9.16 复验：
  `mvn -pl campus-module-identity -am -Dtest=AccountLifecycleServiceTest,AccountLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`
  通过，11 tests、0 failures/errors/skipped；未启动数据库。M01.06 本包 A=`PASS`，B=`NOT_RUN`
（真实 MySQL 生命周期读写/撤权/并发），C=`BLOCKED_EXTERNAL`（真实部署/跨环境会话）。

## 2026-09-18 M01.05 跑腿资格纯领域小包

- 架构边界由 `7d3948c` 冻结：只实现 `RunnerQualification` 的内存状态机和严格
  `user/school/campus` 接单断言，不接仓储、迁移、订单锁序、HTTP 或 M02.05/V0012。
- Terra 实现 `1a21767`：状态 `PENDING|ACTIVE|SUSPENDED|REVOKED`，批准/停用/恢复/撤销均递增
  version；有效期使用服务端传入 `Instant`，采用左闭右开窗口；跨用户、跨学校、跨校区、未批准、
  停用、撤销和过期均明确拒绝，成功只返回 qualificationId/version。
- Sol 补充停用/撤销/未来生效时间断言测试并复验：
  `mvn -pl campus-module-identity -am -Dtest=RunnerQualificationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  通过，5 tests、0 failures/errors/skipped；未启动数据库。M01.05 本包 A=`PASS`，B=`NOT_RUN`
  （真实资格 DB、锁序及 M05 并发），C=`BLOCKED_EXTERNAL`（真实部署/跨环境会话）。

## 2026-09-18 M01.03/M02.02/M02.03 真实 B 复验

- 交叉测试修复提交 `28b2d8e`（完整 SHA `28b2d8ee1a250f1754d6dc400e84f73f8bc88325`）只调整
  `M0203TenantBoundaryIntegrationIT` 的共享 schema 基线断言：按当前用户/目标资产记录基线，
  保留新材料子集重提的 `MEDIA_ALREADY_BOUND`、无新增 application/binding/asset/idempotency 行断言；
  未改生产代码、迁移或 M02.05/V0012。`git diff --check` 通过，Terra 无本地 Maven/DB，未宣称本地 B。
- integration 分支已推送至 `28b2d8e`，CI run
  `35324983794`（[run](https://github.com/liuxueqi0044/campus-assistant/actions/runs/35324983794)）实际完成：
  Contract、Secret、Student frontend、Backend compilation/tests 及 M00.04/M00.05/M01.01 A/M01.03 A/
  M02.02 A/M02.03 A 全部成功。计数基线修复后的同一真实 M01.01 B 套件仅剩
  `campusAdminSessionPinsPlatformTenantAndRejectsRefreshTokenAsBearer` 失败，断言 logout 响应
  `data` 不是 `true`；因此 gate 为 FAIL，M01.01 B 仍 `PARTIAL`，M01.03/M02.02/M02.03 B 不因 A
  选择器通过而升级。该失败保持为独立会话行为缺陷，未用测试夹具掩盖。
- 该 run 的 B 子集重提、真实媒体绑定回滚及同键异序重放未再出现在失败列表，但在完整套件未通过前，
  不能把 M02.02 B 或跨模块认证闭环标为 PASS。下一步仅处理 logout 会话契约/实现的最小根因并复跑
  同一套件；不重复 dispatch、不启动本地重型环境，M02.05/V0012 保持不动。

## 2026-09-18 logout FilterChain 审计结论

- 审计边界记录 `8f724d6`，测试侧脱敏诊断提交 `26df365`、`76fecb6`、`609b65f`；最终 CI
  `35327096247`（head `609b65f`）仍只失败于 `M0203TenantBoundaryIntegrationIT` 的 logout 场景。
  诊断断言未到达，因为 `assertSuccess(loggedOut)` 先在业务 `code == 0` 断言失败；HTTP 可能为 200，
  但响应由上游 `CommonResult.error(403, ...)` 提前返回，不能据此写成 data=false 或 JSON 类型问题。
- 源码核对确认 `TenantSecurityWebFilter` 在 controller 前比较认证 user tenant 与伪造的
  `tenant-id=OTHER`，不匹配即返回；`@TenantIgnore` 仅绕过缺失租户校验，不能绕过已登录 mismatch。
  因此 M01.01/M01.03 B 保持 `PARTIAL`。最小候选修复是只对固定校园 admin session/query 路径在该
  filter 前覆盖服务端 platform tenant，继续保留 platform principal/token guard；不得全局放宽租户检查。
- 结论与边界已在 `754deeb` 推送；未修改生产代码、迁移或 M02.05/V0012，未再启动本地重型环境。

## 2026-09-18 固定校园 tenant 前置覆盖小包

- 架构边界 `9ef319c` 冻结：只覆盖 `/admin-api/campus/v1/session/**`、精确 context、
  `/admin-api/campus/v1/verifications/**`；过滤顺序位于 TenantContext(-104) 与 TenantSecurity(-99)
  之间，finally 恢复原 tenant。公共 `CampusPathMatcher` 过宽的首版 `db297ac` 已审查拒绝，
  收窄提交为 `23fab46`，测试 matcher 修复为 `58184bf`。
- Terra 实现新增 `CampusFixedTenantWebFilter` 与 `CampusFixedTenantConfiguration`，不改全局
  `TenantSecurityWebFilter`、controller、service 或迁移。窄测命令（Temurin 17/Maven 3.9.16）实际通过：
  `CampusFixedTenantWebFilterTest` 4 tests，0 failures/errors/skipped；首次夹具 matcher 错误已由
  `58184bf` 修复后重跑通过。
- Sol 在 `11ea6da` 补真实 FilterChain 集成断言：platform ADMIN + OTHER header 的 context/queue/detail
  仍成功；异租户 ADMIN + OTHER header 仍拒绝；普通 system admin 路径 mismatch 仍拒绝；原 session
  login/refresh/logout 场景继续覆盖。该提交已推送，等待唯一一次远端 CI 的 MySQL/Valkey 回归，期间
  不把 A/B 状态提前升级。
- CI `35329731733` 暴露 matcher 初版仍覆盖 POST `/verifications/{id}/review`，使既有 headerless
  异租户 review 断言从 Campus 403 变成上游 HTTP 200/CommonResult 403。Terra 修正为方法敏感匹配：
  仅 GET context/verifications 与 POST session，提交 `0ba398a`；窄测复跑 4 tests、0 failures/errors/skipped。
  旧 CI 因该 overreach 保持 FAIL，尚未据此更新 B；下一次 CI 必须基于 `0ba398a` 验证 session、三条查询、
  review 保护和普通路径 mismatch。

## 2026-09-18 固定校园 tenant session 诊断与测试夹具修复

- 唯一诊断 CI `35331361662`（head `2e12579`）的 FilterChain 查询/审核路径已通过，M0203 仅剩 session 场景；脱敏 wire 结果为 `login` HTTP 400、code `1040000000`，顶层字段 `[code,message,data,traceId]`，`dataType=object`。
- 源码核对确认该码为 `CAMPUS_REQUEST_INVALID`：测试创建的用户名 `m0203-operator-<id>` 含连字符，违反生产 DTO `CampusAdminLoginRequest` 的固定正则 `^[a-zA-Z0-9]{4,30}$`。生产校验、filter matcher、租户边界均不改；仅将 M0203 fixture 改为 `m0203operator<id>`（长度 <=30）。
- 修复仅限 `backend/yudao-server/src/test/java/cn/iocoder/yudao/server/integration/M0203TenantBoundaryIntegrationIT.java`，需推送后只 dispatch 一次完整 CI。修复前 run `35331361662` 保持证据：A selectors PASS，M01.01 B PARTIAL；待完整 run 全绿前不得升级 B。
- 完整 CI `35332535144`（head `ad18a10`）确认修复方向但仍失败：login 已通过 DTO 校验，返回 HTTP 500；原因是仅修改 login 变量后，`createAdmin` 仍以旧含连字符用户名入库，测试夹具前后不一致。提交 `9333d8b` 将入库用户名同步为 `m0203operator<id>`，仍仅测试文件改动、生产校验不放宽。按本轮约束不重复 dispatch，待后续基于 `9333d8b` 的既有 CI 机会再验；M01.01 B 保持 `PARTIAL`。
- 复验 CI `35334146543`（head `b9435d7`，含 `9333d8b` 夹具对齐）中 login/refresh 均通过，logout wire 响应也通过；仅 logout 后 `system_oauth2_access_token` 清理断言失败（期望 0、实际 1），确认仍是既有 logout token-revocation 缺陷。其它 A 选择器与跨租户 FilterChain 路径通过；M01.01 B 继续 `PARTIAL`，不以修测试夹具代替生产修复，未再 dispatch。

## 2026-09-18 logout token 物理撤销修复包

- Terra 根因证据：上游 `OAuth2TokenServiceImpl.removeAccessToken` 对 OAuth 表执行逻辑删除（`deleted=1`），
  而 M01.01 真实契约/验收按 access_token、refresh_token 计数要求撤销后行不存在；因此 CI logout 成功后仍见 current token 1 行。
- `715d57c` 先将 token 查询、platform/user/client 校验与 upstream logout 放进同一 `PlatformTenantExecutor`
  临界区，finally 恢复 tenant/ignore；`391e93f` 在同一事务内对已验证的 access id + refresh token 执行带
  `tenant_id=platformTenantId` 的定向物理 DELETE。未知/异租户 token 在 SQL 前拒绝，不触碰其它用户或全表。
- 单测 `CampusAdminSessionServiceTest` 已覆盖 tenant restore、定向 access/refresh DELETE、unknown/other-tenant
  拒绝且不调用 upstream；本地 Temurin 17/Maven 3.9.16 窄测通过 5 tests、0 failures/errors/skipped。
- 生产改动仅 `CampusAdminSessionService.java`，对应单测；待 Sol 审查后再进行一次真实 MySQL/Valkey CI。M01.01/M01.03 B 先保持 `PARTIAL`。

## 2026-09-18 logout 物理撤销真实 B 通过

- CI run `35336130220`（head `a67f633`）全绿：Secret、Contract、Student frontend、Backend compilation/tests、Database acceptance 均 success。
- Database acceptance 的 M01.01 B 完整套件通过，包含固定 platform tenant + OTHER header 的 login/refresh/logout、refresh token 作为 Bearer 拒绝、未知/异租户 token 拒绝、access/refresh 定向物理撤销及其它 token 保留；gate 输出 `M01_01_B_OUTCOME: success`，证据门通过 7 个模块。
- 该 run 的 M01.03/M02.02/M02.03 仍是各自 A 选择器通过；未将其 B/C 误升级。CSV 仅把 M01.01 B 从 `PARTIAL` 更新为 `PASS`，C 仍 `BLOCKED_EXTERNAL`；M01.03、M02.02、M02.03 的 B 继续按原真实范围记录。

## 2026-09-18 M01.03 禁用成员真实 B 小包

- 架构边界 `26233d3`：只补既有 M01.01 B HTTP 测试中的禁用成员重登场景，不改生产/迁移/workflow；
  B 要求真实 MySQL/Valkey 下同 openId 重登返回 `AUTH_LOGIN_USER_DISABLED` 4xx，成员、微信绑定和 access token
  行数均不增加，C 仍为真实微信/真机外部阻塞。
- Terra 测试提交 `ff09008` 仅修改 `M0101MemberOAuth2IntegrationIT.java`，首次真实登录后将成员设为
  DISABLE，再次登录断言错误码与零新增，finally 恢复 ENABLE。`git diff --check` 通过。
- 本地 Temurin 17/Maven 3.9.16 编译与选择器执行通过：4 tests discovered, 4 skipped（缺少隔离 DB/Valkey），
  因此仅证明 A 编译，不宣称 B；等待下一次远端完整 CI。
- CI `35337005181` 真实 B 仅因测试期望上游 `AUTH_LOGIN_USER_DISABLED`（1004003001）而失败；实际校园 HTTP 契约稳定返回 `CampusErrorCodes.ACCOUNT_DISABLED`（1040001012/403）。
- Terra 修正测试断言提交 `5d1634c`，仅移除错误的 member upstream error import、改用校园错误码；成员、绑定、token 零新增行断言保留。待该 SHA 复验前，M01.03 B 仍 `PARTIAL`。
- 修正后 CI `35337566202`（head `1a31855`）全绿；M01.01 B full server 与 M0203 tenant suite 均 success，新增 `disabledMemberCannotLoginAgainOrCreateAnotherToken` 真实 MySQL/Valkey 场景通过：成员禁用后同 openId 登录返回 `ACCOUNT_DISABLED` 1040001012/403，member/binding/access-token 行数无新增，finally 恢复状态。
- 本包 B 证据通过，但 M01.03 聚合状态仍 `PARTIAL`：当前 workflow 的 `M01_03_OUTCOME` 是 A 选择器，尚无独立完整 M01.03 B gate；不把单个生命周期场景误标整模块 B PASS。C 继续 `BLOCKED_EXTERNAL`。

## 2026-09-18 M01.03 独立 B gate 收口

- 架构边界与文件所有权见 `docs/coordination/2026-09-18-m0103-b-independent-gate-architecture.md`，提交 `86ad540`；CSV 先将过时的“补禁用成员场景” next step 改为独立 gate。
- Terra 新增独立入口 `scripts/ci/m0103_b_acceptance.py`（`09a29ce`），Sol 接入专用 schema preparation、报告和最终 fail-closed workflow（`db1cc91`）。准备阶段映射全部 `CAMPUS_M0103_B_*` 到 Java 夹具固定读取的 `CAMPUS_M0101_B_*`，缺少固定基线时不运行业务 selector。
- 首次复验 run `35348650161` 的三个 selector 在空 schema 上失败，准确根因是缺 `M0101BDatabasePreparationIT` 固定基线导入；未改生产代码，B 继续 `PARTIAL`。修复后唯一复验 run `35351196760` 全绿：preparation 1/1、禁用成员重登 1/1、材料集合异序幂等 1/1、申请插入失败回滚 1/1；Backend、Contract、Student、Secret 及敏感数据扫描均 success。
- 因独立 B 报告 `artifacts/acceptance/m01.03-b.json` 的 `status=PASS`、三业务 selector 零失败/错误/跳过且最终 gate 通过，M01.03 B 现可标为 `PASS`；真实邮箱/校方渠道、微信 code2session、真机流程仍为 C `BLOCKED_EXTERNAL`。

## 2026-09-18 M02.02 独立 B gate 收口

- 架构边界见 `docs/coordination/2026-09-18-m0202-independent-b-gate-architecture.md`，提交 `89c40d8`；DeepSeek 执行包为 `b3f8816`，Sol 接线为 `63c63df`。
- 脚本先在专用 `campus_m0202_b_acceptance` schema 运行 `M0101BDatabasePreparationIT`，再按固定顺序运行两条 `M0203TenantBoundaryIntegrationIT` 跨模块事务场景和 `M0202MySqlIntegrationTest`；映射仅在子进程内覆盖 Java 夹具变量，拒绝共享 schema/危险 JDBC URL。
- 远端 CI `35355272444`（head `63c63df`）全绿：preparation 1/1、材料集合异序重放幂等 1/1、申请插入失败回滚 1/1、M0202 数据库迁移/并发唯一约束/旧 generation CAS/业务资源与 binding 同事务回滚 4/4；Backend、Contract、Student、Secret 与敏感数据扫描均 success。
- `artifacts/acceptance/m02.02-b.json` 报告 `status=PASS`、三业务 selector 无失败/错误/跳过且最终 evidence gate 通过，因此 M02.02 B 现标为 `PASS`；恶意压缩 worker、真实云对象和外部凭据仍为 C `BLOCKED_EXTERNAL`。
