/**
 * 针对 <script setup> 的轻量静态检查：重复声明。
 *
 * 背景：CI 报过 `Identifier 'previewTask' has already been declared`——我在同一作用域里
 * 同时写了 `const previewTask = ref(...)` 和 `async function previewTask(...)`。
 * 这类错误本地没有 node_modules（无 tsc/eslint）时很难发现，但用正则收集声明名即可拦住。
 *
 * 用法：node script/ci/check-script-setup.mjs <file.vue> [...]
 *
 * ── 2026-10-08 加固：原实现会**空跑也算通过** ──
 * 它只遍历 `process.argv.slice(2)`：一旦调用方给不出文件（例如 `$(git ls-files '*.vue')`
 * 因为不在 git 仓库/glob 写错而展开成空），循环体一次都不执行，`failed` 保持 false，
 * `process.exit(0)` —— **什么都没检查，却是绿的**。这与本仓其它"静默不执行"是同一类失效。
 * 现在有三条硬约束：
 *   ① 一个文件参数都没有 → 失败；
 *   ② 没有任何含 `<script setup>` 的文件被真正检查 → 失败；
 *   ③ **独立地**扫描扫描根（默认 `frontend/src`，可用 `HOTTER_VUE_SCAN_ROOT` 覆盖）
 *      下的所有 `.vue`，要求它们**全部**出现在传入列表里 —— 防"只传了一部分也照样绿"。
 * 第 ③ 条刻意不依赖调用方的命令，这样换掉 CI 里的 glob 也不会把覆盖悄悄变小。
 */
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const DECL = [
  // 顶层 const/let/var 声明（含解构的第一层名字）
  { re: /^const\s+([A-Za-z_$][\w$]*)\s*=/gm, kind: 'const' },
  { re: /^let\s+([A-Za-z_$][\w$]*)\s*=/gm, kind: 'let' },
  { re: /^var\s+([A-Za-z_$][\w$]*)\s*=/gm, kind: 'var' },
  // 函数与类声明（含 async）
  { re: /^(?:async\s+)?function\s+([A-Za-z_$][\w$]*)/gm, kind: 'function' },
  { re: /^class\s+([A-Za-z_$][\w$]*)/gm, kind: 'class' },
  // 解构声明：const { a, b } = ... / const [a, b] = ...
  { re: /^const\s*\{([^}]*)\}\s*=/gm, kind: 'const-destructure' },
  { re: /^const\s*\[([^\]]*)\]\s*=/gm, kind: 'const-destructure' }
];

// 这些是 Vue 组合式 API，重复声明会直接报错，必须检查
const IGNORE = new Set([]);

const passed = process.argv.slice(2);
const scanRoot = process.env.HOTTER_VUE_SCAN_ROOT ?? 'frontend/src';

const toPosix = (p) => p.split(sep).join('/');

/** 独立扫描：扫描根下所有 .vue（不依赖调用方传了什么）。 */
function collectVue(dir, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules') collectVue(full, found);
    } else if (entry.name.endsWith('.vue')) {
      found.push(toPosix(relative('.', full)));
    }
  }
  return found;
}

let failed = false;
let inspected = 0;

for (const file of passed) {
  const src = readFileSync(file, 'utf8');
  const m = src.match(/<script setup[^>]*>([\s\S]*?)<\/script>/);
  if (!m) {
    console.log(`  skip  ${file}（无 <script setup>）`);
    continue;
  }
  inspected++;
  const body = m[1];
  const seen = new Map();

  const add = (name, kind, index) => {
    const n = name.trim();
    if (!n || IGNORE.has(n)) return;
    if (!seen.has(n)) seen.set(n, []);
    seen.get(n).push({ kind, line: body.slice(0, index).split('\n').length });
  };

  for (const { re, kind } of DECL) {
    re.lastIndex = 0;
    let hit;
    while ((hit = re.exec(body)) !== null) {
      if (kind === 'const-destructure') {
        for (const part of hit[1].split(',')) {
          // 处理 a: b / a = 1 形态，取绑定名
          const name = part.split(':').pop().split('=')[0];
          add(name, kind, hit.index);
        }
      } else {
        add(hit[1], kind, hit.index);
      }
    }
  }

  const dupes = [...seen.entries()].filter(([, v]) => v.length > 1);
  if (dupes.length === 0) {
    console.log(`  OK    ${file}`);
  } else {
    failed = true;
    console.log(`  FAIL  ${file}`);
    for (const [name, hits] of dupes) {
      console.log(`         重复声明 '${name}': ` +
        hits.map(h => `${h.kind}@L${h.line}`).join(', '));
    }
  }
}

// ── 覆盖度断言（见文件头 ① ② ③）──
const problems = [];
if (passed.length === 0) {
  problems.push("没有收到任何 .vue 文件参数（调用方的文件列表是空的）——"
    + "这会让下面的循环一次都不执行，从而'检查通过'其实是'什么都没检查'");
}
if (inspected === 0) {
  problems.push('没有任何含 <script setup> 的文件被检查到');
}

let discovered = [];
try {
  discovered = collectVue(scanRoot);
} catch (err) {
  problems.push(`无法扫描 ${scanRoot}：${err.message}（用 HOTTER_VUE_SCAN_ROOT 指定正确的扫描根）`);
}
const passedSet = new Set(passed.map(toPosix));
const missing = discovered.filter((f) => !passedSet.has(f));
if (missing.length > 0) {
  problems.push(`${scanRoot} 下有 ${missing.length} 个 .vue 文件没有被传入本检查：`
    + missing.slice(0, 10).join(', ') + (missing.length > 10 ? ` … 等 ${missing.length} 个` : ''));
}

console.log(`  覆盖度：传入 ${passed.length} 个文件，其中含 <script setup> 的 ${inspected} 个；`
  + `${scanRoot} 下共发现 ${discovered.length} 个 .vue`);

if (problems.length > 0) {
  console.error('\n检查覆盖度不足（这类失败通常是调用方式错了，而不是代码有问题）：');
  for (const p of problems) console.error(`  - ${p}`);
}

process.exit(failed || problems.length > 0 ? 1 : 0);
