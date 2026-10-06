import { describe, expect, it } from 'vitest';
import {
  cloudModelsFor,
  cloudMaterialSlots,
  cloudOutputOptions,
  cloudDurations,
  createCloudDraft
} from './cloud-models';
import { canSubmitLocal } from './types';

describe('云端候选能力与提交边界', () => {
  it('云端视频和图像分别推荐，并且均不可提交到本地工作流', () => {
    for (const media of ['video', 'image'] as const) {
      expect(cloudModelsFor(media).length).toBeGreaterThan(1);
      for (const model of cloudModelsFor(media)) {
        expect(model.media).toBe(media);
        expect(model.status).toBe('PENDING');
      }
    }
    expect(canSubmitLocal('cloud', { status: 'PUBLISHED', submittable: true })).toBe(false);
  });
  it('1080P 的 Hailuo 草稿不能沿用 10 秒选项', () => {
    const model = cloudModelsFor('video')[0];
    expect(cloudDurations(model, 'T2V', '768P')).toContain(10);
    expect(cloudDurations(model, 'T2V', '1080P')).not.toContain(10);
  });
  it('Veo 的参考图、高分辨率和续写使用限定参数组合', () => {
    const model = cloudModelsFor('video')[1];
    expect(cloudDurations(model, 'T2V', '720p')).toEqual([4, 6, 8]);
    expect(cloudDurations(model, 'T2V', '4k')).toEqual([8]);
    expect(cloudDurations(model, 'R2V', '720p')).toEqual([8]);
    expect(cloudOutputOptions(model, 'EXTEND').map(item => item.value)).toEqual(['720p']);
    expect(cloudMaterialSlots('EXTEND')).toEqual([]);
  });
  it('文生图不要求参考图，编辑与首尾帧具有不同的素材契约', () => {
    expect(cloudMaterialSlots('T2I')).toEqual([]);
    expect(cloudMaterialSlots('EDIT').map(item => item.required)).toEqual([true, false, false]);
    expect(cloudMaterialSlots('FL2V').map(item => item.required)).toEqual([true, true]);
    expect(cloudModelsFor('image')[0].capabilities).not.toContain('EDIT');
    expect(cloudModelsFor('video')[0].capabilities).not.toContain('FL2V');
  });
  it('各个模型能力的初始草稿使用有效输出组合且互相独立', () => {
    for (const media of ['video', 'image'] as const) {
      for (const model of cloudModelsFor(media)) {
        for (const capability of model.capabilities) {
          const first = createCloudDraft(model, capability);
          first.prompt = '测试草稿';
          expect(createCloudDraft(model, capability).prompt).toBe('');
          expect(cloudOutputOptions(model, capability).map(item => item.value)).toContain(first.output);
          if (media === 'video') expect(cloudDurations(model, capability, first.output)).toContain(first.duration);
        }
      }
    }
  });
});
