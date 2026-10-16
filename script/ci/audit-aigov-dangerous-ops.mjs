#!/usr/bin/env node
/**
 * 【评审用·非门禁】治理台「危险操作是否带二次确认」的可复现审计。
 *
 * 走读 U10 记了"5 处危险操作无二次确认"，但没有留下可复核的清单（哪 5 处、按什么口径数）。
 * 这个脚本把口径写死，让任何人能重跑出同样的数：
 *
 *   ① **危险命名**：变量名命中 handleDelete / handleRemove / remove 前缀 / delete 前缀 /
 *      archive 前缀 / cancel 前缀 / revoke 前缀 / expire 前缀 / disable 前缀 / rollback 前缀 /
 *      unbind 前缀 / clear 前缀；
 *   ② **真会写**：函数体内出现写接口调用（add/create/save/update/submit/delete/remove/
 *      archive/rollback/cancel/expire/disable/enable/revoke/publish/release/decide/probe/apply/index 前缀），
 *      且把 `modal.confirm('…')` 自身剔除后再判（避免"自己确认自己"）；
 *   ③ **已确认**：函数体内出现 `modal.confirm` / `.confirm(` / `modal.prompt`（弹窗表单、
 *      prompt 收原因同样算"人明确确认过"）。
 *
 * ⚠️ **为什么不接成 CI 门禁**：判据里含"命名意图"和"是否可恢复"这类人工判断，
 *    而"弹窗表单/prompt"也算确认——硬做成门禁会不断误报，最后只会被加豁免加废。
 *    所以它是**给你复核数字用的工具**：跑一遍、贴输出、对着清单看有没有漏。
 *
 * 用法：node script/ci/audit-aigov-dangerous-ops.mjs
 */

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..');
const TARGET = join(ROOT, 'frontend', 'src', 'views', 'aigov');

/**
 * 危险命名（按"这个函数名一看就知道在删/停/撤"的口径）。
 */
const DANGER_NAME =
  /^(handleDelete|handleRemove|remove\w*|delete\w*|doDelete|doRemove|archive\w*|cancel\w*|revoke\w*|expire\w*|disable\w*|rollback\w*|unbind\w*|clear\w*)$/;

/**
 * 写接口调用（用于排除"只是算了个值"的函数）。
 */
const WRITE_CALL =
  /\b(add|create|save|update|submit|delete|remove|archive|rollback|cancel|expire|disable|enable|revoke|publish|release|decide|probe|apply|index)\w*\s*\(/;

/**
 * 确认动作（弹窗表单/prompt 也算"人明确确认过"）。
 */
const CONFIRM = /modal\.confirm|\.confirm\(|modal\.prompt/;

/**
 * 「确认由弹窗承担」的命名（弹窗里要填原因/目标状态才能提交 ⇒ 不需要再叠一层 confirm）。
 */
const DIALOG_SUBMIT =
  /^(submit\w*|doSubmit|doCreate\w*|doAdd\w*|doHandle|doRenew|doIndex\w*|handleSave|handlePublish|handleDisable|handleApply|handleProposalApplied|save\w*|chooseDirty)$/;

/**
 * 明显不是"危险动作"的辅助函数（读数据/取值/打开弹窗）。
 */
const HELPER = /^(get\w*|load\w*|open\w*|reset\w*|capabilityLabel|toggle\w*|before\w*Change)$/;

/**
 * 递归收集 .vue。
 *
 * @param dir 目录
 * @returns 文件路径列表（POSIX 分隔符）
 */
function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...walk(full));
    } else if (full.endsWith('.vue')) {
      out.push(full.replaceAll('\\', '/'));
    }
  }
  return out;
}

/**
 * 粗略截取一个 `const fn = (…) => { … }` 的函数体（截到下一个行首的 `};`）。
 *
 * @param text       文件全文
 * @param startIndex 函数声明起点
 * @returns 函数体文本（最多 3000 字符）
 */
function bodyOf(text, startIndex) {
  const rest = text.slice(startIndex);
  const end = rest.search(/\n\};|\n\}\n/);
  return end < 0 ? rest.slice(0, 3000) : rest.slice(0, end);
}

const dangerRows = [];
const writeRows = [];

for (const file of walk(TARGET)) {
  const text = readFileSync(file, 'utf8');
  const decl = /const\s+(\w+)\s*=\s*(?:async\s*)?\(/g;
  let match;
  while ((match = decl.exec(text))) {
    const name = match[1];
    const body = bodyOf(text, match.index);
    const withoutConfirm = body.replace(/modal\.(confirm|prompt)\([^)]*\)/g, '');
    const writes = WRITE_CALL.test(withoutConfirm);
    const confirmed = CONFIRM.test(body);
    if (writes) {
      writeRows.push({ file, name, confirmed });
    }
    if (DANGER_NAME.test(name) && writes) {
      dangerRows.push({ file, name, confirmed });
    }
  }
}

const danglingDanger = dangerRows.filter((row) => !row.confirmed);
const danglingWrite = writeRows
  .filter((row) => !row.confirmed)
  .filter((row) => !DIALOG_SUBMIT.test(row.name) && !HELPER.test(row.name));

console.log('\n治理台危险操作确认覆盖（口径见文件头注释）\n');
console.log(`  ① 危险命名且会写库：${dangerRows.length} 个，缺确认：${danglingDanger.length} 个  ← 这一项应为 0`);
for (const row of danglingDanger) {
  console.log(`      ✗ ${row.file} → ${row.name}`);
}
console.log(`  ② 会写库但函数内没有确认、且不属于"弹窗表单提交/辅助函数"：${danglingWrite.length} 个  ← 需要人判断`);
for (const row of danglingWrite) {
  console.log(`      · ${row.file} → ${row.name}`);
}
console.log(`  ③ （参考）会写库的函数总数 ${writeRows.length} 个；其中"确认由弹窗承担"或属辅助函数的已排除。`);
console.log('\n判据提醒：弹窗表单（在弹窗里填原因/目标状态才能提交）与 modal.prompt 都算已确认；');
console.log('真正的缺口是"一次点击就改状态、没有中间那一步"的动作。');
console.log('本脚本是评审工具，**不接 CI 门禁**：判据含人工成分，硬做门禁只会被加废。\n');
process.exit(danglingDanger.length > 0 ? 1 : 0);
