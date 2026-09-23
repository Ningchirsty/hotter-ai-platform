/**
 * AI 视觉工厂 独立渲染服务。
 *
 * 设计要点（都是踩过坑才会写下来的约束）：
 *   1. **零网络**：素材一律以 data URI 内联进 layout，渲染期不请求任何外部资源——
 *      否则「同输入两次渲染一致」会被网络时序破坏。
 *   2. **可复现**：同容器、同浏览器构建、同字体、同视口 + 禁用动画/过渡 + 等字体与图片解码完成。
 *   3. **不猜**：缺图就明确画出「还没有产出」，而不是留白；模板缺失/校验和不符直接报错。
 *   4. **单屏与整页同一套 DOM**：整页里第 k 屏的 DOM 结构与单屏渲染完全一致，
 *      这样「单屏渲染 == 整页中该屏」才可能逐像素成立（规格 §9 第 4 项验收）。
 *
 * 接口：
 *   GET  /health   → { status: "ok" }
 *   GET  /version  → 版本、Playwright/Chromium 版本、字体清单
 *   GET  /templates→ 内置模板清单（编码/版本/校验和）
 *   POST /render   → body: { templateCode, templateVersion, mode: "screen"|"page"|"element",
 *                            selector?, layout }
 *                    返回 image/png，响应头带 X-Render-Ms / X-Page-Width / X-Page-Height /
 *                    X-Template-Checksum / X-Render-Sha256
 */
import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TEMPLATE_ROOT = path.join(__dirname, 'templates');
const PORT = Number(process.env.PORT || 8090);
const VIEWPORT_WIDTH = Number(process.env.VIEWPORT_WIDTH || 750);
const MAX_BODY_BYTES = Number(process.env.MAX_BODY_BYTES || 64 * 1024 * 1024);
const SERVICE_VERSION = '1.0.0';

/** 字体清单在启动时固化：渲染前若字体没就绪，宁可失败也不出「缺字图」 */
const CJK_FAMILY = process.env.CJK_FAMILY || 'Noto Sans CJK SC';

let browser = null;
let browserVersion = 'unknown';

/** 模板清单：编码/版本 → 文件路径与校验和 */
function listTemplates() {
  const out = [];
  if (!fs.existsSync(TEMPLATE_ROOT)) return out;
  for (const code of fs.readdirSync(TEMPLATE_ROOT)) {
    const codeDir = path.join(TEMPLATE_ROOT, code);
    if (!fs.statSync(codeDir).isDirectory()) continue;
    for (const version of fs.readdirSync(codeDir)) {
      const file = path.join(codeDir, version, 'template.html');
      if (!fs.existsSync(file)) continue;
      const content = fs.readFileSync(file);
      out.push({
        templateCode: code,
        templateVersion: version,
        checksum: crypto.createHash('sha256').update(content).digest('hex'),
        bytes: content.length
      });
    }
  }
  return out;
}

function templatePath(code, version) {
  const file = path.join(TEMPLATE_ROOT, String(code), String(version), 'template.html');
  // 防目录穿越：只允许模板根目录下的既有文件
  const resolved = path.resolve(file);
  if (!resolved.startsWith(path.resolve(TEMPLATE_ROOT))) return null;
  return fs.existsSync(resolved) ? resolved : null;
}

async function startBrowser() {
  browser = await chromium.launch({
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--font-render-hinting=none', '--force-color-profile=srgb']
  });
  browserVersion = browser.version();
}

