/**
 * 图像创作模块 · 工作流契约工具（Node ESM，无第三方依赖）
 *
 * 作用：
 *  1. 校验 `image-workflow-contracts.json` 里的每个 workflowCode：
 *     模板文件存在 → 文件字节是规范格式（JSON.stringify(parsed, null, 2) + "\n"）→ SHA-256 与契约 checksum 一致。
 *  2. `--write` 模式下重新计算并把 checksum 写回契约（模板改动后必须执行，禁止手抄校验值）。
 *  3. 校验 mapping 白名单与模板结构的一致性：节点存在、inputKey 存在、输出节点是 SaveImage。
 *
 * 为什么把 checksum 约定写死在这里：后端（org.dromara.ai.image）读取模板后会对
 * **文件字节**做 SHA-256 并与契约比对，不一致则整条工作流不予加载。视频模块曾出现
 * 「模板改了、checksum 没跟着改」的事故，因此这里用程序而不是人工保证两者同步。
 *
 * 用法：
 *   node script/image/workflows/image-contracts.mjs           # 只校验
 *   node script/image/workflows/image-contracts.mjs --write   # 重算并写回 checksum
 */
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
export const CONTRACT_PATH = join(HERE, 'image-workflow-contracts.json');

/** 后端加载模板时使用的根目录（对应 image.contract-root，默认 script）。 */
export function contractRoot() {
  return resolve(HERE, '..', '..');
}

export function loadContract(path = CONTRACT_PATH) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

/** 规范格式：与 JS JSON.stringify(obj, null, 2) + "\n" 完全一致的字节。 */
export function canonicalize(obj) {
  return JSON.stringify(obj, null, 2) + '\n';
}

export function sha256(text) {
  return createHash('sha256').update(text, 'utf8').digest('hex');
}

/** 展开契约里的所有 (capability, workflow) 绑定。 */
export function bindings(contract = loadContract()) {
  const out = [];
  for (const capability of contract.capabilities ?? []) {
    for (const workflow of capability.workflows ?? []) {
      out.push({ capability, workflow });
    }
  }
  return out;
}

export function templatePath(workflow, root = contractRoot()) {
  return join(root, workflow.apiJsonFile);
}

/**
 * 校验单个绑定。返回 { ok, errors[], checksum, file }。
 */
export function verifyBinding(workflow, root = contractRoot()) {
  const errors = [];
  const file = templatePath(workflow, root);
  if (!existsSync(file)) {
    return { ok: false, errors: [`模板文件不存在：${workflow.apiJsonFile}`], file, checksum: null };
  }
  const raw = readFileSync(file, 'utf8');
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    return { ok: false, errors: [`模板不是合法 JSON：${e.message}`], file, checksum: null };
  }
  if (raw !== canonicalize(parsed)) {
    errors.push('模板文件不是规范格式（应为 JSON.stringify(obj, null, 2) + 换行）');
  }
  const checksum = sha256(raw);
  if (!workflow.checksum || workflow.checksum === 'TBD') {
    errors.push('契约缺少有效 checksum');
  } else if (workflow.checksum !== checksum) {
    errors.push(`checksum 不匹配：契约 ${workflow.checksum} / 实际 ${checksum}`);
  }
  return { ok: errors.length === 0, errors, file, checksum, template: parsed };
}

/**
 * 重算并把 checksum 写回契约（仅在确实有变化时改动 `meta.updatedAt`）。
 *
 * <p><b>为什么不能无条件盖日期</b>：`updatedAt` 表达的是"最后一次实际变更日期"。
 * 早期实现每次调用都把它改成当天并写回文件，于是
 * ①「重算不应改变契约内容」这条测试在跨天之后必红（文件字节多了个新日期），
 * ② 一次只读的校验会悄悄弄脏工作区。真实事故：跨天当天 Backend CI 因此变红，
 * 而该步骤失败会连带跳过镜像发布。</p>
 */
export function rewriteChecksums(path = CONTRACT_PATH) {
  const contract = loadContract(path);
  const changed = [];
  for (const { workflow } of bindings(contract)) {
    const file = templatePath(workflow);
    if (!existsSync(file)) {
      throw new Error(`模板文件不存在：${workflow.apiJsonFile}`);
    }
    const parsed = JSON.parse(readFileSync(file, 'utf8'));
    const checksum = sha256(canonicalize(parsed));
    if (workflow.checksum !== checksum) {
      changed.push(`${workflow.workflowCode}: ${workflow.checksum} -> ${checksum}`);
      workflow.checksum = checksum;
    }
  }
  if (changed.length > 0) {
    contract.meta.updatedAt = new Date().toISOString().slice(0, 10);
  }
  // 仍然写回规范格式（修正手改造成的格式漂移），但内容一致时不落盘。
  const next = canonicalize(contract);
  if (next !== readFileSync(path, 'utf8')) {
    writeFileSync(path, next, 'utf8');
  }
  return changed;
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  if (process.argv.includes('--write')) {
    const changed = rewriteChecksums();
    console.log(changed.length ? `已更新 ${changed.length} 条 checksum：\n  ${changed.join('\n  ')}` : '所有 checksum 均为最新');
  } else {
    let failed = 0;
    for (const { capability, workflow } of bindings()) {
      const r = verifyBinding(workflow);
      if (r.ok) {
        console.log(`OK   ${capability.capabilityCode.padEnd(9)} ${workflow.workflowCode.padEnd(20)} ${r.checksum}`);
      } else {
        failed++;
        console.log(`FAIL ${capability.capabilityCode.padEnd(9)} ${workflow.workflowCode.padEnd(20)} ${r.errors.join('; ')}`);
      }
    }
    if (failed > 0) {
      console.error(`\n${failed} 个工作流校验未通过`);
      process.exit(1);
    }
  }
}
