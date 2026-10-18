const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('fs'),assert=require('assert'),path=require('path');
const artifacts=path.resolve(__dirname,'../artifacts'); fs.mkdirSync(artifacts,{recursive:true});
const baseURL=process.env.BASE_URL||'http://127.0.0.1:5173';
(async()=>{
const browser=await chromium.launch({headless:true,executablePath:process.env.EDGE_PATH||(process.platform==='win32'?'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe':undefined)});
const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1,colorScheme:'light',timezoneId:'Asia/Shanghai'});
const errors=[],steps=[];page.on('pageerror',e=>errors.push(e.message));
const btn=t=>page.locator('uni-button').filter({hasText:typeof t==='string'?new RegExp('^\\s*'+t+'\\s*$'):t});
const visible=t=>page.getByText(t,{exact:false}).filter({visible:true}).first().waitFor();
async function click(t){await btn(t).click();await page.waitForTimeout(330)}
async function nav(url){await page.evaluate(url=>uni.navigateTo({url}),url);await page.waitForTimeout(550)}
async function shot(name){await page.screenshot({path:path.join(artifacts,`${name}.png`)})}
try{
await page.goto(baseURL);await page.locator('.goods-card').first().waitFor();await page.waitForTimeout(700);await shot('home-390');
await click('数码配件');assert.equal(await page.locator('.goods-card').count(),2);await page.locator('.search-box input').fill('不存在');await visible('还没有找到');await page.locator('.search-box input').fill('');await click('全部');
await click('东校区');await click(/榆川大学 · 西校区/);assert.equal(await page.locator('.goods-card').count(),1);await visible('头戴式耳机');await click('西校区');await click(/榆川大学 · 东校区/);steps.push('campus switch, search and filters');
await page.locator('.goods-card').first().click();await visible('好物详情');await click('收藏');await visible('已收藏');await click('预约交接');await visible('我已完成交接');await click('我已完成交接');await visible('等待卖家确认');await shot('market-detail');
await click('联系同学');await page.locator('.composer textarea').fill('明天下午图书馆见，可以吗？');await click('发送');await visible('明天下午图书馆见，可以吗？');steps.push('favorite, reservation, pending seller and chat');
await nav('/pages-market/publish');await page.locator('input').nth(0).fill('前端测试：线性代数教材');await page.locator('textarea').fill('少量铅笔笔记，可当面检查。');await page.locator('input').nth(1).fill('18.5');await page.locator('input').nth(3).fill('图书馆南门');await click('提交发布');await visible('发布已提交');await click('查看我的发布');await visible('信息审核中');steps.push('market publish pending review');
await nav('/pages-errand/detail?id=parcel');await click('接下这个任务');await click('确认已取件');await click('标记已送达');await visible('等待收件确认');assert.equal(await btn('确认收到物品').count(),0);await shot('errand-detail');
await nav('/pages-errand/detail?id=my-parcel');await click('确认收到物品');await visible('待结算 · 尚未到账');steps.push('errand accept, delivery and payout separation');
await nav('/pages-activity/detail?id=film');await click(/加入候补/);await visible('已加入候补');await shot('activity-detail');await click('退出候补');await click('确认退出');await visible('已退出本次报名');steps.push('full activity waitlist and cancel');
await nav('/pages/timetable/index');await shot('timetable');await nav('/pages-timetable/course?id=c1');await click('编辑课程');await page.locator('.course-fields input').nth(1).fill('教学楼 A509');await click('保存课程');await nav('/pages-timetable/course?id=c1');await visible('教学楼 A509');steps.push('course edit saved');
await nav('/pages-timetable/import');await click('加载示例课表，体验核对');const missing=page.locator('.draft-card').filter({hasText:'地点缺失'});await missing.locator('uni-button').filter({hasText:'修改'}).click();await missing.locator('input').nth(1).fill('实验楼 B101');await page.locator('.confirm-check').click();await click(/确认追加/);if(await page.locator('.uni-modal').isVisible().catch(()=>false)){await page.getByText('保留并导入',{exact:true}).click()}await visible('课程已加入课表');steps.push('sample import review and append');
await nav('/pages/messages/index');await shot('messages');await nav('/pages/me/index');await shot('profile');await nav('/pages-account/settings');await click(/较大/);await visible('文字大小');assert(await page.locator('.large-type').count()>0);await click('退出账号');await click('确认');await visible('当前未登录');await click('进入演示账号');await click('进入演示账号');await visible('林同学');await nav('/pages-message/chat?id=chat-book');await visible('明天下午图书馆见，可以吗？');steps.push('font preference, logout and private data restoration');
await nav('/pages-errand/publish');await page.locator('input').nth(0).fill('演示取文件');await page.locator('input').nth(1).fill('东门打印店');await page.locator('input').nth(2).fill('教学楼大厅');await page.locator('input').nth(3).fill('今天18点前');await click('创建任务，查看费用');console.log('CREATED BUTTONS',await page.locator('uni-button').allTextContents());await click(/查看任务/);await click('模拟支付 · 不扣款');await visible('等待同学接单');await click('取消任务');await click('确认取消');await visible('退款处理中');steps.push('errand create, demo pay, cancel and pending refund');
assert.deepEqual(errors,[]);fs.writeFileSync(path.join(artifacts,'browser-flows.json'),JSON.stringify({passed:true,steps,errors},null,2));console.log(JSON.stringify({steps,errors},null,2));
}catch(e){await shot('failure');console.error('STEPS',steps,'BODY',await page.locator('body').innerText());throw e}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});

