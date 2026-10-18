# 浏览器验收脚本

先在 `apps/student` 启动 `npm run dev`。

脚本依赖 Playwright（本次使用 1.62.1）和 Edge，既可以使用已经安装的模块，也可在独立测试环境安装 Playwright。`PLAYWRIGHT_MODULE` 可设为其模块绝对路径，未设置则使用 Node 标准模块解析。

```powershell
$env:PLAYWRIGHT_MODULE = '你的 playwright 模块目录'
$env:BASE_URL = 'http://127.0.0.1:5173'
# 若 Edge 不在默认位置，再设置 EDGE_PATH 为浏览器可执行文件路径
node tests/flows.cjs
node tests/responsive.cjs
# 编译后的H5目录另起静态服务器时：
$env:BASE_URL = 'http://127.0.0.1:5175'
node tests/build-smoke.cjs
```

- `flows.cjs`：同校区过滤、收藏预约、聊天、发布审核、跑腿履约/模拟支付/取消退款、活动候补、课表修改/导入和退出恢复。
- `responsive.cjs`：320/430px 页面布局、错误日志、图片加载、周课表内部滚动及大字号场景。
- `build-smoke.cjs`：正式H5产物的独立路由与预约/跑腿/候补操作；无须暴露开发环境的全局 uni API。`flows.cjs` 使用开发环境导航API，只用于开发服务器。
- 报告、截图输出到 `artifacts/`。测试使用独立浏览器会话和虚构演示数据。
- API和传输单元测试用 `cd apps/student; npm test`，无需浏览器。

浏览器脚本用于 H5 演示环境，不能证明微信真机、真实后端、支付渠道、OCR 或多人并发已通过验收。
