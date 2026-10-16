#!/usr/bin/env node
/**
 * `docs/aigov/24-…总体实施与评审说明.md`（以及它引用的各阶段文档）的**口径漂移检查**。
 *
 * 为什么需要它：这份交付的"证据"大半写在文档里——落点的类名、部署脚本清单、测试数。
 *   代码改名/删除、脚本漏进重放清单、测试数前移，都不会让任何测试变红，
 *   只会让评审人照着文档找到不存在的东西，或者拿着一个过期的数字去下结论。
 *   本脚本把三类可机械核对的claim 拿出来逐条验：
 *     ① 文档里提到的类，仓库里是否真的还有这个文件；
 *     ② 文档里提到的 SQL 脚本是否存在；含 ALTER 的迁移是否都在发布工作流的重放清单里；
 *     ③ 文档 §4 的测试数是否与"刚跑出来的报告"一致（报告不存在则跳过，并说明原因）；
 *     ④ 文档 §1 提交表里的每个提交号是否真的存在于当前分支（手抄哈希很容易抄错一位）；
 *        §1.1「已合并分支（PR squash）的历史提交」不要求祖先关系（squash 会故意抹掉它），
 *        但要求提交对象在本仓库里确实存在——"防抄错"这一层不能因为合并方式不同就丢掉。
 *
 * 用法：node script/ci/check-aigov-docs.mjs
 * 退出码：全部通过 0；有任何一条对不上 1。
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..');
const DOC24 = join(ROOT, 'docs', 'aigov', '24-Agent平台优化v1.0-总体实施与评审说明.md');
const WORKFLOW = join(ROOT, '.github', 'workflows', 'deploy-poc.yml');
const GOV_REPORTS = join(ROOT, 'ruoyi-modules', 'ruoyi-ai-gov', 'target', 'surefire-reports');
const VITEST_JSON = join(ROOT, 'frontend', 'vitest-results.json');

const failures = [];
const notes = [];

const doc = readFileSync(DOC24, 'utf8');

/**
 * 递归收集某个后缀的文件名（不含 .md：文档互相引用不算"代码里有"）。
 *
 * @param dir    目录
 * @param suffix 后缀（含点）
 * @returns 文件名集合
 */
function collectFiles(dir, suffix) {
  const found = new Set();
  const walk = (current) => {
    for (const entry of readdirSync(current)) {
      if (entry === 'node_modules' || entry === '.git' || entry === 'target') continue;
      const full = join(current, entry);
      if (statSync(full).isDirectory()) {
        walk(full);
      } else if (full.endsWith(suffix)) {
        found.add(entry);
      }
    }
  };
  walk(dir);
  return found;
}

/** ① 文档提到的类是否真的存在 */
const javaFiles = collectFiles(join(ROOT, 'ruoyi-modules', 'ruoyi-ai-gov', 'src'), '.java');
const frontendFiles = collectFiles(join(ROOT, 'frontend', 'src'), '.ts');
const vueFiles = collectFiles(join(ROOT, 'frontend', 'src'), '.vue');

// 只认"像类名"的 token：以 Aig 开头、长度足够，避免把 SQL/HTTP 这类缩写算进来
const CLASS_TOKEN = /\bAig[A-Za-z0-9]{4,}\b/g;
const IGNORE = new Set(['Aigov']); // 出现在命令/包名里的词，不是类
const mentioned = new Set([...doc.matchAll(CLASS_TOKEN)].map((hit) => hit[0]));

// 兜底搜索语料：枚举、内部类、常量都可能没有同名文件，所以"文件名命中"之外再全文找一次
const SOURCE_ROOTS = [
  join(ROOT, 'ruoyi-modules', 'ruoyi-ai-gov', 'src'),
  join(ROOT, 'frontend', 'src')
];
const corpus = [];
for (const sourceRoot of SOURCE_ROOTS) {
  const walk = (current) => {
    for (const entry of readdirSync(current)) {
      if (entry === 'node_modules' || entry === '.git' || entry === 'target') continue;
      const full = join(current, entry);
      if (statSync(full).isDirectory()) {
        walk(full);
      } else if (/\.(java|ts|vue)$/.test(full)) {
        corpus.push(readFileSync(full, 'utf8'));
      }
    }
  };
  walk(sourceRoot);
}
const corpusText = corpus.join('\n');

