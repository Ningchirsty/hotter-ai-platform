import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import image from '../../views/image/abilities.json';
import video from '../../views/video/abilities.json';
import { abilityDefaults, abilityRequest, compileAbility, type CreativeAbility } from './types';
import { localCreationModels, localModelWorkflow } from './model-selection';
import type { LocalWorkflowOption } from '../LocalWorkflowPicker/types';
import imageCatalogData from '../../views/image/local-workflows.json';
import videoCatalogData from '../../views/video/local-workflows.json';
import { IMAGE_MODULES } from '../../views/image/modules';
import { VIDEO_MODULES } from '../../views/video/modules';

describe('业务用途与后端 ComfyUI 契约', () => {
  it('前后端用途契约一致，8 个图像 / 6 个视频，28 个专用模板均有有效映射与哈希', () => {
    expect(image).toHaveLength(8); expect(video).toHaveLength(6);
    let count = 0;
    for (const [media, abilities] of [['image', image], ['video', video]] as const) {
      const server = JSON.parse(readFileSync(new URL(`../../../../script/${media}/workflows/abilities.json`, import.meta.url), 'utf8'));
      expect(abilities).toEqual(server);
      const contract = JSON.parse(readFileSync(new URL(`../../../../script/${media}/workflows/native-workflow-contracts.json`, import.meta.url), 'utf8'));
      for (const ability of abilities) for (const workflow of ability.workflows) {
        count++;
        expect(ability.media).toBe(media); expect(workflow.media).toBe(media);
        expect(workflow.capabilityCode).toBe(ability.capabilityCode);
        const version = contract.capabilities.flatMap((c: any) => c.workflows).find((w: any) => w.workflowCode === workflow.workflowCode);
        expect(version.status).toBe('DRAFT');
        const bytes = readFileSync(new URL(`../../../../script/${version.apiJsonFile}`, import.meta.url));
        expect(createHash('sha256').update(bytes).digest('hex')).toBe(version.checksum);
        const graph = JSON.parse(bytes.toString());
        for (const mapping of version.mapping) expect(graph[mapping.nodeId].inputs).toHaveProperty(mapping.inputKey);
      }
    }
    expect(count).toBe(28);
    expect(JSON.stringify([...image, ...video])).not.toMatch(/class_type|nodeId|safetensors|192\.168\.|apiJsonFile/);
  });

  it('标题、文案与版式进入海报描述，不允许缺少必填项或跨媒体提交', () => {
    const poster = image[0] as unknown as CreativeAbility;
    const inputs: Record<string, string> = { ...abilityDefaults(poster), title: '春日新品', copy: '把自然带进生活', visual: '青色茶壶' };
    const compiled = compileAbility(poster, inputs);
    expect(compiled.errors).toEqual([]);
    expect(compiled.prompt).toContain('春日新品'); expect(compiled.prompt).toContain('把自然带进生活');
    expect(compiled.prompt).toContain(inputs.layout);
    expect(compileAbility(poster, abilityDefaults(poster)).errors.length).toBeGreaterThan(0);
    expect(() => abilityRequest(poster, 'video', poster.recommendedWorkflowCode, inputs, {}, {}, 'test')).toThrow();
    expect(() => abilityRequest(poster, 'image', 'wf-local-image-z-image-turbo', inputs, {}, {}, 'test')).toThrow();
  });

  it('专用请求只带声明的输入、素材和输出，丢弃另一用途残留字段', () => {
    const motion = video[0] as unknown as CreativeAbility;
    const inputs = { ...abilityDefaults(motion), action: '蒸汽升起', title: '不能进入视频' };
    const request = abilityRequest(motion, 'video', motion.recommendedWorkflowCode, inputs,
      { img: '123', image1: '456', reference1: '789' }, { tier: '档位', dur: '2 秒', size: '图片尺寸', nodeId: '5' }, 'test');
    expect(request.inputs).not.toHaveProperty('title');
    expect(request.assets).toEqual({ img: '123' }); expect(request.output).toEqual({ tier: '档位', dur: '2 秒' });
    expect(() => abilityRequest(motion, 'video', motion.recommendedWorkflowCode, inputs, {}, {}, 'test')).toThrow('请上传');
  });
});

