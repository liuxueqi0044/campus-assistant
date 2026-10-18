// Run against the compiled H5 static server; each route uses a fresh browser page.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||(process.platform==='win32'?'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe':undefined)});
 const base=process.env.BASE_URL||'http://127.0.0.1:5175',errors=[],results=[];
 const scenarios=[
  {route:'/pages/index/index',title:'校园',run:async p=>{await p.locator('.goods-card').first().click();await click(p,'预约交接');await click(p,'我已完成交接');await p.getByText('你已确认交接，等待卖家确认。',{exact:true}).waitFor();}},
  {route:'/pages-errand/detail?id=parcel',title:'跑腿详情',run:async p=>{await click(p,'接下这个任务');await click(p,'确认已取件');await click(p,'标记已送达');await p.getByText('等待收件确认',{exact:true}).waitFor();}},
  {route:'/pages-errand/detail?id=my-parcel',title:'跑腿详情',run:async p=>{await click(p,'确认收到物品');await p.getByText('待结算 · 尚未到账',{exact:true}).waitFor();}},
  {route:'/pages-activity/detail?id=film',title:'活动详情',run:async p=>{await click(p,'加入候补名单');await p.locator('.panel-title').filter({hasText:'已加入候补'}).waitFor();await click(p,'退出候补');await click(p,'确认退出');}},
  {route:'/pages/timetable/index',title:'课表'},
  {route:'/pages/messages/index',title:'消息'},
  {route:'/pages/me/index',title:'我'},
  {route:'/pages-account/settings',title:'设置'},
  {route:'/pages-account/verify',title:'学生认证'},
 ];
 async function click(p,t){await p.locator('uni-button').filter({hasText:new RegExp('^\\s*'+t+'\\s*$')}).click();await p.waitForTimeout(250)}
 try{for(const s of scenarios){const p=await browser.newPage({viewport:{width:390,height:844}});p.on('pageerror',e=>errors.push(e.message));p.on('response',r=>{if(r.status()>=400)errors.push(`${r.status()} ${r.url()}`)});await p.goto(base+'/#'+s.route);await p.locator('.header-title').filter({hasText:new RegExp('^'+s.title+'$')}).waitFor();await p.waitForTimeout(350);if(s.run)await s.run(p);results.push(s.route);console.log('PASS',s.route);await p.close()}assert.deepEqual(errors,[]);fs.writeFileSync(path.resolve(__dirname,'../artifacts/build-smoke.json'),JSON.stringify({passed:true,base,results,errors},null,2))}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});

