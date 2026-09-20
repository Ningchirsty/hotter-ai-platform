// 内容生产协同 阶段1A 端到端冒烟。
//
// 前置：应用已启动（见 SPEC §9），且已执行
//       script/sql/cp_content.sql、cp_content_menu.sql、cp_content_aigov.sql。
// 用法：node script/smoke/smoke-content.mjs [xlsx路径]
//       不传路径时默认使用同目录下的 fixtures/content-conflict.xlsx。
// 用例主线：建产品→建任务→上传含「同字段两个不同值」的 Excel→解析→预检
//          →断言冲突卡带两处来源→确认→闸门流转→开工包签发。
//
// 夹具说明：fixtures/content-conflict.xlsx 是一份最小可用的 OOXML 表格，内含
//   产品名称/SKU/主体版本×2/颜色/数量/参数；主体版本故意给 V1 与 V2 两个值以构造冲突，
//   并故意不含包装版本以构造缺料。
import { readFileSync, existsSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = 'http://127.0.0.1:8080';
const CID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
// 默认取脚本同级的 fixtures/content-conflict.xlsx，便于在仓库内直接复跑
const DEFAULT_XLSX = join(dirname(fileURLToPath(import.meta.url)), 'fixtures', 'content-conflict.xlsx');
const XLSX = process.argv[2] || DEFAULT_XLSX;
if (!existsSync(XLSX)) {
  console.error(`夹具不存在：${XLSX}`);
  process.exit(2);
}

let fail = 0;
const ok = (m) => console.log(`  OK    ${m}`);
const bad = (m) => { console.log(`  FAIL  ${m}`); fail++; };
const info = (m) => console.log(`        ${m}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login() {
  const r = await (await fetch(`${BASE}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ clientId: CID, grantType: 'password', username: 'admin', password: 'admin123' })
  })).json();
  if (!r?.data?.access_token) throw new Error('登录失败: ' + JSON.stringify(r).slice(0, 200));
  return { Authorization: `Bearer ${r.data.access_token}`, clientid: CID, 'Content-Type': 'application/json' };
}

const J = async (res) => { try { return await res.json(); } catch { return {}; } };
const api = async (auth, method, url, body) => J(await fetch(BASE + url, {
  method, headers: auth, body: body === undefined ? undefined : JSON.stringify(body)
}));

const stamp = Date.now().toString(36);

// 轮询任务详情，直到谓词成立
async function waitTask(auth, taskId, predicate, label, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() < deadline) {
    await sleep(1200);
    const r = await api(auth, 'GET', `/content/task/${taskId}`);
    last = r?.data;
    if (last && predicate(last)) return last;
  }
  return last;
}

