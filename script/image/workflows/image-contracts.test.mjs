/**
 * 图像创作模块 · 契约与模板一致性测试
 *
 *   node --test script/image/workflows/image-contracts.test.mjs
 *
 * 与视频模块的 import-h3.test.mjs 保持同一套路（node:test + assert/strict，无第三方依赖），
 * 但断言的是本模块自己产出的四份 API Format 模板。要点：
 *  - 模板文件必须存在、必须是规范格式、SHA-256 必须与契约 checksum 一致（后端启动即按此拒绝加载）；
 *  - mapping 白名单必须与能力声明的字段、与模板里的节点/输入键三方对齐；
 *  - 模板里不允许残留样例提示词或样例文件名（由服务端按任务填充）；
 *  - 状态必须自洽：契约条目与 meta.status 同时为 DRAFT（避免出现「条目 PUBLISHED 但测试断言 DRAFT」的红测试）。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import {
  loadContract,
  bindings,
  verifyBinding,
  canonicalize,
  sha256,
  templatePath,
  contractRoot,
} from './image-contracts.mjs';

const contract = loadContract();

/** 每个能力期望的 mapping 字段集合（与契约 fields 对齐，size/strength 等由固定档位校验处理）。 */
const EXPECTED_MAPPING = {
  T2I: ['prompt', 'negative_prompt'],
  I2I: ['img', 'prompt', 'negative_prompt', 'strength'],
  EDIT: ['image1', 'image2', 'image3', 'prompt', 'negative_prompt'],
  BGREMOVE: ['img'],
};

/** 期望的输出节点与模板节点数。 */
const EXPECTED_SHAPE = {
  'wf-t2i-qwen21': { outputNode: '8', nodes: 8 },
  'wf-i2i-qwen21': { outputNode: '9', nodes: 9 },
  'wf-edit-qwen21': { outputNode: '10', nodes: 10 },
  'wf-bgremove-qwen21': { outputNode: '8', nodes: 8 },
};

const MODEL_TRIO = {
  unet: 'qwen_image_2.1_int8_convrot.safetensors',
  clip: 'qwen3vl_8b_int8_convrot.safetensors',
  vae: 'qwen_image_2.1_vae_bf16.safetensors',
};

test('契约文件存在且能力/工作流数量符合预期', () => {
  assert.equal(contract.meta.name, 'hotter-ai image workflow contracts');
  assert.equal(contract.meta.status, 'DRAFT', 'meta.status 必须与各条目状态一致（当前为 DRAFT）');
  assert.deepEqual(
    contract.capabilities.map((c) => c.capabilityCode),
    ['T2I', 'I2I', 'EDIT', 'BGREMOVE'],
  );
  for (const c of contract.capabilities) {
    assert.equal(c.workflows.length, 1, `${c.capabilityCode} 应绑定 1 个工作流`);
    assert.ok(c.name && /[\u4e00-\u9fa5]/.test(c.name), `${c.capabilityCode} 的中文名缺失`);
  }
  assert.equal(bindings(contract).length, 4);
});

test('每个模板都存在、格式规范、SHA-256 与契约一致', () => {
  for (const { workflow } of bindings(contract)) {
    const r = verifyBinding(workflow);
    assert.ok(r.ok, `${workflow.workflowCode} 校验失败：${r.errors.join('; ')}`);
    const raw = readFileSync(r.file, 'utf8');
    assert.equal(r.checksum, sha256(canonicalize(JSON.parse(raw))), 'checksum 必须按文件字节计算');
  }
});

test('工作流状态一律为 DRAFT（未通过生产联调前不得发布）', () => {
  for (const { workflow } of bindings(contract)) {
    assert.equal(workflow.status, 'DRAFT', `${workflow.workflowCode} 未经业务批准不得提升状态`);
    assert.equal(workflow.version, 'v0.1.0-draft');
  }
});

