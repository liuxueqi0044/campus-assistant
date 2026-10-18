# 开源复用与许可

本工程复用 uni-app、Vue 3、Vue Router、Wot Design Uni；构建版本与入口参考 unibest base 精简实现。没有照搬完整上游模板。

| 来源 | 版本 / 参考 | 许可与用途 |
|---|---|---|
| unibest | base, dc3af8ddb99b78fa806078c80434b664a4b1ce93 | MIT；版本基线与工程入口参考。原许可见 unibest-LICENSE.txt |
| Vue | 3.4.21 | MIT；响应式与页面组件。原许可见 vue-LICENSE.txt |
| Vue Router | 4.5.1 | MIT；uni-app H5依赖。原许可见 vue-router-LICENSE.txt |
| Wot Design Uni | 1.14.0 | MIT；图标和底部弹层。原许可见 wot-design-uni-LICENSE.txt |
| @dcloudio/uni-* | 3.0.0-4070620250821001 | Apache-2.0（npm包标识）；跨端编译与组件，实际完整依赖见 lockfile |

TS/Vite/Vitest/Sass/Prettier 为开发和构建工具，不重写其功能。所有依赖及传递依赖的精确版本记录在 apps/student/package-lock.json；第三方资产应遵守各自许可。本目录不宣称覆盖所有传递依赖许可证。

本项目 static 中的校园示意插画为原创 SVG 代码绘制，并栅格化为 PNG 用于跨端显示。未使用真实学生、真实学校标识或真实商品照片。
