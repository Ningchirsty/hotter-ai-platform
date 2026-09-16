import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const directory = path.dirname(fileURLToPath(import.meta.url));
const outputDirectory = path.join(directory, 'api');
const specs = [
  { capability: 'I2V', marker: 'i2v', code: 'wf-i2v-h3', fields: ['img', 'desc', 'tier', 'dur'] },
  { capability: 'T2V', marker: 't2v', code: 'wf-t2v-h3', fields: ['desc', 'tier', 'dur'] },
  { capability: 'FL2V', marker: 'fl2v', code: 'wf-fl2v-h3', fields: ['first', 'last', 'desc', 'tier', 'dur'] }
];

function clearSampleData(value) {
  if (Array.isArray(value)) return value.map(clearSampleData);
  if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    if (['imageFile', 'videoFile', 'fileName', 'prompt', 'negativePrompt'].includes(key)) return [key, ''];
    return [key, clearSampleData(item)];
  }));
}

export function sanitizeH3Graph(source, spec) {
  const graph = structuredClone(source);
  const director = graph['5'];
  if (director?.class_type !== 'MiniMaxH3Director' || !director.inputs.task_type.startsWith(spec.marker)) {
    throw new Error(`${spec.code}: unexpected director or task type`);
  }
  if (graph['7']?.class_type !== 'SaveVideo' || graph['7'].inputs.video?.[0] !== '6') {
    throw new Error(`${spec.code}: unexpected video output`);
  }

  const timeline = clearSampleData(JSON.parse(director.inputs.timeline_data));
  timeline.global.prompt = '';
  timeline.segments[0].prompt = '';
  timeline.shots[0].prompt = '';
  timeline.keyframes = spec.capability === 'FL2V'
    ? timeline.keyframes.slice(0, 2)
    : spec.capability === 'I2V' ? timeline.keyframes.slice(0, 1) : [];
  if (spec.capability !== 'FL2V') {
    delete timeline.segments[0].endImage;
    delete timeline.shots[0].endImage;
  }
  director.inputs.global_prompt = '';
  director.inputs.seed = 0;
  director.inputs.timeline_data = JSON.stringify(timeline);
  const serialized = JSON.stringify(graph);
  if (/mmwebwx|webwxgetmsgimg|@crypt_|MsgID=/i.test(serialized)) {
    throw new Error(`${spec.code}: sample attachment reference remains`);
  }
  return graph;
}

export function prepareH3Graph(template, capability, fields) {
  const spec = specs.find(item => item.capability === capability);
  if (!spec) throw new Error('Unsupported H3 capability');
  if (fields.tier !== '高清 · 1080P' || fields.dur !== '5 秒' || !fields.desc?.trim()) {
    throw new Error('H3 template supports only approximately 1080P, 5 seconds and a nonempty prompt');
  }
  if (spec.capability === 'I2V' && !fields.img) throw new Error('Image is required');
  if (spec.capability === 'FL2V' && (!fields.first || !fields.last)) throw new Error('Both frames are required');

  const graph = structuredClone(template);
  const director = graph['5'].inputs;
  const timeline = JSON.parse(director.timeline_data);
  director.global_prompt = fields.desc.trim();
  timeline.global.prompt = director.global_prompt;
  timeline.segments[0].prompt = director.global_prompt;
  timeline.shots[0].prompt = director.global_prompt;
  if (capability !== 'T2V') {
    const first = capability === 'I2V' ? fields.img : fields.first;
    timeline.segments[0].genImage.imageFile = first;
    timeline.shots[0].startImage.imageFile = first;
    timeline.keyframes[0].imageFile = first;
  }
  if (capability === 'FL2V') {
    timeline.segments[0].endImage.imageFile = fields.last;
    timeline.shots[0].endImage.imageFile = fields.last;
    timeline.keyframes[1].imageFile = fields.last;
  }
  director.timeline_data = JSON.stringify(timeline);
  return graph;
}

function mappingFor(spec) {
  return spec.fields.filter(field => !['tier', 'dur'].includes(field)).map(field => ({
    field,
    nodeId: '5',
    inputKey: field === 'desc' ? 'global_prompt' : 'timeline_data',
    note: field === 'desc' ? 'Also update timeline.global/segments/shots prompt' :
      'Resolve uploaded image to a ComfyUI input filename and update timeline segment/shot/keyframe; use prepareH3Graph'
  }));
}