test('mapping 白名单与能力字段、模板节点三方对齐', () => {
  for (const { capability, workflow } of bindings(contract)) {
    const fields = (capability.fields ?? []).map((f) => f.field);
    const mapped = (workflow.mapping ?? []).map((m) => m.field);
    assert.deepEqual(
      [...mapped].sort(),
      [...(EXPECTED_MAPPING[capability.capabilityCode] ?? [])].sort(),
      `${workflow.workflowCode} 的 mapping 字段集不符合预期`,
    );
    for (const field of mapped) {
      assert.ok(fields.includes(field), `${workflow.workflowCode} 的 mapping 字段 ${field} 不在能力字段里`);
    }
    const r = verifyBinding(workflow);
    for (const m of workflow.mapping) {
      const node = r.template[m.nodeId];
      assert.ok(node, `${workflow.workflowCode} 的 mapping 指向不存在的节点 ${m.nodeId}`);
      assert.ok(
        Object.prototype.hasOwnProperty.call(node.inputs, m.inputKey),
        `${workflow.workflowCode} 节点 ${m.nodeId} 没有输入键 ${m.inputKey}`,
      );
    }
  }
});

test('模板结构与输出节点符合预期', () => {
  for (const { workflow } of bindings(contract)) {
    const shape = EXPECTED_SHAPE[workflow.workflowCode];
    assert.ok(shape, `未登记的 workflowCode：${workflow.workflowCode}`);
    const r = verifyBinding(workflow);
    assert.equal(Object.keys(r.template).length, shape.nodes, `${workflow.workflowCode} 节点数不符`);
    assert.equal(workflow.outputRule.nodeId, shape.outputNode);
    const outNode = r.template[workflow.outputRule.nodeId];
    assert.equal(outNode.class_type, 'SaveImage', '输出节点必须是 SaveImage（保留 alpha 通道）');
    assert.equal(workflow.outputRule.outputField, 'images');
    assert.equal(workflow.outputRule.format, 'png');
    assert.equal(workflow.outputRule.mime, 'image/png');
  }
});

test('模型三件套固定且与 ComfyUI 中已下载的权重一致', () => {
  for (const { workflow } of bindings(contract)) {
    const fixed = workflow.fixedParams;
    assert.equal(fixed.model, 'Qwen-Image-2.1 int8_convrot');
    assert.equal(fixed.textEncoder, 'qwen3vl_8b_int8_convrot');
    assert.equal(fixed.vae, 'qwen_image_2.1_vae_bf16');
    assert.equal(fixed.sampler, 'euler');
    assert.equal(fixed.scheduler, 'simple');
    assert.equal(fixed.steps, 25);
    assert.equal(fixed.cfg, 1);

    const r = verifyBinding(workflow);
    const unet = Object.values(r.template).find((n) => n.class_type === 'UNETLoader');
    const clip = Object.values(r.template).find((n) => n.class_type === 'CLIPLoader');
    const vae = Object.values(r.template).find((n) => n.class_type === 'VAELoader');
    assert.equal(unet.inputs.unet_name, MODEL_TRIO.unet);
    assert.equal(clip.inputs.clip_name, MODEL_TRIO.clip);
    assert.equal(clip.inputs.type, 'qwen_image');
    assert.equal(vae.inputs.vae_name, MODEL_TRIO.vae);
  }
});

test('采样参数固定：仅图生图允许 denoise 被覆写，其余 denoise=1', () => {
  for (const { capability, workflow } of bindings(contract)) {
    const r = verifyBinding(workflow);
    const ks = Object.entries(r.template).find(([, n]) => n.class_type === 'KSampler');
    assert.ok(ks, `${workflow.workflowCode} 缺少 KSampler`);
    const [, node] = ks;
    assert.equal(node.inputs.steps, 25);
    assert.equal(node.inputs.cfg, 1);
    assert.equal(node.inputs.sampler_name, 'euler');
    assert.equal(node.inputs.scheduler, 'simple');
    assert.equal(node.inputs.seed, 0, 'seed 必须留 0 由服务端按任务下发');
    if (capability.capabilityCode === 'I2I') {
      assert.ok(node.inputs.denoise > 0 && node.inputs.denoise < 1, '图生图的 denoise 必须是部分重绘');
    } else {
      assert.equal(node.inputs.denoise, 1);
    }
  }
});

