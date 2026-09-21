// AI 治理层「模型密钥直接录入」端到端冒烟。
//
// 前置：
//   1. 应用已启动，且开启了密钥录入：
//        --aigov.model-crypto.enabled=true
//        --aigov.model-crypto.secret-key=<与 snail-ai 的 snail-ai.crypto.secret-key 一致>
//        --aigov.model-crypto.iv=<同上，对应 snail-ai.crypto.iv>
//   2. 已执行 script/sql/aig_ai_gov.sql、aig_ai_gov_menu.sql。
//   3. 至少要有一个「openai-compatible + 配了 api_endpoint」的模型可供探测。
//
// 用法：node script/smoke/smoke-model-secret.mjs
//
// 这条用例守的是三件事：
//   A. 密钥能录进去，且列表/详情只回布尔位，任何响应都不出现密钥原值；
//   B. 密钥值确实进入了外发请求（写入前鉴权头缺失、写入后有值），
//      连通性测试的这一路解密没有失败，且回显里不会出现密钥明文；
//   C. 既有的 REF:// 引用不会被新增的 scheme 白名单误伤（回归护栏）。
//
// ⚠️ 本脚本**仍不能**证明「密文与 snail-ai 口径一致」：
//      RuoYi 用同一对 key/iv 加密、也在同一对 key/iv 上解密，自己跟自己永远自洽。
//      跨系统兼容性必须用 snail-ai 侧的 /ai-model/config/{id}/test 单独验证
//      （提交说明里有实测记录：正确密文得到干净的上游 401，明文则报 System exception）。
//
// 说明：本脚本会自行清理——结束前一定把写入的密钥清除，不留残留。
//      唯一会留下的痕迹是「既有引用不被误伤」那条断言：它把某个已登记治理属性模型的
//      secretRef 原样写回一次，因此会刷新该行的 update_by/update_time（值不变）。
//      这是通过接口验证「白名单接受既有值」的唯一方式，属于有意为之。

const BASE = 'http://127.0.0.1:8080';
const CID = 'e5cd7e4891bf95d1d19206ce24a7b32e';

// 只用于本用例的假密钥；不是任何真实凭据
const FAKE_KEY = 'sk-smoke-verify-not-a-real-key-0001';

let fail = 0;
let skip = 0;
const ok = (m) => console.log(`  OK    ${m}`);
const bad = (m) => {
  console.log(`  FAIL  ${m}`);
  fail++;
};
const info = (m) => console.log(`        ${m}`);
const skipped = (m) => {
  console.log(`  SKIP  ${m}`);
  skip++;
};

