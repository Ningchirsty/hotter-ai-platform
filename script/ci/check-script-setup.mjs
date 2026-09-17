/**
 * 针对 <script setup> 的轻量静态检查：重复声明。
 *
 * 背景：CI 报过 `Identifier 'previewTask' has already been declared`——我在同一作用域里
 * 同时写了 `const previewTask = ref(...)` 和 `async function previewTask(...)`。
 * 这类错误本地没有 node_modules（无 tsc/eslint）时很难发现，但用正则收集声明名即可拦住。
 *
 * 用法：node script/ci/check-script-setup.mjs <file.vue> [...]
 */
import { readFileSync } from 'node:fs';

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

let failed = false;

for (const file of process.argv.slice(2)) {
  const src = readFileSync(file, 'utf8');
  const m = src.match(/<script setup[^>]*>([\s\S]*?)<\/script>/);
  if (!m) {
    console.log(`  skip  ${file}（无 <script setup>）`);
    continue;
  }
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

process.exit(failed ? 1 : 0);