test('模板不残留样例提示词或样例文件名（由服务端按任务填充）', () => {
  for (const { capability, workflow } of bindings(contract)) {
    const r = verifyBinding(workflow);
    for (const [id, node] of Object.entries(r.template)) {
      if (node.class_type === 'LoadImage') {
        assert.equal(node.inputs.image, '', `${workflow.workflowCode} 节点 ${id} 残留了样例文件名`);
      }
      if (node.class_type === 'TextEncodeQwenImage21') {
        const editable = capability.capabilityCode !== 'BGREMOVE';
        if (editable) {
          assert.equal(node.inputs.prompt, '', `${workflow.workflowCode} 残留了样例提示词`);
        } else {
          assert.ok(node.inputs.prompt.length > 0, '抠图模板必须带固定提示词');
        }
        assert.equal(node.inputs.negative_prompt, '');
      }
      const title = node._meta?.title ?? '';
      assert.ok(title.length > 0, `${workflow.workflowCode} 节点 ${id} 缺少 _meta.title`);
      assert.ok(!/[ÃÂ�]/.test(title), `${workflow.workflowCode} 节点 ${id} 的标题疑似编码损坏`);
    }
  }
});

test('参考图槽位：images.image_N 必须指向存在的 LoadImage 节点', () => {
  for (const code of ['wf-edit-qwen21', 'wf-bgremove-qwen21']) {
    const { workflow } = bindings(contract).find((b) => b.workflow.workflowCode === code);
    const r = verifyBinding(workflow);
    const encoder = Object.entries(r.template).find(([, n]) => n.class_type === 'TextEncodeQwenImage21');
    assert.ok(encoder, `${code} 缺少 TextEncodeQwenImage21`);
    const slots = Object.keys(encoder[1].inputs).filter((k) => k.startsWith('images.image_'));
    assert.ok(slots.length >= 1, `${code} 未声明任何参考图槽位`);
    for (const key of slots) {
      const link = encoder[1].inputs[key];
      assert.ok(Array.isArray(link) && link.length === 2, `${code} 的 ${key} 必须是节点连线`);
      const target = r.template[String(link[0])];
      assert.ok(target, `${code} 的 ${key} 指向不存在的节点 ${link[0]}`);
      assert.equal(target.class_type, 'LoadImage', `${code} 的 ${key} 必须连到 LoadImage`);
      assert.equal(link[1], 0);
    }
    assert.equal(
      slots.length,
      code === 'wf-bgremove-qwen21' ? 1 : 3,
      `${code} 的参考图槽位数与契约描述不符`,
    );
  }
});

test('契约声明的模板路径落在后端受控目录内', () => {
  const root = contractRoot();
  for (const { workflow } of bindings(contract)) {
    const p = templatePath(workflow, root);
    assert.ok(p.startsWith(root), '模板必须位于 contract-root 之内');
    assert.ok(existsSync(p), `模板不存在：${workflow.apiJsonFile}`);
    assert.match(workflow.apiJsonFile, /^image\/workflows\/api\/wf-[a-z0-9-]+-v0\.1\.0\.json$/);
  }
});

test('真机验证记录存在且四个模板均实测成功', () => {
  const reportPath = `${contractRoot()}/image/workflows/api/_validation-live.json`;
  assert.ok(existsSync(reportPath), '缺少真机验证记录 _validation-live.json');
  const report = JSON.parse(readFileSync(reportPath, 'utf8'));
  for (const { workflow } of bindings(contract)) {
    const entry = report[workflow.workflowCode];
    assert.ok(entry, `${workflow.workflowCode} 没有真机验证记录`);
    assert.equal(entry.status, 'success', `${workflow.workflowCode} 真机验证未通过`);
    assert.ok(entry.seconds > 0 && entry.seconds < 300);
    assert.ok(Array.isArray(entry.size) && entry.size.length === 2);
    assert.match(entry.prompt_id, /^[0-9a-f-]{36}$/);
  }
});

test('写作辅助：--write 模式可重算 checksum 且结果稳定', async () => {
  const { rewriteChecksums } = await import('./image-contracts.mjs');
  const before = readFileSync(`${contractRoot()}/image/workflows/image-workflow-contracts.json`, 'utf8');
  const changed = rewriteChecksums();
  const after = readFileSync(`${contractRoot()}/image/workflows/image-workflow-contracts.json`, 'utf8');
  assert.deepEqual(changed, [], 'checksum 未变化时不应有改动');
  assert.equal(after, before, '重算不应改变契约内容');
});
