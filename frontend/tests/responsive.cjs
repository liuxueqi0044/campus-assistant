/* Run against the student H5 dev server. No backend or local data is changed.
 * PLAYWRIGHT_MODULE may be an absolute path; EDGE_PATH overrides Edge discovery.
 * BASE_URL defaults to http://127.0.0.1:5173. */
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const edgeCandidates = [process.env.EDGE_PATH,
  path.join(process.env['ProgramFiles(x86)'] || 'C:/Program Files (x86)', 'Microsoft/Edge/Application/msedge.exe'),
  path.join(process.env.ProgramFiles || 'C:/Program Files', 'Microsoft/Edge/Application/msedge.exe'),
  path.join(process.env.LOCALAPPDATA || '', 'Microsoft/Edge/Application/msedge.exe')];
const executablePath = edgeCandidates.find(candidate => candidate && fs.existsSync(candidate));
const artifacts = path.resolve(__dirname, '../artifacts');
const baseURL = (process.env.BASE_URL || 'http://127.0.0.1:5173').replace(/\/$/, '');
fs.mkdirSync(artifacts, { recursive: true });
const report = { baseURL, widths: [320, 430], checks: [], failures: [] };
const button = (page, text) => page.locator('uni-button').filter({ hasText: text instanceof RegExp ? text : new RegExp(`^\\s*${text}\\s*$`) });
const click = async (page, text) => { await button(page, text).click(); await page.waitForTimeout(250); };
const scenarios = [
  { name: 'home', route: '/pages/index/index', text: ['校园', '发布', '东校区'] },
  { name: 'home-errands', route: '/pages/index/index', setup: p => click(p, '跑腿'), text: ['发布', '顺路帮取一个小快递'] },
  { name: 'home-activities', route: '/pages/index/index', setup: p => click(p, '活动'), text: ['发布', '下课一起打羽毛球'] },
  { name: 'timetable-today', route: '/pages/timetable/index', text: ['添加课程', '导入', '今日'] },
  { name: 'timetable-week', route: '/pages/timetable/index', setup: p => click(p, '周课表'), text: ['添加课程', '高等数学'] },
  { name: 'messages', route: '/pages/messages/index', text: ['消息', '订单进展、报名提醒和账号动态', '全部已读'] },
  { name: 'profile', route: '/pages/me/index', text: ['林同学', '我的发布', '我的收藏'] },
  { name: 'errand-publish', route: '/pages-errand/publish', text: ['发布跑腿', '创建任务，查看费用', '任务名称'] },
  { name: 'errand-detail', route: '/pages-errand/detail?id=parcel', text: ['顺路帮取一个小快递', '接下这个任务'] },
  { name: 'activity-publish', route: '/pages-activity/publish', text: ['发起活动', '提交活动审核'] },
  { name: 'activity-detail', route: '/pages-activity/detail?id=badminton', text: ['下课一起打羽毛球', '免费报名参加'] },
  { name: 'timetable-import', route: '/pages-timetable/import', text: ['选择课表截图', '选择课表文件', '加载示例课表，体验核对'] },
  { name: 'timetable-import-review', route: '/pages-timetable/import', setup: async p => {
    await click(p, '加载示例课表，体验核对');
    await p.locator('.draft-card').filter({ hasText: '地点缺失' }).locator('uni-button').filter({ hasText: '修改' }).click();
  }, text: ['确认追加 2 门课程', '地点缺失 · 请填写', '返回选择来源'] },
  { name: 'course-edit', route: '/pages-timetable/course?id=c1', setup: p => click(p, '编辑课程'), text: ['保存课程', '取消', '上课时间'] }
];
const largeScenarios = [
  { ...scenarios.find(s => s.name === 'home'), name: 'large-home', large: true },
  { ...scenarios.find(s => s.name === 'timetable-week'), name: 'large-timetable-week', large: true },
  { ...scenarios.find(s => s.name === 'activity-detail'), name: 'large-activity-detail', large: true },
  { name: 'large-settings', route: '/pages-account/settings', large: true, text: ['文字大小', '较大', '默认首页'] }
];