describe('模型优先的本地创作选择', () => {
  const imageCatalog = imageCatalogData as LocalWorkflowOption[];
  const videoCatalog = videoCatalogData as LocalWorkflowOption[];
  const imageAbilities = image as unknown as CreativeAbility[];
  const videoAbilities = video as unknown as CreativeAbility[];
  const images = localCreationModels('image', imageAbilities, imageCatalog, IMAGE_MODULES);
  const videos = localCreationModels('video', videoAbilities, videoCatalog, VIDEO_MODULES);

  it('每个模型只展示真实绑定能力，28 个用途与 31 个通用工作流均保留', () => {
    expect(images).toHaveLength(6); expect(videos).toHaveLength(5);
    const purposeCodes: string[] = [];
    for (const [media, models, catalog] of [['image', images, imageCatalog], ['video', videos, videoCatalog]] as const) {
      const generalCodes: string[] = [];
      for (const model of models) for (const choice of model.choices) for (const workflow of choice.workflows) {
        expect(workflow.media).toBe(media); expect(workflow.modelCode).toBe(model.code);
        (choice.kind === 'purpose' ? purposeCodes : generalCodes).push(workflow.workflowCode);
      }
      expect(generalCodes.toSorted()).toEqual(catalog.map(item => item.workflowCode).toSorted());
    }
    expect(new Set(purposeCodes).size).toBe(28);
    expect(images.find(item => item.code === 'ZIMAGE')!.choices.map(item => item.key)).not.toContain('purpose:CUTOUT');
    expect(videos.find(item => item.code === 'WAN')!.choices.map(item => item.key)).not.toContain('purpose:SOUND_STORY');
    expect(videos.find(item => item.code === 'LTX')!.choices.map(item => item.key)).toContain('purpose:SOUND_STORY');
  });

  it('切换模型保留它支持的用途，不支持时回到该模型实际能力', () => {
    const poster = imageAbilities.find(item => item.code === 'POSTER')!;
    const qwen21 = poster.workflows.find(item => item.modelCode === 'QWEN21')!;
    expect(localModelWorkflow(images, 'QWEN21', poster.recommendedWorkflowCode)).toBe(qwen21.workflowCode);
    const zimage = localModelWorkflow(images, 'ZIMAGE', poster.recommendedWorkflowCode);
    expect(imageAbilities.find(item => item.code === 'QUICK_IMAGE')!.workflows.map(item => item.workflowCode)).toContain(zimage);
    expect(localModelWorkflow(images, 'WAN', poster.recommendedWorkflowCode)).toBeUndefined();
  });

  it('保留已发布的旧版通用工作流，并拒绝混入另一媒体的目录', () => {
    const legacy = { ...imageCatalog[0], workflowCode: 'wf-t2i-qwen21', verifiedAt: undefined, version: '' };
    const models = localCreationModels('image', [...imageAbilities, ...videoAbilities], [...imageCatalog, ...videoCatalog, legacy], IMAGE_MODULES);
    expect(models).toHaveLength(6);
    expect(localModelWorkflow(models, 'QWEN21', 'wf-local-image-z-image-turbo', ['wf-t2i-qwen21'])).toBe('wf-t2i-qwen21');
    expect(localModelWorkflow(models, 'QWEN21', 'wf-t2i-qwen21', ['wf-t2i-qwen21'])).toBe('wf-t2i-qwen21');
    const invalidAbility = { ...imageAbilities[0], workflows: [videoAbilities[0].workflows[0]] };
    expect(localCreationModels('image', [invalidAbility], [], IMAGE_MODULES)).toEqual([]);
  });
});