async function importGraphs(files) {
  if (files.length !== specs.length) throw new Error('Expected I2V, T2V and FL2V JSON paths in that order');
  const contractPath = path.join(directory, 'video-workflow-contracts.json');
  const contract = JSON.parse(await readFile(contractPath, 'utf8'));
  const imageCapability = contract.capabilities.find(item => item.capabilityCode === 'I2V');
  imageCapability.name = '图生视频';
  imageCapability.fields = [
    { field: 'img', required: true, type: 'file', accept: ['jpg', 'png', 'webp'] },
    { field: 'desc', required: true, type: 'string', maxLength: 200 },
    { field: 'tier', required: true, type: 'enum', options: ['高清 · 1080P', '流畅 · 720P', '标清 · 480P'] },
    { field: 'dur', required: true, type: 'enum', options: ['5 秒', '10 秒', '20 秒'] }
  ];
  for (const binding of imageCapability.workflows) {
    binding.mapping = binding.mapping.filter(item => item.field !== 'last');
    for (const item of binding.mapping) if (item.field === 'first') item.field = 'img';
  }
  if (!contract.capabilities.some(item => item.capabilityCode === 'FL2V')) {
    const firstLast = {
      capabilityCode: 'FL2V',
      name: '首尾帧生视频',
      fields: [
        { field: 'first', required: true, type: 'file', accept: ['jpg', 'png', 'webp'] },
        { field: 'last', required: true, type: 'file', accept: ['jpg', 'png', 'webp'] },
        ...imageCapability.fields.slice(1)
      ],
      workflows: [{ ...structuredClone(imageCapability.workflows[0]), workflowCode: 'wf-fl2v-h3' }]
    };
    contract.capabilities.splice(1, 0, firstLast);
  }
  contract.meta.updatedAt = new Date().toISOString().slice(0, 10);
  contract.meta.rules[2] = 'Three MiniMax H3 API templates have verified node IDs and checksums; runtime dependencies and task API remain untested (DRAFT)';
  const outputs = [];
  for (const [index, spec] of specs.entries()) {
    const source = JSON.parse(await readFile(files[index], 'utf8'));
    const graph = sanitizeH3Graph(source, spec);
    const content = `${JSON.stringify(graph, null, 2)}\n`;
    const capability = contract.capabilities.find(item => item.capabilityCode === spec.capability);
    if (!capability) throw new Error(`Missing contract capability ${spec.capability}`);
    const binding = capability.workflows.find(item => item.workflowCode === spec.code);
    if (!binding) throw new Error(`Missing contract workflow ${spec.code}`);
    binding.apiJsonFile = `workflows/api/${spec.code}-v0.1.0.json`;
    binding.checksum = createHash('sha256').update(content).digest('hex');
    binding.mapping = mappingFor(spec);
    binding.fixedFieldValidation = { tier: '高清 · 1080P', dur: '5 秒' };
    binding.fixedParams = { model: 'MiniMax H3 8-step BF16', sampler: 'res_multistep', steps: 8, cfg: 1, fps: 24, resolution: '1920x1088', seedPolicy: 'set by server per task' };
    binding.outputRule = { nodeId: '7', outputField: 'videos', format: 'mp4', mime: 'video/mp4', multiple: false, coverRule: 'first frame', maxSizeMB: 'TBD', maxDurationSeconds: 5, durationCapStatus: 'PENDING_RUNTIME_VALIDATION' };
    binding.supportedOutputs = [{ tier: '高清 · 1080P', duration: '5 秒', width: 1920, height: 1080, fps: 24, sourceFrames: 124, sourceDurationSeconds: 124 / 24 }];
    outputs.push({ filename: path.join(outputDirectory, `${spec.code}-v0.1.0.json`), content });
  }
  await mkdir(outputDirectory, { recursive: true });
  for (const output of outputs) await writeFile(output.filename, output.content, 'utf8');
  await writeFile(contractPath, `${JSON.stringify(contract, null, 2)}\n`, 'utf8');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  importGraphs(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
}
