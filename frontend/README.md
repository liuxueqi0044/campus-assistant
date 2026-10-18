# 校园助手 · 学生端前端

这是一套可运行的 uni-app / Vue 3 / TypeScript 学生端，默认以**明确标注的演示模式**启动。延续原手机交互原型的四栏导航、白灰背景和绿色主色，已经拆成页面、业务模块、共享组件、状态和接口适配层。

目前可用于产品评审、向校内伙伴演示和与后端模型联调。尚未连接真实用户、支付、文件识别、审核或聊天服务，不能当作已上线的校园交易服务。

## 启动

要求 Node.js 20.19+ 或 22 LTS，npm。已在 Windows / Node 22.23.2 验证。

```powershell
cd apps/student
npm ci
npm run dev
```

浏览器打开 http://localhost:5173 。电脑上页面最大宽度 520px，手机按屏幕宽度显示。

```powershell
npm run typecheck
npm test
npm run build:h5
npm run build:mp-weixin
```

- H5 产物：`apps/student/dist/build/h5`。需要通过 HTTP 静态服务打开，不支持直接双击 `index.html` 的 file 协议。
- 微信小程序产物：`apps/student/dist/build/mp-weixin`。在微信开发者工具导入；正式联调前填写 `src/manifest.json` 的 `mp-weixin.appid`，配置请求域名及隐私权限。
- 本次完成小程序编译；微信开发者工具、iOS/Android 真机及正式账号授权尚未验收。
- 示例数据在内存中，刷新恢复初始状态；字号、默认首页及通知偏好保存在本机。演示登出立即清私有数据，再次进入同一演示账号可恢复当前会话快照。

## 已实现的页面和交互

| 模块 | 内容 |
|---|---|
| 校园 | 校区切换、二手/跑腿/活动切换、关键词搜索、分类、空状态、请求失败重试 |
| 二手 | 插画商品列表、详情、收藏、预约/取消、买家交接确认、联系卖家、出售/求购发布与待审核状态 |
| 跑腿 | 公开任务、费用预览、创建、模拟支付、接单、取件/送达、收件确认、取消及退款/结算状态区分 |
| 活动 | 海报列表、详情、免费报名、满员候补、退出、模拟签到、发起及待审核状态 |
| 课表 | 今日/周视图、周次与日期切换、课程新增/修改/删除、冲突提醒、导入草稿纠错与追加 |
| 消息 | 搜索、未读筛选、已读、上下文关联会话、文本发送、服务通知 |
| 我的 | 个人资料、认证状态、发布/收藏/订单/活动记录、登录/退出、字号、默认首页、通知偏好、演示重置 |

二手预约不扣款；买家确认后仍等待卖家确认，当前没有卖家确认闭环。课表文件选择不会读取或识别文件，用户主动加载示例才能体验纠错；这不是 OCR 成果。真实图片上传、真实支付、现场签到凭证、审核后台、实时消息推送均待接。

## 工程结构

```text
apps/student/
  src/
    pages/                  校园、课表、消息、我的
    pages-{业务}/           详情、发布、账号等六组分包
    features/               三类信息流及课表表单/校验
    components/             图标、页头、导航、空态、演示提示
    services/api.ts         页面唯一业务接口入口，demo/real 分离
    services/transport.ts   请求、业务错误、令牌和幂等键
    state.ts                会话状态、演示数据与登出隔离
    types.ts                当前前端领域类型
    data/demo.ts            虚构学校和示例数据
    styles/theme.scss       颜色、字号、表单和布局
    static/                 本地 PNG 插画与 SVG 源图
  package-lock.json         固定依赖安装结果
docs/                       对接说明、视觉规范及验收清单
tests/                      浏览器回归脚本
artifacts/                  截图和实际验收报告
third-party/                开源许可记录
```

18 个页面，六个业务分包。当前没有接入管理后台或写入后端工程。前端共享状态使用 Vue reactive，规模增长后可在适配层之后逐步拆模块 store，无需页面重写。

本次验收：22项逻辑/传输测试、9组开发环境业务操作、32项响应式检查，以及正式H5产物9个路由检查通过。详见 `docs/本次验收结果.md` 和 `artifacts/`，浏览器测试运行方式见 `tests/README.md`。

## 发给后端模型

优先阅读 `docs/后端联调说明.md`、`apps/student/src/types.ts`、`apps/student/src/services/api.ts`。这份接口清单是前端候选契约，应与后端现有设计合并，不是已经通过联调的协议。

真实模式：复制 `.env.example` 为 `.env.local`，设置 `VITE_API_MODE=real` 和实际 `VITE_API_BASE_URL`，重启开发服务。真实模式启动没有演示身份和数据，请求失败也不会回退为模拟成功。

## 复用来源

构建版本和基本 SSR 入口参考 [unibest base](https://github.com/feige996/unibest/tree/base)，固定参考提交 `dc3af8ddb99b78fa806078c80434b664a4b1ce93`；做了精简，没有复制完整模板或引入其全部插件。跨端能力由 [uni-app](https://uniapp.dcloud.net.cn/quickstart-cli.html) 提供，图标和弹层使用 [Wot UI](https://wot-ui.cn/guide/quick-use.html)。具体版本见 package.json，许可见 `third-party/`。

插画为本项目原创代码绘制的示意图。为兼容小程序，运行时统一引用 PNG；SVG 保留为设计源文件。uni-app 官方文档说明小程序 SVG 有地址限制，详见 [image 组件文档](https://uniapp.dcloud.io/component/image.html)。
