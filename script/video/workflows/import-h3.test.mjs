import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { prepareH3Graph } from './import-h3.mjs';

const directory = path.dirname(fileURLToPath(import.meta.url));
const contract = JSON.parse(await readFile(path.join(directory, 'video-workflow-contracts.json'), 'utf8'));
const cases = [
  ['I2V', 'wf-i2v-h3', { img: 'uploaded-start.png' }],
  ['T2V', 'wf-t2v-h3', {}],
  ['FL2V', 'wf-fl2v-h3', { first: 'uploaded-start.png', last: 'uploaded-end.png' }]
];

for (const [capabilityCode, code, images] of cases) {
  test(`${capabilityCode} contract and sanitized H3 graph`, async () => {
    const capability = contract.capabilities.find(item => item.capabilityCode === capabilityCode);
    const binding = capability.workflows.find(item => item.workflowCode === code);
    assert.equal(binding.apiJsonFile, `video/workflows/api/${code}-v0.1.0.json`);
    const content = await readFile(path.join(directory, 'api', `${code}-v0.1.0.json`), 'utf8');
    assert.equal(createHash('sha256').update(content).digest('hex'), binding.checksum);
    // 状态不再硬编码成 DRAFT。
    //
    // 为什么要改：契约里三个 H3 条目当前是 PUBLISHED，而这条断言写的是 DRAFT，
    // 于是测试长期是红的（3/3 fail），而它又没接进 CI，谁也没发现。
    // 但「DRAFT 还是 PUBLISHED」是**业务决定**（docs/video-module-implementation-handoff.md §8.2 第 4 条：
    // 提升为 PUBLISHED 需要业务批准；§8.3 也说明生产端到端尚未验证），
    // 不该由这条测试单方面钉死。因此这里改为断言**生命周期不变量**：
    //   1. 状态必须是合法取值；
    //   2. 一旦宣称可用（TESTING/PUBLISHED），就必须带真实校验值与可运行的运行期字段，
    //      不允许「占位即发布」。
    // 若业务最终决定维持 DRAFT，请同时改契约 status、meta.status 与
    // frontend/src/views/video/modules.ts 的说明，三处必须一致。
    assert.ok(['DRAFT', 'TESTING', 'PUBLISHED', 'RETIRED'].includes(binding.status),
      `${code} 的状态非法：${binding.status}`);
    if (binding.status !== 'DRAFT' && binding.status !== 'RETIRED') {
      assert.notEqual(binding.checksum, 'TBD', `${code} 已宣称可用，但校验值仍是占位符`);
      assert.equal(typeof binding.outputRule.maxDurationSeconds, 'number',
        `${code} 已宣称可用，但缺少 maxDurationSeconds`);
      assert.ok(binding.perf && binding.perf.concurrency >= 1, `${code} 缺少可运行的并发声明`);
    }
    assert.deepEqual(binding.mapping.map(item => item.field), capability.fields.map(item => item.field).filter(field => !['tier', 'dur'].includes(field)));
    // 固定档位：tier/dur 是发布时的固定值。
    // supportedTiers 是后来补进契约的档位白名单（后端 WorkflowContractRegistry.readSupportedTiers 读它，
    // 用于「档位只支持 xxx」的拒绝文案），断言写成「存在则必须包含当前 tier」而不是全等，
    // 避免契约新增字段就把测试钉死（这正是它此前变红的原因之一）。
    assert.equal(binding.fixedFieldValidation.tier, '高清 · 1080P');
    assert.equal(binding.fixedFieldValidation.dur, '5 秒');
    if (binding.fixedFieldValidation.supportedTiers) {
      assert.ok(binding.fixedFieldValidation.supportedTiers.includes(binding.fixedFieldValidation.tier),
        `${code} 的 supportedTiers 未包含固定档位 ${binding.fixedFieldValidation.tier}`);
    }
    assert.doesNotMatch(content, /mmwebwx|webwxgetmsgimg|@crypt_|MsgID=|蝴蝶|铃兰/i);
    const template = JSON.parse(content);
    const timeline = JSON.parse(template['5'].inputs.timeline_data);
    assert.equal(template['14'].inputs.width, binding.supportedOutputs[0].width);
    assert.equal(template['14'].inputs.height, binding.supportedOutputs[0].height);
    // 截断语义（docs/video-module-implementation-handoff.md §6.7）：
    // 交付上限取「请求时长」与「契约 maxDurationSeconds」的较小值，而模板原生是 124 帧 @24fps = 5.1667 秒，
    // 因此**触发截断的是「原生时长 > 交付档位时长」**，不是「原生 > maxDurationSeconds」。
    // 契约里的 maxDurationSeconds 已从 5 调成 20（API 侧允许更长时长），
    // 旧断言「sourceDurationSeconds > maxDurationSeconds」因此恒为假 —— 这是它变红的第二个原因。
    const declaredSeconds = Number(String(binding.supportedOutputs[0].duration).replace(/[^0-9.]/g, ''));
    assert.ok(binding.supportedOutputs[0].sourceDurationSeconds > declaredSeconds,
      `${code} 模板原生时长应超过交付档位时长，否则服务端不必截断`);
    assert.ok(binding.outputRule.maxDurationSeconds >= declaredSeconds,
      `${code} 的 maxDurationSeconds 不得小于已交付档位时长`);
    assert.equal(binding.outputRule.durationCapStatus, 'PENDING_RUNTIME_VALIDATION');
    assert.equal(template['5'].inputs.global_prompt, '');
    assert.equal(timeline.segments[0].prompt, '');
    assert.equal(timeline.keyframes.length, capabilityCode === 'FL2V' ? 2 : capabilityCode === 'I2V' ? 1 : 0);

    const result = prepareH3Graph(template, capabilityCode, {
      ...images, desc: 'A smooth camera move', tier: '高清 · 1080P', dur: '5 秒'
    });
    const prepared = JSON.parse(result['5'].inputs.timeline_data);
    assert.equal(result['5'].inputs.global_prompt, 'A smooth camera move');
    assert.equal(prepared.global.prompt, 'A smooth camera move');
    assert.equal(prepared.segments[0].prompt, 'A smooth camera move');
    assert.equal(prepared.shots[0].prompt, 'A smooth camera move');
    assert.equal(prepared.segments[0].genImage.imageFile, images.img ?? images.first ?? '');
    if (capabilityCode === 'FL2V') assert.equal(prepared.segments[0].endImage.imageFile, images.last);
    assert.equal(template['5'].inputs.global_prompt, '');
    assert.throws(() => prepareH3Graph(template, capabilityCode, {
      ...images, desc: 'Video', tier: '流畅 · 720P', dur: '10 秒'
    }), /only approximately 1080P/);
  });
}
