import type { CreativeMedia } from './types';

export type CloudCapability = 'T2V' | 'I2V' | 'FL2V' | 'R2V' | 'EXTEND' | 'T2I' | 'EDIT';
export interface CloudModel {
  id: string;
  name: string;
  provider: string;
  media: CreativeMedia;
  description: string;
  capabilities: CloudCapability[];
  status: 'PENDING';
  docs: string;
}
export const CLOUD_MODELS: CloudModel[] = [
  {
    id: 'MiniMax-Hailuo-2.3',
    name: 'Hailuo 2.3',
    provider: 'MiniMax',
    media: 'video',
    description: '文字或首帧驱动的视频创作',
    capabilities: ['T2V', 'I2V'],
    status: 'PENDING',
    docs: 'https://github.com/MiniMax-AI/skills/blob/main/skills/frontend-dev/references/minimax-video-guide.md'
  },
  {
    id: 'veo-3.1-generate-preview',
    name: 'Veo 3.1',
    provider: 'Google',
    media: 'video',
    description: '首尾帧、多图参考与原生音频',
    capabilities: ['T2V', 'I2V', 'FL2V', 'R2V', 'EXTEND'],
    status: 'PENDING',
    docs: 'https://ai.google.dev/gemini-api/docs/veo'
  },
  {
    id: 'qwen-image-plus',
    name: 'Qwen-Image Plus',
    provider: '阿里云百炼',
    media: 'image',
    description: '文字排版、海报与多样艺术风格',
    capabilities: ['T2I'],
    status: 'PENDING',
    docs: 'https://help.aliyun.com/zh/model-studio/qwen-image-api'
  },
  {
    id: 'qwen-image-edit-plus',
    name: 'Qwen-Image Edit Plus',
    provider: '阿里云百炼',
    media: 'image',
    description: '指令编辑、单图修改与多图融合',
    capabilities: ['EDIT'],
    status: 'PENDING',
    docs: 'https://help.aliyun.com/zh/model-studio/qwen-image-edit-api'
  }
];
export const CLOUD_CAPABILITIES: Record<CloudCapability, { name: string; description: string }> = {
  T2V: { name: '文生视频', description: '用文字描述场景与镜头' },
  I2V: { name: '图生视频', description: '以首帧图片驱动运动' },
  FL2V: { name: '首尾帧视频', description: '指定镜头的开始与结束' },
  R2V: { name: '多图参考视频', description: '用参考图保持主体方向' },
  EXTEND: { name: '视频续写', description: '延续该模型已生成的视频' },
  T2I: { name: '文生图像', description: '文字描述画面与设计' },
  EDIT: { name: '图像编辑', description: '修改画面或融合多图' }
};
export interface CloudMaterialSlot {
  key: string;
  label: string;
  required: boolean;
}
export function cloudModelsFor(media: CreativeMedia): CloudModel[] {
  return CLOUD_MODELS.filter(model => model.media === media);
}
export function cloudMaterialSlots(capability: CloudCapability): CloudMaterialSlot[] {
  if (capability === 'I2V') return [{ key: 'first', label: '首帧图片', required: true }];
  if (capability === 'FL2V')
    return [
      { key: 'first', label: '首帧图片', required: true },
      { key: 'last', label: '尾帧图片', required: true }
    ];
  if (capability === 'R2V' || capability === 'EDIT')
    return [
      { key: 'image1', label: capability === 'EDIT' ? '待编辑图片' : '参考图片 1', required: true },
      { key: 'image2', label: '参考图片 2', required: false },
      { key: 'image3', label: '参考图片 3', required: false }
    ];
  return [];
}
export interface CloudOutputOption {
  value: string;
  label: string;
}
export function cloudOutputOptions(model: CloudModel, capability: CloudCapability): CloudOutputOption[] {
  if (model.id === 'MiniMax-Hailuo-2.3') return ['768P', '1080P'].map(value => ({ value, label: value }));
  if (model.media === 'video')
    return (capability === 'EXTEND' ? ['720p'] : ['720p', '1080p', '4k']).map(value => ({
      value,
      label: value.toUpperCase()
    }));
  return model.id === 'qwen-image-plus'
    ? [
        ['1664*928', '16:9 · 1664 × 928'],
        ['928*1664', '9:16 · 928 × 1664'],
        ['1328*1328', '1:1 · 1328 × 1328'],
        ['1472*1104', '4:3 · 1472 × 1104'],
        ['1104*1472', '3:4 · 1104 × 1472']
      ].map(([value, label]) => ({ value, label }))
    : [
        ['auto', '跟随输入图片'],
        ['1024*1024', '1:1 · 1024 × 1024'],
        ['1920*1080', '16:9 · 1920 × 1080'],
        ['1080*1920', '9:16 · 1080 × 1920']
      ].map(([value, label]) => ({ value, label }));
}
export function cloudDurations(model: CloudModel, capability: CloudCapability, output: string): number[] {
  if (model.media !== 'video') return [];
  if (model.id === 'MiniMax-Hailuo-2.3') return output === '1080P' ? [6] : [6, 10];
  return capability === 'R2V' || capability === 'EXTEND' || output !== '720p' ? [8] : [4, 6, 8];
}
export interface CloudDraft {
  prompt: string;
  negativePrompt: string;
  output: string;
  duration: number;
  ratio: string;
  count: number;
  optimize: boolean;
  watermark: boolean;
  seed: string;
  sourceTask: string;
}
export function createCloudDraft(model: CloudModel, capability: CloudCapability): CloudDraft {
  const output = cloudOutputOptions(model, capability)[0].value;
  return {
    prompt: '',
    negativePrompt: '',
    output,
    duration: cloudDurations(model, capability, output)[0] ?? 0,
    ratio: '16:9',
    count: 1,
    optimize: true,
    watermark: false,
    seed: '',
    sourceTask: ''
  };
}