async function renderOnce({ templateCode, templateVersion, mode, selector, layout }) {
  const file = templatePath(templateCode, templateVersion);
  if (!file) {
    const err = new Error(`模板不存在：${templateCode}@${templateVersion}`);
    err.statusCode = 404;
    throw err;
  }
  const html = fs.readFileSync(file, 'utf8').replace('__LAYOUT__', JSON.stringify(layout ?? {}));
  const checksum = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

  const context = await browser.newContext({
    viewport: { width: VIEWPORT_WIDTH, height: 1200 },
    deviceScaleFactor: 1,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    reducedMotion: 'reduce'
  });
  try {
    const page = await context.newPage();
    // 任何外部请求都视为配置错误（素材必须内联）：直接失败，避免悄悄渲染出「半张图」
    const external = [];
    await page.route('**/*', (route) => {
      const url = route.request().url();
      if (url.startsWith('data:') || url.startsWith('about:') || url.startsWith('file:')) {
        return route.continue();
      }
      external.push(url);
      return route.abort();
    });

    await page.setContent(html, { waitUntil: 'load' });
    await page.evaluate(async () => {
      // 等字体与所有图片解码完成，再截图——否则第一次渲染常常少一层字
      if (document.fonts && document.fonts.ready) await document.fonts.ready;
      const imgs = Array.from(document.images);
      await Promise.all(imgs.map((img) => (img.complete ? Promise.resolve() : new Promise((res) => {
        img.onload = res; img.onerror = res;
      }))));
      await Promise.all(imgs.map((img) => (img.decode ? img.decode().catch(() => {}) : Promise.resolve())));
    });
    await page.addStyleTag({ content: '*,*::before,*::after{animation:none !important;transition:none !important;}' });
    await page.waitForTimeout(60);

    // 高度整数化（必须在字体与图片就绪之后做）：
    // 文字换行会让屏高出现小数，整页里第 k 屏的 y 偏移随之变成小数；而单屏渲染时该屏偏移恒为 0。
    // 两者的抗锯齿栅格不同 → 逐像素比对必然不一致。把每屏高度向上取整后，
    // 整页中任意屏的偏移都是整数，与单屏渲染的栅格对齐。
    await page.evaluate(() => {
      Array.prototype.forEach.call(document.querySelectorAll('.screen'), (section) => {
        const rect = section.getBoundingClientRect();
        section.style.height = Math.ceil(rect.height) + 'px';
        section.style.overflow = 'hidden';
      });
    });

    const fontProbe = await page.evaluate((family) => {
      const probe = document.createElement('span');
      probe.style.font = `32px "${family}"`;
      probe.style.position = 'absolute';
      probe.style.visibility = 'hidden';
      probe.textContent = '中文渲染探针';
      document.body.appendChild(probe);
      const width = probe.getBoundingClientRect().width;
      probe.remove();
      return {
        family,
        available: document.fonts ? document.fonts.check(`32px "${family}"`, '中文') : false,
        cjkWidth: width
      };
    }, CJK_FAMILY);

    if (external.length) {
      const err = new Error(`渲染期出现外部请求（素材必须内联）：${external.slice(0, 3).join(', ')}`);
      err.statusCode = 400;
      throw err;
    }
    if (!fontProbe.available || fontProbe.cjkWidth < 40) {
      const err = new Error(`中文字体不可用（${CJK_FAMILY}）：available=${fontProbe.available} width=${fontProbe.cjkWidth}`);
      err.statusCode = 500;
      throw err;
    }

    const started = Date.now();
    let png;
    let shotElement = null;
    if (mode === 'element') {
      // 整页里按选择器取单个元素截图：用于验收「单屏渲染 == 整页中该屏」
      shotElement = await page.$(selector || '#page-root');
      if (!shotElement) throw new Error(`整页中找不到元素：${selector}`);
      png = await shotElement.screenshot({ type: 'png', animations: 'disabled', caret: 'hide' });
    } else if (mode === 'screen') {
      shotElement = await page.$('#screen-root');
      if (!shotElement) throw new Error('模板缺少 #screen-root（单屏模式要求该容器）');
      png = await shotElement.screenshot({ type: 'png' });
    } else {
      shotElement = await page.$('#page-root');
      if (!shotElement) throw new Error('模板缺少 #page-root（整页模式要求该容器）');
      png = await shotElement.screenshot({ type: 'png', fullPage: true, animations: 'disabled', caret: 'hide' });
    }
    const ms = Date.now() - started;

    // 尺寸以「实际截图的元素」为准（element 模式下报告整页高度会误导验收）
    const box = shotElement
      ? await shotElement.evaluate((el) => {
          const rect = el.getBoundingClientRect();
          return { width: Math.round(rect.width), height: Math.round(rect.height) };
        })
      : { width: 0, height: 0 };

    return {
      png,
      ms,
      width: box.width,
      height: box.height,
      checksum,
      fontProbe,
      rssBytes: process.memoryUsage().rss
    };
  } finally {
    await context.close();
  }
}

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(body) });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        const err = new Error(`请求体超过上限 ${(MAX_BODY_BYTES / 1024 / 1024).toFixed(0)}MB`);
        err.statusCode = 413;
        reject(err);
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  try {
    if (req.method === 'GET' && url.pathname === '/health') {
      return json(res, 200, { status: 'ok', browser: !!browser, version: SERVICE_VERSION });
    }
    if (req.method === 'GET' && url.pathname === '/version') {
      return json(res, 200, {
        version: SERVICE_VERSION,
        node: process.version,
        chromium: browserVersion,
        cjkFamily: CJK_FAMILY,
        viewportWidth: VIEWPORT_WIDTH,
        templates: listTemplates()
      });
    }
    if (req.method === 'GET' && url.pathname === '/templates') {
      return json(res, 200, { templates: listTemplates() });
    }
    if (req.method === 'POST' && url.pathname === '/render') {
      const raw = await readBody(req);
      let payload;
      try {
        payload = JSON.parse(raw);
      } catch {
        return json(res, 400, { error: '请求体不是合法 JSON' });
      }
      const mode = payload.mode === 'screen' ? 'screen' : (payload.mode === 'element' ? 'element' : 'page');
      const result = await renderOnce({
        templateCode: payload.templateCode,
        templateVersion: payload.templateVersion,
        mode,
        selector: payload.selector,
        layout: payload.layout
      });
      const sha = crypto.createHash('sha256').update(result.png).digest('hex');
      res.writeHead(200, {
        'content-type': 'image/png',
        'content-length': result.png.length,
        'x-render-ms': String(result.ms),
        'x-page-width': String(result.width),
        'x-page-height': String(result.height),
        'x-template-checksum': result.checksum,
        'x-render-sha256': sha,
        'x-font-cjk-width': String(Math.round(result.fontProbe.cjkWidth)),
        'x-rss-bytes': String(result.rssBytes)
      });
      return res.end(result.png);
    }
    return json(res, 404, { error: `未知路径 ${req.method} ${url.pathname}` });
  } catch (err) {
    const status = err.statusCode || 500;
    return json(res, status, { error: String(err.message || err) });
  }
});

startBrowser()
  .then(() => {
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`[renderer] listening on :${PORT} chromium=${browserVersion} font=${CJK_FAMILY}`);
    });
  })
  .catch((err) => {
    console.error('[renderer] 启动失败：', err);
    process.exit(1);
  });