async function login(username, password) {
  const r = await (
    await fetch(`${BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ clientId: CID, grantType: 'password', username, password })
    })
  ).json();
  const token = r?.data?.access_token;
  if (!token) return { err: `登录失败(${username}) code=${r.code} msg=${r.msg}` };
  return {
    headers: { Authorization: `Bearer ${token}`, clientid: CID, 'Content-Type': 'application/json' }
  };
}

const rowsOf = (j) => j?.data?.rows ?? j?.data?.records ?? [];

async function main() {
  const adminLogin = await login('admin', 'admin123');
  if (adminLogin.err) {
    bad(adminLogin.err);
    return;
  }
  const auth = adminLogin.headers;
  ok('管理员登录成功');

  // ---------- 找出可探测的目标模型 ----------
  const listRes = await (await fetch(`${BASE}/aigov/model/list?pageNum=1&pageSize=50`, { headers: auth })).json();
  const models = rowsOf(listRes);
  const target = models.find((m) => m.adapterKey === 'openai-compatible' && m.apiEndpoint);
  if (!target) {
    bad('找不到「openai-compatible + 有 api_endpoint」的模型，无法验证密钥链路');
    return;
  }
  const modelId = target.modelId;
  info(`目标模型 modelId=${modelId} modelKey=${target.modelKey} endpoint=${target.apiEndpoint}`);

  // ---------- 保证起点干净 ----------
  if (target.keyConfigured === true) {
    info('目标模型已有密钥（上次运行残留），先清除以保证断言从干净状态开始');
    await fetch(`${BASE}/aigov/model/secret`, {
      method: 'PUT',
      headers: auth,
      body: JSON.stringify({ modelId, clearKey: true })
    });
  }

  // ---------- 基线：无密钥时的连通性测试 ----------
  const probeBefore = await (
    await fetch(`${BASE}/aigov/model/${modelId}/test`, { method: 'POST', headers: auth })
  ).json();
  const detailBefore = String(probeBefore?.data?.detail ?? '');
  info(`无密钥探测：ok=${probeBefore?.data?.ok} msg=${probeBefore?.data?.message}`);
  if (detailBefore.includes('Incorrect API key')) {
    bad('无密钥时上游就回「Incorrect API key」，说明环境里还残留着密钥，后续断言不可信');
  } else {
    ok('无密钥时上游未收到「Incorrect API key」（基线成立）');
  }

  // ---------- 密钥状态的基线 ----------
  const listBefore = rowsOf(
    await (await fetch(`${BASE}/aigov/model/list?pageNum=1&pageSize=50`, { headers: auth })).json()
  ).find((m) => m.modelId === modelId);
  if (listBefore?.keyConfigured === false) ok('列表基线：keyConfigured=false（未配置密钥）');
  else bad(`列表基线异常：keyConfigured=${JSON.stringify(listBefore?.keyConfigured)}，期望 false`);

  // ---------- A1. scheme 白名单：短明文必须被拒 ----------
  // 必须挑一个**已登记治理属性**的模型：/governance 的 EditGroup 校验要求
  // deploymentType/dataLevelMax/lifecycleStatus 齐全，未登记治理属性的模型会在
  // 到达 checkSecretRef 之前就因「生命周期状态不能为空」被拒，那样断言就测错了东西。
  const govModel = models.find((m) => m.governanceId && m.deploymentType && m.lifecycleStatus);
  if (!govModel) {
    skipped('没有已登记治理属性的模型，跳过 scheme 白名单断言');
  } else {
    const plainRef = await (
      await fetch(`${BASE}/aigov/model/governance`, {
        method: 'PUT',
        headers: auth,
        body: JSON.stringify({
          governanceId: govModel.governanceId,
          modelId: govModel.modelId,
          deploymentType: govModel.deploymentType,
          dataLevelMax: govModel.dataLevelMax,
          lifecycleStatus: govModel.lifecycleStatus,
          secretRef: 'mysecret123'
        })
      })
    ).json();
    if (plainRef.code !== 200) {
      ok(`短明文 secretRef 被拒（code=${plainRef.code}）`);
      if (String(plainRef.msg).includes('密钥引用')) ok('拒绝文案含「密钥引用」字样');
      else bad(`拒绝文案未说明是密钥引用问题：${plainRef.msg}`);
    } else {
      bad('短明文 mysecret123 被接受 —— scheme 白名单未生效');
    }
  }

  // ---------- A2. 既有 REF:// 引用不能被误伤（回归护栏） ----------
  const refModel = models.find((m) => m.secretRef);
  if (!refModel) {
    skipped('没有已登记 secretRef 的模型，跳过「既有引用不被误伤」的回归断言');
  } else {
    const refRes = await (
      await fetch(`${BASE}/aigov/model/governance`, {
        method: 'PUT',
        headers: auth,
        body: JSON.stringify({
          governanceId: refModel.governanceId,
          modelId: refModel.modelId,
          deploymentType: refModel.deploymentType,
          dataLevelMax: refModel.dataLevelMax,
          lifecycleStatus: refModel.lifecycleStatus,
          secretRef: refModel.secretRef
        })
      })
    ).json();
    if (refRes.code === 200) ok(`既有引用原样回传被接受（${refModel.secretRef}）`);
    else bad(`既有引用 ${refModel.secretRef} 被拒（code=${refRes.code} msg=${refRes.msg}）—— 白名单误伤既有数据`);
  }

  // ---------- B1. 写入密钥 ----------
  const writeRes = await (
    await fetch(`${BASE}/aigov/model/secret`, {
      method: 'PUT',
      headers: auth,
      body: JSON.stringify({ modelId, apiKey: FAKE_KEY })
    })
  ).json();
  if (writeRes.code === 200) ok(`写入模型密钥成功（影响行数=${writeRes.data}）`);
  else bad(`写入模型密钥失败：code=${writeRes.code} msg=${writeRes.msg}`);

  // ---------- B2. 状态翻转 + 不回显 ----------
  const listAfterRaw = await (
    await fetch(`${BASE}/aigov/model/list?pageNum=1&pageSize=50`, { headers: auth })
  ).text();
  if (listAfterRaw.includes(FAKE_KEY)) bad('列表响应里出现了密钥明文 —— 严重泄漏');
  else ok('列表响应不含密钥明文');

  const listAfter = rowsOf(JSON.parse(listAfterRaw)).find((m) => m.modelId === modelId);
  if (listAfter?.keyConfigured === true) ok('写入后列表 keyConfigured=true');
  else bad(`写入后 keyConfigured=${JSON.stringify(listAfter?.keyConfigured)}，期望 true`);

  const detailAfterRaw = await (await fetch(`${BASE}/aigov/model/${modelId}`, { headers: auth })).text();
  if (detailAfterRaw.includes(FAKE_KEY)) bad('详情响应里出现了密钥明文 —— 严重泄漏');
  else ok('详情响应不含密钥明文');
  const detailAfter = JSON.parse(detailAfterRaw);
  if (detailAfter?.data?.keyConfigured === true) ok('写入后详情 keyConfigured=true');
  else bad(`写入后详情 keyConfigured=${JSON.stringify(detailAfter?.data?.keyConfigured)}，期望 true`);

  // ---------- B3. 密钥值确实进入了外发请求（且解密这一路没坏） ----------
  const probeAfter = await (
    await fetch(`${BASE}/aigov/model/${modelId}/test`, { method: 'POST', headers: auth })
  ).json();
  const detailAfterProbe = String(probeAfter?.data?.detail ?? '');
  const msgAfter = String(probeAfter?.data?.message ?? '');
  info(`写入后探测：ok=${probeAfter?.data?.ok} msg=${msgAfter}`);
  if (msgAfter.includes('解密失败') || msgAfter.includes('密钥加解密')) {
    bad(`连通性测试没能解出密钥：${msgAfter}`);
  } else if (detailAfterProbe.includes(FAKE_KEY)) {
    bad('连通性测试的 detail 回显了密钥明文 —— 上游回显未被掩码');
  } else if (msgAfter.includes('连接异常')) {
    skipped(`上游不可达（${msgAfter}），无法验证密钥是否进入请求`);
  } else if (detailAfterProbe.includes('Incorrect API key')) {
    ok('写入后请求带上了密钥（上游报「Incorrect API key」；写入前是「未提供密钥」）');
  } else {
    bad(`写入后未出现上游鉴权报错，密钥可能未进入请求：detail=${detailAfterProbe.slice(0, 160)}`);
  }

  // ---------- A3. 清除语义必须是显式的 ----------
  const blankRes = await (
    await fetch(`${BASE}/aigov/model/secret`, {
      method: 'PUT',
      headers: auth,
      body: JSON.stringify({ modelId, apiKey: '' })
    })
  ).json();
  if (blankRes.code !== 200) ok(`apiKey 留空且未声明 clearKey 被拒（code=${blankRes.code}）`);
  else bad('apiKey 留空且未声明 clearKey 竟然成功 —— 会把「误清空」变成默认行为');

  const clearRes = await (
    await fetch(`${BASE}/aigov/model/secret`, {
      method: 'PUT',
      headers: auth,
      body: JSON.stringify({ modelId, clearKey: true })
    })
  ).json();
  if (clearRes.code === 200) ok(`清除密钥成功（影响行数=${clearRes.data}）`);
  else bad(`清除密钥失败：code=${clearRes.code} msg=${clearRes.msg}`);

  const listFinal = rowsOf(
    await (await fetch(`${BASE}/aigov/model/list?pageNum=1&pageSize=50`, { headers: auth })).json()
  ).find((m) => m.modelId === modelId);
  if (listFinal?.keyConfigured === false) ok('清除后 keyConfigured=false（现场已还原）');
  else bad(`清除后 keyConfigured=${JSON.stringify(listFinal?.keyConfigured)}，期望 false`);

  const probeFinal = await (
    await fetch(`${BASE}/aigov/model/${modelId}/test`, { method: 'POST', headers: auth })
  ).json();
  if (String(probeFinal?.data?.detail ?? '').includes('Incorrect API key')) {
    bad('清除后上游仍报「Incorrect API key」——密钥没有被真正清掉');
  } else {
    ok('清除后探测回到「未提供密钥」状态');
  }
}

main()
  .then(() => {
    const tail = skip > 0 ? `，${skip} 项跳过` : '';
    console.log(fail === 0 ? `\n模型密钥直录冒烟: 全部通过${tail}` : `\n模型密钥直录冒烟: ${fail} 项失败${tail}`);
    process.exit(fail === 0 ? 0 : 1);
  })
  .catch((e) => {
    console.error('冒烟脚本异常:', e.message);
    process.exit(2);
  });