const missingNames = [];
for (const token of [...mentioned].sort()) {
  if (IGNORE.has(token)) continue;
  if (javaFiles.has(`${token}.java`)) continue;
  if (frontendFiles.has(`${token}.ts`) || vueFiles.has(`${token}.vue`)) continue;
  if (corpusText.includes(token)) continue;
  missingNames.push(token);
}
if (missingNames.length > 0) {
  failures.push(`文档提到但代码里找不到的名字：${missingNames.join('、')}`);
}
notes.push(`① 文档提到的 Aig* 名字：${mentioned.size} 个，全部能在源码里找到对应物`);

/** ② SQL 脚本：文档提到的存在；含 ALTER 的必须在发布工作流重放清单里 */
const sqlFiles = readdirSync(join(ROOT, 'script', 'sql')).filter((name) => name.endsWith('.sql'));
const sqlSet = new Set(sqlFiles);
const missingSql = [...doc.matchAll(/script\/sql\/([A-Za-z0-9_]+\.sql)/g)]
  .map((hit) => hit[1])
  .filter((name) => !sqlSet.has(name));
if (missingSql.length > 0) {
  failures.push(`文档提到但不存在的 SQL 脚本：${[...new Set(missingSql)].join('、')}`);
}

const workflow = readFileSync(WORKFLOW, 'utf8');
const replayed = new Set([...workflow.matchAll(/[A-Za-z0-9_]+\.sql/g)].map((hit) => hit[0]));
const alterNotReplayed = sqlFiles.filter((name) => {
  if (!name.startsWith('aig_')) return false;
  const text = readFileSync(join(ROOT, 'script', 'sql', name), 'utf8');
  return /ALTER\s+TABLE/i.test(text) && !replayed.has(name);
});
if (alterNotReplayed.length > 0) {
  failures.push(`含 ALTER 的迁移没进发布工作流的重放清单：${alterNotReplayed.join('、')}`);
}
notes.push(`② SQL：${sqlFiles.length} 个脚本；文档提到的全部存在；含 ALTER 的都已在重放清单里`);

/** ③ 数字口径：文档 §4 的数 vs 刚跑出来的报告 */
const expectedBackend = Number((doc.match(/\*\*(\d+) tests \/ 0 failures/) ?? [])[1] ?? -1);
if (!existsSync(GOV_REPORTS)) {
  notes.push('③ 后端测试数：SKIP（没有 surefire 报告——先跑 verify-aigov-v1.mjs）');
} else {
  let tests = 0;
  let failuresCount = 0;
  for (const name of readdirSync(GOV_REPORTS).filter((file) => file.startsWith('TEST-') && file.endsWith('.xml'))) {
    const xml = readFileSync(join(GOV_REPORTS, name), 'utf8');
    tests += Number((xml.match(/tests="(\d+)"/) ?? [])[1] ?? 0);
    failuresCount += Number((xml.match(/failures="(\d+)"/) ?? [])[1] ?? 0);
    failuresCount += Number((xml.match(/errors="(\d+)"/) ?? [])[1] ?? 0);
  }
  if (expectedBackend < 0) {
    failures.push('文档 §4 里没找到后端测试数（期望形如 `**<数字> tests / 0 failures**`）');
  } else if (tests !== expectedBackend) {
    failures.push(`文档 §4 写后端 ${expectedBackend} tests，磁盘上的报告是 ${tests} tests（改完代码要同步这个数）`);
  } else if (failuresCount !== 0) {
    failures.push(`后端报告里有 ${failuresCount} 个失败/错误（文档写 0 failures）`);
  } else {
    notes.push(`③ 后端测试数：文档 §4 与报告一致（${tests} tests / 0 failures / ${readdirSync(GOV_REPORTS).filter((f) => f.startsWith('TEST-')).length} 个报告文件）`);
  }
}