(async () => {
  const browser = await chromium.launch({ headless: true, ...(executablePath ? { executablePath } : {}) });
  try {
    for (const width of report.widths) {
      const context = await browser.newContext({ viewport: { width, height: 844 }, deviceScaleFactor: 1, colorScheme: 'light', timezoneId: 'Asia/Shanghai' });
      for (const scenario of [...scenarios, ...(width === 320 ? largeScenarios : [])]) {
        const page = await context.newPage();
        const result = { width, name: scenario.name, route: scenario.route, console: [], pageErrors: [], text: [], screenshot: `responsive-${width}-${scenario.name}.png` };
        page.on('console', message => { if (['error', 'warning'].includes(message.type())) result.console.push({ type: message.type(), text: message.text(), location: message.location() }); });
        page.setDefaultTimeout(6000);
        page.on('pageerror', error => result.pageErrors.push(error.message));
        try {
          await page.goto(`${baseURL}/#${scenario.route}`, { waitUntil: 'networkidle' });
          await page.locator('.page').first().waitFor();
          if (scenario.large) {
            await page.evaluate(() => uni.setStorageSync('campus-preferences-v1', { fontScale: 1.125, defaultTab: 'campus', notifications: true }));
            await page.reload({ waitUntil: 'networkidle' });
            await page.locator('.page.large-type').waitFor();
            result.font = await page.locator('.header-title').evaluate(title => {
              const root = title.closest('.page');
              const scaled = parseFloat(getComputedStyle(title).fontSize);
              const previous = root.style.getPropertyValue('--font-scale');
              root.style.setProperty('--font-scale', '1');
              const standard = parseFloat(getComputedStyle(title).fontSize);
              if (previous) root.style.setProperty('--font-scale', previous); else root.style.removeProperty('--font-scale');
              return { standard, scaled };
            });
            if (Math.abs(result.font.scaled / result.font.standard - 1.125) > 0.01) throw new Error(`Large text preference did not scale header: ${JSON.stringify(result.font)}`);
          }
          if (scenario.setup) await scenario.setup(page);
          await page.waitForTimeout(250);
          result.layout = await page.evaluate(() => ({
            viewport: innerWidth,
            documentWidth: document.documentElement.scrollWidth,
            bodyWidth: document.body.scrollWidth,
            pageWidths: [...document.querySelectorAll('.page')].filter(el => el.getBoundingClientRect().width).map(el => ({ width: el.getBoundingClientRect().width, scrollWidth: el.scrollWidth }))
          }));
          // An internally scrolling week grid is allowed; the document must not scroll sideways.
          if (Math.max(result.layout.documentWidth, result.layout.bodyWidth) > width + 1) throw new Error(`Page horizontal overflow: ${JSON.stringify(result.layout)}`);
          for (const text of scenario.text) {
            const element = page.getByText(text, { exact: true }).filter({ visible: true }).first();
            await element.waitFor({ timeout: 3000 });
            await element.scrollIntoViewIfNeeded();
            const box = await element.boundingBox();
            if (!box || box.x < -1 || box.x + box.width > width + 1 || box.height <= 0) throw new Error(`Important text clipped horizontally: ${text} ${JSON.stringify(box)}`);
            result.text.push({ text, visible: true });
          }
          await page.evaluate(() => { window.scrollTo(0, 0); document.querySelectorAll('uni-page-wrapper,uni-page-body').forEach(el => { el.scrollTop = 0; }); });
          await page.screenshot({ path: path.join(artifacts, result.screenshot), fullPage: true });
          if (result.pageErrors.length || result.console.some(m => m.type === 'error')) throw new Error('Browser console error or pageerror; see report');
          result.passed = true;
        } catch (error) {
          result.passed = false;
          result.error = error.message;
          report.failures.push(`${width}/${scenario.name}: ${error.message}`);
          await page.screenshot({ path: path.join(artifacts, result.screenshot), fullPage: true }).catch(() => {});
        } finally {
          report.checks.push(result);
          console.log(`${result.passed ? 'PASS' : 'FAIL'} ${width} ${scenario.name}${result.error ? `: ${result.error}` : ''}`);
          await page.close();
        }
      }
      await context.close();
    }
  } finally {
    await browser.close();
    report.passed = report.failures.length === 0;
    fs.writeFileSync(path.join(artifacts, 'responsive-report.json'), JSON.stringify(report, null, 2));
  }
  if (!report.passed) process.exitCode = 1;
})().catch(error => { console.error(error); process.exitCode = 1; });
