import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import image from '../../views/image/local-workflows.json';
import video from '../../views/video/local-workflows.json';
import manifest from '../../../../script/local-workflow-import-20261010.json';
import { matchRegisteredWorkflow, workflowFields, type LocalWorkflowOption } from './types';
import { canSubmitLocal } from '../CreativeInspiration/types';

const all = [...image, ...video] as LocalWorkflowOption[];
describe('31 个工作流目录和提交边界', () => {
  it('图像 11 / 视频 20 与真实验证记录一一对应，API 工件哈希一致', () => {
    expect(image).toHaveLength(11); expect(video).toHaveLength(20);
    expect(new Set(all.map(w => w.workflowCode)).size).toBe(31);
    expect(all.map(w => w.workflowCode).toSorted()).toEqual(manifest.map(w => w.workflowCode).toSorted());
    for (const row of manifest) {
      const entry = all.find(w => w.workflowCode === row.workflowCode)!;
      expect(entry.media).toBe(row.media); expect(entry.capabilityCode).toBe(row.capabilityCode);
      expect(row.comfyStatus).toBe('success'); expect(row.platformStatus).toBe('UNREGISTERED');
      const api = readFileSync(new URL(`../../../../script/${row.apiJsonFile}`, import.meta.url));
      expect(createHash('sha256').update(api).digest('hex')).toBe(row.apiChecksum);
      const classes = Object.values(JSON.parse(api.toString())).map((n: any) => n.class_type);
      expect(classes.some(type => row.media === 'image' ? ['SaveImage', 'SaveImageAdvanced', 'PreviewImage'].includes(type) : type === 'SaveVideo')).toBe(true);
    }
  });

  it('图像和视频数据仅含本模块能力，公开目录不含节点、路径或服务器地址', () => {
    expect(image.every(w => w.media === 'image' && ['T2I', 'EDIT', 'CONTROL'].includes(w.capabilityCode))).toBe(true);
    expect(video.every(w => w.media === 'video' && ['T2V', 'I2V', 'FL2V', 'R2V'].includes(w.capabilityCode))).toBe(true);
    expect(new Set(image.map(w => w.modelCode)).size).toBe(6);
    expect(new Set(video.map(w => w.modelCode)).size).toBe(5);
    expect(JSON.stringify(all)).not.toMatch(/class_type|nodeId|apiJsonFile|safetensors|192\.168\.|submitted_prompt/);
  });

  it('五个本地视频模型均提供与后端一致的 5、10、20 秒档位', () => {
    const contract = JSON.parse(readFileSync(new URL('../../../../script/video/workflows/native-workflow-contracts.json', import.meta.url), 'utf8'));
    const bindings = contract.capabilities.flatMap((c: any) => c.workflows);
    for (const option of video) {
      const backend = bindings.find((w: any) => w.workflowCode === option.workflowCode);
      expect(backend).toBeDefined();
      expect(option.supportedDuration).toBe('5 秒');
      expect(option.maxDurationSeconds).toBe(20);
      for (const tier of option.supportedTiers) {
        const profile = backend.fixedFieldValidation.outputProfiles.find((p: any) => p.tier === tier);
        expect(option.supportedDurationsByTier[tier]).toEqual(profile.durations);
        expect(option.supportedDurationsByTier[tier]).toContain('5 秒');
      }
    }
  });

  it('每个本地视频模型及用途的三个比例、三档尺寸与后端契约一致且不泄漏节点配置', () => {
    const contract = JSON.parse(readFileSync(new URL('../../../../script/video/workflows/native-workflow-contracts.json', import.meta.url), 'utf8'));
    const bindings = contract.capabilities.flatMap((c: any) => c.workflows);
    const abilities = JSON.parse(readFileSync(new URL('../../views/video/abilities.json', import.meta.url), 'utf8'));
    const serverAbilities = JSON.parse(readFileSync(new URL('../../../../script/video/workflows/abilities.json', import.meta.url), 'utf8'));
    expect(abilities).toEqual(serverAbilities);
    const options = [...video, ...abilities.flatMap((a: any) => a.workflows)];
    expect(options).toHaveLength(35);
    for (const option of options) {
      const fixed = bindings.find((w: any) => w.workflowCode === option.workflowCode).fixedFieldValidation;
      expect(option.supportedRatios).toEqual(['16:9 横屏', '9:16 竖屏', '1:1 方形']);
      expect(option.outputProfiles).toHaveLength(9);
      expect(option.supportedTiers).toEqual(fixed.supportedTiers);
      expect(option.outputProfiles).toEqual(fixed.outputProfiles.filter((p: any) => !p.legacy).map(({ legacy, ...p }: any) => p));
      for (const ratio of option.supportedRatios) {
        expect(option.supportedTiersByRatio[ratio]).toHaveLength(3);
      }
      for (const profile of option.outputProfiles) {
        expect(profile.tier.length).toBeLessThanOrEqual(32);
        expect(profile.tier).toContain(`${profile.width}×${profile.height}`);
        expect(option.supportedDurationsByTier[profile.tier]).toEqual(profile.durations);
        const shortEdge = Math.min(profile.width, profile.height);
        expect(profile.durations).toEqual(shortEdge === 480 ? ['5 秒', '10 秒', '20 秒'] : shortEdge === 720 ? ['5 秒', '10 秒'] : ['5 秒']);
      }
    }
    expect(JSON.stringify(options)).not.toMatch(/nodeId|videoNodeId|resizeNodeId|class_type|apiJsonFile|safetensors/);
  });

  it('提交必须匹配媒体、能力、模型、版本和服务器发布许可', () => {
    const option = all[0]!;
    const registered = { ...option, status: 'PUBLISHED', submittable: true };
    expect(matchRegisteredWorkflow(option.media, option, [registered])).toBe(registered);
    expect(matchRegisteredWorkflow('image', video[0] as LocalWorkflowOption, [registered])).toBeUndefined();
    expect(matchRegisteredWorkflow('video', image[0] as LocalWorkflowOption, [registered])).toBeUndefined();
    for (const change of [{ capabilityCode: 'OTHER' }, { modelCode: 'OTHER' }, { version: 'v0' }, { workflowCode: 'OTHER' }]) {
      expect(matchRegisteredWorkflow(option.media, option, [{ ...registered, ...change }])).toBeUndefined();
    }
    expect(canSubmitLocal('local', matchRegisteredWorkflow(option.media, option, []))).toBe(false);
    for (const status of ['DRAFT', 'TESTING', 'RETIRED']) {
      expect(canSubmitLocal('local', matchRegisteredWorkflow(option.media, option, [{ ...registered, status }]))).toBe(false);
    }
    expect(canSubmitLocal('local', { ...registered, submittable: false })).toBe(false);
  });

  it('切换创作能力后隐藏字段与残留素材不会进入提交载荷', () => {
    const values = { prompt: '图像', desc: '视频', img: 10, first: 20, last: 21, reference1: 30, reference2: 31, tier: '验证', dur: '2 秒', nodeId: 5 };
    const t2v = video.find(w => w.capabilityCode === 'T2V') as LocalWorkflowOption;
    const fl2v = video.find(w => w.capabilityCode === 'FL2V') as LocalWorkflowOption;
    const r2v = video.find(w => w.capabilityCode === 'R2V') as LocalWorkflowOption;
    expect(workflowFields(t2v, values)).toEqual({ desc: '视频', tier: '验证', dur: '2 秒' });
    expect(workflowFields(fl2v, values)).toEqual({ first: 20, last: 21, desc: '视频', tier: '验证', dur: '2 秒' });
    expect(workflowFields(r2v, values)).toEqual({ reference1: 30, reference2: 31, desc: '视频', tier: '验证', dur: '2 秒' });
    for (const option of all) expect(Object.keys(workflowFields(option, values)).every(key => option.fields.includes(key))).toBe(true);
  });
});