// 前端数：需要 vitest JSON（verify 脚本 --keep-reports 会留下它）
const expectedFrontend = (doc.match(/\*\*(\d+) files \/ (\d+) tests\*\*/) ?? []).slice(1).map(Number);
if (!existsSync(VITEST_JSON)) {
  notes.push('③ 前端测试数：SKIP（没有 frontend/vitest-results.json——用 verify-aigov-v1.mjs --keep-reports 跑一次）');
} else {
  const report = JSON.parse(readFileSync(VITEST_JSON, 'utf8'));
  const ranFiles = (report.testResults ?? []).length;
  const ranTests = report.numTotalTests ?? 0;
  if (expectedFrontend.length < 2 || Number.isNaN(expectedFrontend[0])) {
    failures.push('文档 §4 里没找到前端测试数（期望形如 `**<文件数> files / <用例数> tests**`）');
  } else if (ranFiles !== expectedFrontend[0] || ranTests !== expectedFrontend[1]) {
    failures.push(`文档 §4 写前端 ${expectedFrontend[0]} files / ${expectedFrontend[1]} tests，`
      + `报告是 ${ranFiles} files / ${ranTests} tests`);
  } else {
    notes.push(`③ 前端测试数：文档 §4 与报告一致（${ranFiles} files / ${ranTests} tests）`);
  }
}

/** ④ 提交表里的提交号是否真的在分支上（手抄哈希很容易抄错一位） */
const section1 = doc.slice(doc.indexOf('## 1 提交序列'), doc.indexOf('## 2 D01–D12 对照'));
// §1 正文 = "本分支的提交"，要求是 HEAD 的祖先；
// §1.1 = "已合并分支（PR squash 后不再有祖先关系）的历史提交"，只要求对象存在（防抄错）。
// 之所以分两段：squash 合并会**故意**抹掉祖先关系，此时"必须能在本分支上找到"这条规则
// 若硬套到历史表上，唯一的出路是把真实历史从文档里删掉——那是用丢证据换绿灯。
const HISTORY_HEADING = '### 1.1';
const historyAt = section1.indexOf(HISTORY_HEADING);
const branchSection = historyAt < 0 ? section1 : section1.slice(0, historyAt);
const historySection = historyAt < 0 ? '' : section1.slice(historyAt);
if (historyAt >= 0 && !/已合并/.test(section1.slice(historyAt, section1.indexOf('\n', historyAt)))) {
  failures.push('§1.1 只能放"已合并分支的历史提交"，标题里必须写明「已合并」（否则等于给祖先检查开后门）');
}
const hashesOf = (text) => new Set([...text.matchAll(/`([0-9a-f]{7,40})`/g)].map((hit) => hit[1]));

const unknownHashes = [];
for (const hash of [...hashesOf(branchSection)].sort()) {
  // 不只看"对象存在"：还要是 HEAD 的祖先——rebase 掉的分支上仍可能有那些对象，
  // 而评审人要的是"这个提交号能在当前分支上找到"
  const probe = spawnSync('git', ['merge-base', '--is-ancestor', hash, 'HEAD'],
    { cwd: ROOT, stdio: 'ignore' });
  if (probe.status !== 0) {
    unknownHashes.push(hash);
  }
}
if (unknownHashes.length > 0) {
  failures.push(`§1 提交表里有不是当前分支祖先的提交号（抄错、或已被 rebase 掉）：${unknownHashes.join('、')}`);
} else {
  notes.push(`④ 提交表：§1 的 ${hashesOf(branchSection).size} 个提交号都是当前分支 HEAD 的祖先`);
}

// §1.1：只对"对象是否存在"负责（squash 合并后祖先关系必然不成立，见上面的说明）
const missingObjects = [];
for (const hash of [...hashesOf(historySection)].sort()) {
  const probe = spawnSync('git', ['cat-file', '-e', `${hash}^{commit}`], { cwd: ROOT, stdio: 'ignore' });
  if (probe.status !== 0) {
    missingObjects.push(hash);
  }
}
if (missingObjects.length > 0) {
  failures.push(`§1.1 历史表里的提交号在本仓库里不存在（抄错一位就会这样）：${missingObjects.join('、')}`);
} else if (historyAt >= 0) {
  notes.push(`④ 提交表：§1.1 的 ${hashesOf(historySection).size} 个历史提交号对象都存在（squash 合并，不要求祖先关系）`);
}

/** 结论 */
console.log('\n文档口径检查结果\n');
for (const line of notes) console.log(`  ✓ ${line}`);
if (failures.length > 0) {
  console.log('');
  for (const line of failures) console.log(`  ✗ ${line}`);
  console.log(`\n❌ ${failures.length} 处口径对不上（文档漂移，不会让任何测试变红）。`);
  process.exit(1);
}
console.log('\n✅ 文档口径与代码/报告一致。');
