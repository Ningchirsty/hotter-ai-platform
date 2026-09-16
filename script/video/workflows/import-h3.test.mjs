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
    assert.equal(binding.status, 'DRAFT');
    assert.deepEqual(binding.mapping.map(item => item.field), capability.fields.map(item => item.field).filter(field => !['tier', 'dur'].includes(field)));
    assert.deepEqual(binding.fixedFieldValidation, { tier: '高清 · 1080P', dur: '5 秒' });
    assert.doesNotMatch(content, /mmwebwx|webwxgetmsgimg|@crypt_|MsgID=|蝴蝶|铃兰/i);
    const template = JSON.parse(content);
    const timeline = JSON.parse(template['5'].inputs.timeline_data);
    assert.equal(template['14'].inputs.width, binding.supportedOutputs[0].width);
    assert.equal(template['14'].inputs.height, binding.supportedOutputs[0].height);
    assert.ok(binding.supportedOutputs[0].sourceDurationSeconds > binding.outputRule.maxDurationSeconds);
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
