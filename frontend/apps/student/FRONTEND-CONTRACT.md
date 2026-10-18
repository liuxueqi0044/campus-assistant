# 前端内部协作契约（待后端对接）

工程根 apps/student，框架 uni-app Vue3 + TS + Wot UI。当前使用明确标记的 demo adapter；真实模式不得降级成模拟成功。

共享导出（根代理负责实现）：

- `@/state` 导出响应式 `state`：campuses:Campus[]、campusId:string、service:Service、user:User、products:Product[]、errands:Errand[]、activities:Activity[]、courses:Course[]、conversations:Conversation[]、notices:Notice[]、favorites:string[]、week:number（默认3）、fontScale:number（1/1.125）、termStart:string（2026-08-31）、defaultTab:Tab、notifications:boolean、demo:boolean、networkState:'ready'|'loading'|'error'。
- `@/services/api` 导出 `api` 全部 async：refresh():void；switchCampus(id):void；login():void；logout():void；verify({name,studentId}):void；toggleFavorite(productId):void；reserveProduct(id):void；cancelReservation(id):void；confirmProduct(id):void；publishProduct(Partial<Product>):Product；contactSeller(productId):string会话ID；acceptErrand(id):void；advanceErrand(id):void；cancelErrand(id):void；createErrand(Partial<Errand>):Errand；simulatePayment(id):void（仅demo）；joinActivity(id):void；cancelActivity(id):void；checkinActivity(id):void；publishActivity(Partial<Activity>):Activity；saveCourse(Partial<Course>):void；deleteCourse(id):void；commitImport(Course[]):void；sendMessage(conversationId,text):void；readConversation(id):void；readNotices():void；resetDemo():void。action 成功修改state，失败抛 Error，页面catch显示inline错误；不要页面直接改变业务状态。
- `@/utils` 导出 `money(fen):string`、`go(url):void`（uni.navigateTo）、`back():void`、`toast(text):void`、`requireLogin():boolean`（false 时转个人登录页）。除用户设置、week、service外不直接改state。
- 共享组件 `PageHeader` props title:string, subtitle?:string, back?:boolean；slot default/right。`AppIcon` props name:string,size?:number,color?:string 使用 Wot 图标；`EmptyState` props title,description?,icon?；`BottomNav` props active:Tab；`DemoNotice` 无props；`SectionTitle` props title,subtitle?。
- 共享样式：page（max-width:520px,padding-bottom导航安全区）,content（16px左右）,surface（白色）,section（块间距）,row（flex）,between,muted,caption,title,heading,chip,tag,btn,btn-primary,btn-secondary,btn-danger,field,label,form-input,form-textarea,form-error,divider,action-bar,detail-page。所有样式变量以--color-primary/bg/surface/text/muted/line命名，root 提供16px字体/低圆角/白灰绿色主题。
- 使用 uni-app 标签 view/text/button/image/input/textarea/scroll-view；不用 DOM API、svg 标签、window/localStorage。Vue imports 明确引入。Wot组件用 wd-* easycom，图标走AppIcon。
- 页面数据初始化 onLoad 接收id，用computed从state找记录；H5刷新详情可能先为空，显示EmptyState与返回。主tab路径 pages/index/index、pages/timetable/index、pages/messages/index、pages/me/index。底部自定义BottomNav使用uni.reLaunch切页；原生页面返回用back兜底校园。
- 分包：pages-market/{detail,publish}，pages-errand/{detail,publish}，pages-activity/{detail,publish}，pages-timetable/{course,import}，pages-message/{chat,notices}，pages-account/{login,verify,settings,records}。不要修改pages.json，根统一注册。
- Root首页调用 ErrandFeed/ActivityFeed props query:string, category:string；列表只显示当前campusId；组件自己详情导航。默认菜单filter字串由根提供'全部'或分类名，不匹配需要EmptyState。
- 本项目演示学校：榆川大学（虚构）东校区/西校区。清晰显示“演示数据”。不能暗示真实已到账/OCR成功；截图文件导入提供可纠错示例＋说明，真实OCR未接入时不伪造识别。
- 可用静态资源 /static/products/{book,lamp,keyboard,headphones,racket,camera}.svg；/static/activities/{badminton,film,walk,boardgame}.svg（根绘制）。字体用系统字体。

所有页面需真实可交互、取消路径、异常/空数据状态、键盘和320px布局；不写空按钮／统一敬请期待toast代替功能。超出本次实现的外部能力用明确禁用说明。