async function main() {
  const auth = await login();
  ok('管理员登录成功');

  // ---------- 1. 产品 ----------
  const p = await api(auth, 'POST', '/content/product', {
    productCode: `JH-${stamp}`, productName: '积木花-玫瑰', skuCode: 'JH-001',
    skuName: '玫瑰红', category: '积木花', version: 'V1', status: '0', remark: '冒烟用例'
  });
  if (p.code !== 200 || !p.data) { bad(`建产品失败：${p.msg}`); return; }
  const productId = p.data;
  ok(`建产品成功 productId=${productId}`);

  // ---------- 2. 任务 ----------
  const t = await api(auth, 'POST', '/content/task', {
    taskName: '积木花-玫瑰 详情图（冒烟）', deliverableType: 'ECOM_DETAIL',
    productId, skuCode: 'JH-001', dataLevel: 'INTERNAL', ownerId: 1, ownerName: 'admin',
    remark: '冒烟用例'
  });
  if (t.code !== 200 || !t.data) { bad(`建任务失败：${t.msg}`); return; }
  const taskId = t.data;
  ok(`建任务成功 taskId=${taskId}`);

  const detail0 = await api(auth, 'GET', `/content/task/${taskId}`);
  info(`初始状态=${detail0?.data?.task?.status}，闸门未满足强制项=${detail0?.data?.gate?.blockUnsatisfied?.length}`);
  if (detail0?.data?.task?.status === 'DRAFT') ok('详情接口可用，初始状态 DRAFT');
  else bad(`初始状态异常：${detail0?.data?.task?.status}`);

  // ---------- 3. 上传含冲突的 Excel ----------
  const bytes = readFileSync(XLSX);
  const fd = new FormData();
  fd.append('file', new Blob([bytes]), basename(XLSX));
  const up = await J(await fetch(`${BASE}/content/task/${taskId}/file`, {
    method: 'POST', headers: { Authorization: auth.Authorization, clientid: CID }, body: fd
  }));
  if (up.code !== 200 || !up.data) { bad(`上传附件失败：${up.msg}`); return; }
  ok(`上传附件成功 fileId=${up.data}`);

  // ---------- 4. 解析 ----------
  await sleep(1200);
  const parseJob = await api(auth, 'POST', `/content/task/${taskId}/parse`);
  info(`触发解析 jobId=${parseJob.data}`);
  const afterParse = await waitTask(auth, taskId, (d) => d.task?.status && d.task.status !== 'PARSING' && d.task.parseDoneAt, '解析完成');
  const files = afterParse?.files ?? [];
  info(`附件解析状态: ${files.map((f) => `${f.fileName}=${f.parseStatus}`).join(', ')}`);
  if (files.some((f) => f.parseStatus === 'DONE')) ok('Excel 解析完成');
  else bad(`解析未完成（parseMessage=${files[0]?.parseMessage}）`);

  const facts = afterParse?.facts ?? [];
  const pending = facts.filter((f) => f.confirmStatus === 'PENDING');
  info(`候选字段 ${facts.length} 条：${facts.map((f) => `${f.fieldCode}=${f.fieldValue}[${f.confirmStatus}]`).join(', ')}`);
  // 红线断言：解析结果必须全部待确认
  if (pending.length === facts.length && facts.length > 0) ok('解析结果全部为「待确认」，无 AI 直接写入事实');
  else bad('存在非待确认的解析结果，违反红线');

  const mv = facts.filter((f) => f.fieldCode === 'main_version');
  if (mv.length >= 2) ok(`主体版本识别到 ${mv.length} 个候选（构造的冲突已入库）`);
  else bad(`主体版本候选数异常：${mv.length}`);

  // ---------- 5. 预检 ----------
  await sleep(1200);
  const pcJob = await api(auth, 'POST', `/content/task/${taskId}/precheck`);
  info(`触发预检 jobId=${pcJob.data}`);
  await sleep(3000);
  const afterPc = await api(auth, 'GET', `/content/task/${taskId}`);
  const cards = afterPc?.data?.cards ?? [];
  info(`互动卡 ${cards.length} 张：${cards.map((c) => `${c.cardType}/${c.fieldCode}/${c.gateLevel}`).join(', ')}`);

  const conflict = cards.find((c) => c.cardType === 'CONFLICT' && c.fieldCode === 'main_version');
  if (conflict) {
    const ev = JSON.parse(conflict.evidenceJson || '[]');
    info(`冲突卡证据 ${ev.length} 条：${ev.map((e) => `${e.value}@${e.sourceFileName ?? '-'} ${e.locator ?? ''}`).join(' | ')}`);
    if (ev.length >= 2 && ev.every((e) => e.value)) ok('冲突卡带多处来源与摘录（设计文档 §7.2 的「来源」要求）');
    else bad('冲突卡证据不完整');
    if (conflict.blocking === 'Y') ok('冲突卡标记为阻断（该字段是 BLOCK 级强制项）');
    else bad(`冲突卡未标记阻断：blocking=${conflict.blocking}`);
  } else {
    bad('未生成 main_version 的冲突卡');
  }

  const missing = cards.find((c) => c.cardType === 'MISSING' && c.fieldCode === 'package_version');
  if (missing) ok('缺失卡生成（包装版本未在资料中出现）');
  else bad('未生成 package_version 的缺失卡');

  info(`预检后任务状态=${afterPc?.data?.task?.status}，阻断原因=${afterPc?.data?.task?.blockReason ?? '-'}`);
  if (afterPc?.data?.task?.status === 'PENDING_CONFIRM') ok('存在未确认强制项，任务被闸门拦在待确认');
  else bad(`任务状态应为 PENDING_CONFIRM，实际 ${afterPc?.data?.task?.status}`);

  // ---------- 6. 处理冲突卡：确认 V1 ----------
  await sleep(1200);
  const v1 = JSON.parse(conflict.optionsJson || '[]').find((o) => o.option === 'CONFIRM' && o.value === 'V1');
  const r1 = await api(auth, 'POST', '/content/card/resolve', {
    cardId: conflict.cardId, option: 'CONFIRM', value: 'V1', snapshotId: v1?.snapshotId, comment: '冒烟：采用 V1'
  });
  if (r1.code === 200) ok('冲突卡已裁定（确认 V1）');
  else bad(`冲突卡裁定失败：${r1.msg}`);

  const afterR1 = await api(auth, 'GET', `/content/task/${taskId}`);
  info(`裁定后状态=${afterR1?.data?.task?.status}`);
  if (afterR1?.data?.task?.status === 'PENDING_CONFIRM') ok('仍有强制项未确认，任务保持在待确认（闸门按规则判定）');
  else bad(`状态异常：${afterR1?.data?.task?.status}`);

  // ---------- 7. 处理缺失卡：手工录入包装版本 ----------
  await sleep(1200);
  const r2 = await api(auth, 'POST', '/content/card/resolve', {
    cardId: missing.cardId, option: 'OTHER', value: 'V1.2', comment: '冒烟：包装版本按 V1.2'
  });
  if (r2.code === 200) ok('缺失卡已裁定（手工录入 V1.2）');
  else bad(`缺失卡裁定失败：${r2.msg}`);

  // ---------- 8. 一键确认无争议项 ----------
  await sleep(1200);
  const cu = await api(auth, 'POST', `/content/fact/confirmUnambiguous?taskId=${taskId}`);
  info(`一键确认无争议项：${cu.code === 200 ? cu.data : cu.msg} 条`);
  if (cu.code === 200) ok('无争议项批量确认成功（设计文档 §3.1：人只处理例外与确认）');
  else bad(`批量确认失败：${cu.msg}`);

  const finalDetail = await api(auth, 'GET', `/content/task/${taskId}`);
  info(`最终状态=${finalDetail?.data?.task?.status}，阻断原因=${finalDetail?.data?.task?.blockReason ?? '-'}`);
  // 全部 BLOCK 已确认即为「可开工」。本例未提供参考图，而参考图是 CONDITION 级
  //（设计文档 §8.2：参考图默认不是产品部门强制项），故正确结果是 CONDITIONAL_READY。
  const st = finalDetail?.data?.task?.status;
  if (st === 'READY' || st === 'CONDITIONAL_READY') {
    ok(`全部强制项已确认，闸门判定为「${st === 'READY' ? '可开工' : '条件开工'}」`);
  } else {
    bad(`期望 READY/CONDITIONAL_READY，实际 ${st}（阻断：${finalDetail?.data?.task?.blockReason}）`);
  }
  // 回归断言：状态已可开工时，阻断原因必须被清空。
  // MyBatis-Plus 默认 NOT_NULL 策略下实体传 null 不会进 UPDATE，曾导致此处残留旧阻断文本。
  if (!finalDetail?.data?.task?.blockReason) {
    ok('阻断原因已随状态转好被清空（NOT_NULL 清空缺陷的回归断言）');
  } else {
    bad(`状态已为 ${st} 但仍残留阻断原因：${finalDetail.data.task.blockReason}`);
  }

  // ---------- 9. 开工包 ----------
  await sleep(1200);
  const g = await api(auth, 'POST', `/content/workPackage/generate?taskId=${taskId}`);
  if (g.code !== 200 || !g.data) { bad(`生成开工包失败：${g.msg}`); return; }
  const packageId = g.data;
  ok(`生成开工包成功 packageId=${packageId}`);

  const wp = await api(auth, 'GET', `/content/workPackage/byTask/${taskId}`);
  const content = JSON.parse(wp?.data?.contentJson || '{}');
  info(`开工包：快照版本=${content.snapshotVersion}，已确认事实=${(content.confirmedFacts || []).length} 项，缺口=${(content.gaps || []).length} 项`);
  const imm = content.immutableItems || [];
  info(`不可修改项：${imm.join('、')}`);
  if (imm.some((x) => x.includes('主体')) && imm.some((x) => x.includes('Logo')) && imm.some((x) => x.includes('包装'))) {
    ok('开工包明确列出不可修改项（产品主体/Logo/包装文字）');
  } else {
    bad('开工包缺少不可修改项');
  }

  // 反向断言：开工包只含已确认事实，不含任何待确认候选
  const confirmedFacts = content.confirmedFacts || [];
  const stillPending = (finalDetail?.data?.facts ?? []).filter((f) => f.confirmStatus === 'PENDING');
  const leaked = confirmedFacts.filter((f) => stillPending.some((p) => p.fieldCode === f.fieldCode && p.fieldValue === f.value));
  if (leaked.length === 0) ok('开工包不含任何「待确认」候选值（红线：候选不得成为产品事实）');
  else bad(`开工包泄露了未确认候选：${JSON.stringify(leaked)}`);
  if (content.confirmedFacts?.some((f) => f.fieldCode === 'main_version' && f.value === 'V1')) ok('开工包冻结的是人工裁定后的值（V1）');
  else bad('开工包未包含裁定后的主体版本');

  await sleep(1200);
  const iss = await api(auth, 'POST', `/content/workPackage/${packageId}/issue`);
  if (iss.code === 200) ok('开工包已签发');
  else bad(`签发失败：${iss.msg}`);

  // ---------- 10. 治理层审计 ----------
  const audit = await api(auth, 'GET', '/aigov/audit/list?pageNum=1&pageSize=100');
  const rows = audit?.data?.rows ?? [];
  const parseAudits = rows.filter((r) => r.capabilityCode === 'document_parse');
  const pcAudits = rows.filter((r) => r.capabilityCode === 'brief_precheck');
  info(`审计：document_parse ${parseAudits.length} 条，brief_precheck ${pcAudits.length} 条`);
  if (parseAudits.length > 0 && pcAudits.length > 0) ok('解析与预检的每次调用都在治理层留痕');
  else bad('治理层审计缺失');
  const external = [...parseAudits, ...pcAudits].filter((r) => String(r.externalCall) !== 'N');
  if (external.length === 0) ok('全部调用 external_call=N（数据未出网）');
  else bad(`存在外发调用 ${external.length} 条`);

  console.log(`\n[用例对象] productId=${productId} taskId=${taskId} packageId=${packageId} 任务号=${finalDetail?.data?.task?.taskNo}`);
  console.log(fail === 0 ? '内容生产协同冒烟: 全部通过' : `内容生产协同冒烟: ${fail} 项失败`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => { console.error('冒烟脚本异常:', e.message); process.exit(2); });
