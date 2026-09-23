/**
 * 渲染服务验收（规格 §9 的四项硬验收）。可对本机容器执行：node smoke.mjs [baseUrl]
 *
 *   1. 同输入两次渲染逐像素一致（sha256 相等）
 *   2. 中文字体可用（服务端缺字即失败；此处再核对探针宽度与「有字/无字」渲染确实不同）
 *   3. 750×N 长图（N ≥ 5000px）内存与耗时实测值
 *   4. 同一屏：单独渲染 == 整页中该屏（逐像素一致）
 *
 * 退出码非 0 表示验收未通过。
 */
const BASE = process.argv[2] || process.env.RENDERER_URL || 'http://127.0.0.1:8090';

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  | ' + detail : ''}`);
}

/** 一张极小的内联 PNG（2×2 纯色），避免冒烟测试依赖外部素材 */
const TINY_PNG =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAG0lEQVQIW2P8z8Dwn4EIwDiqED2qkAAxqAAAAN0ABf8R2wAAAABJRU5ErkJggg==';

async function render(payload) {
  const res = await fetch(`${BASE}/render`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`render 失败 HTTP ${res.status}: ${text.slice(0, 200)}`);
  }
  const buf = Buffer.from(await res.arrayBuffer());
  return {
    buf,
    sha: res.headers.get('x-render-sha256'),
    ms: Number(res.headers.get('x-render-ms') || 0),
    width: Number(res.headers.get('x-page-width') || 0),
    height: Number(res.headers.get('x-page-height') || 0),
    checksum: res.headers.get('x-template-checksum'),
    cjkWidth: Number(res.headers.get('x-font-cjk-width') || 0),
    rss: Number(res.headers.get('x-rss-bytes') || 0)
  };
}

function heroLayout(title) {
  return {
    dna: { background: '#f5f5f3', swatches: ['#2e6da4', '#f5f5f3'] },
    screen: {
      title,
      subtitle: '副标题：轻量款 · 静态花',
      body: '正文：主体版本 V2；参数 257.60*149.30；数量 1 盒。',
      chips: ['HERO-H02', '严格保真'],
      soloStatement: '一眼看清这是什么产品、什么配色、什么形态',
      image: TINY_PNG
    }
  };
}

function pageLayout(screenCount) {
  const screens = [];
  for (let i = 1; i <= screenCount; i++) {
    screens.push({
      screenNo: `S${String(i).padStart(2, '0')}`,
      screenType: i === 1 ? 'HERO' : (i % 3 === 0 ? 'DETAIL' : 'SELLING_POINT'),
      screenTypeDesc: i === 1 ? '主图' : '卖点',
      title: `第 ${i} 屏标题：积木花详情页第 ${i} 段`,
      subtitle: `第 ${i} 屏副标题`,
      body: '正文：主体版本 V2；参数 257.60*149.30；数量 1 盒；工艺 UV+喷漆。'.repeat(2),
      soloStatement: `第 ${i} 屏的画面独白：不靠文案也要说清这一段的信息`,
      image: TINY_PNG
    });
  }
  return { dna: { background: '#f5f5f3' }, screens, footerNote: '视觉工厂渲染服务验收样本' };
}

async function main() {
  // ---- 0. 健康与版本 ----
  const health = await (await fetch(`${BASE}/health`)).json();
  check('服务健康', health.status === 'ok', JSON.stringify(health));
  const version = await (await fetch(`${BASE}/version`)).json();
  check('模板已内置且带校验和',
    Array.isArray(version.templates) && version.templates.length >= 2
      && version.templates.every((t) => /^[0-9a-f]{64}$/.test(t.checksum || '')),
    version.templates.map((t) => `${t.templateCode}@${t.templateVersion}:${String(t.checksum).slice(0, 8)}`).join(', '));
  check('Chromium 版本可读', !!version.chromium && version.chromium !== 'unknown', String(version.chromium));

  // ---- 1. 两次渲染一致性 ----
  const payload = { templateCode: 'HERO-H02', templateVersion: '1.0.0', mode: 'screen', layout: heroLayout('积木花 · 主图') };
  const first = await render(payload);
  const second = await render(payload);
  check('同输入两次渲染逐像素一致', first.sha === second.sha,
    `sha=${String(first.sha).slice(0, 16)} bytes=${first.buf.length}`);

  // ---- 2. 中文字体 ----
  check('中文字体探针可用（宽度 > 40px）', first.cjkWidth > 40, `cjkWidth=${first.cjkWidth}`);
  const withText = await render({ ...payload, layout: heroLayout('中文标题测试') });
  const withoutText = await render({
    ...payload,
    layout: (() => {
      const l = heroLayout('');
      l.screen.title = '';
      l.screen.subtitle = '';
      l.screen.body = '';
      return l;
    })()
  });
  check('有字与无字渲染结果不同（确实画出了字）', withText.sha !== withoutText.sha,
    `with=${String(withText.sha).slice(0, 12)} without=${String(withoutText.sha).slice(0, 12)}`);

  // ---- 3. 长图（12 屏，≥5000px） ----
  const long = await render({ templateCode: 'longpage', templateVersion: '1.0.0', mode: 'page', layout: pageLayout(12) });
  check('长图高度 ≥ 5000px', long.height >= 5000, `height=${long.height} width=${long.width}`);
  check('长图为 750 宽', long.width === 750, `width=${long.width}`);
  check('长图耗时在合理范围（< 60s）', long.ms < 60000, `renderMs=${long.ms}`);
  check('长图内存已实测（RSS < 2GB）', long.rss > 0 && long.rss < 2 * 1024 * 1024 * 1024,
    `rss=${(long.rss / 1024 / 1024).toFixed(0)}MB renderMs=${long.ms} bytes=${long.buf.length}`);

  // ---- 4. 单屏 == 整页中该屏 ----
  // 两边都必须截「同一个屏元素」：单屏那次只给一屏数据，整页那次给全部数据后按选择器取该屏。
  // （曾经的错误比法：一边截整页含页脚、一边截单屏元素，高度差正好是页脚，白折腾一轮。）
  const pageAll = pageLayout(5);
  const soloLayout = { ...pageAll, screens: [pageAll.screens[1]] };
  const solo = await render({
    templateCode: 'longpage', templateVersion: '1.0.0', mode: 'element',
    selector: '[data-screen-no="S02"]', layout: soloLayout
  });
  const inPage = await render({
    templateCode: 'longpage', templateVersion: '1.0.0', mode: 'element',
    selector: '[data-screen-no="S02"]', layout: pageAll
  });
  check('单屏渲染 == 整页中该屏（逐像素一致）', solo.sha === inPage.sha,
    `solo=${String(solo.sha).slice(0, 16)} inPage=${String(inPage.sha).slice(0, 16)} ` +
    `soloBox=${solo.width}x${solo.height} inPageBox=${inPage.width}x${inPage.height}`);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n==== 渲染服务验收：PASS=${results.length - failed.length} FAIL=${failed.length} ====`);
  if (failed.length) {
    failed.forEach((f) => console.log(`  FAILED: ${f.name} | ${f.detail}`));
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('smoke 异常：', err);
  process.exit(2);
});
