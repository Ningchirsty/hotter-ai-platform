export type FieldKey =
  | 'first'
  | 'last'
  | 'frames'
  | 'img'
  | 'source'
  | 'audio'
  | 'desc'
  | 'tier'
  | 'dur'
  | 'move'
  | 'extend'
  | 'target'
  | 'fps';

export interface StudioModule {
  code: string;
  name: string;
  desc: string;
  version: string;
  fields: FieldKey[];
  promptLabel?: string;
  placeholder?: string;
}

export interface StudioModel {
  code: string;
  name: string;
  desc: string;
  version: string;
  license: 'closed' | 'open';
  recommended?: boolean;
}

export interface Inspiration {
  title: string;
  module: string;
  model: string;
  prompt: string;
  tone: string;
}

export const VIDEO_MODULES: StudioModule[] = [
  {
    code: 'I2V',
    name: '首尾帧生视频',
    desc: '首尾帧 + 描述生成视频',
    version: '多底模',
    fields: ['first', 'last', 'desc', 'tier', 'dur'],
    promptLabel: '视频描述',
    placeholder: '例如：从产品特写缓缓拉远，镜头聚焦包装纹理，光影自然流动。'
  },
  {
    code: 'T2V',
    name: '文生视频',
    desc: '纯文字描述生成视频',
    version: '多底模',
    fields: ['desc', 'tier', 'dur'],
    promptLabel: '视频描述',
    placeholder: '例如：城市夜景延时，霓虹灯光汇聚成品牌 LOGO，大气收尾。'
  },
  {
    code: 'MFRAME',
    name: '智能多帧',
    desc: '2-10 张关键帧生成长视频',
    version: '多底模',
    fields: ['frames', 'desc', 'dur'],
    promptLabel: '视频描述',
    placeholder: '例如：产品多角度连续展示，镜头平滑衔接。'
  },
  {
    code: 'CAMMOVE',
    name: '运镜视频',
    desc: '图片 + 推拉摇移环绕运镜',
    version: '多底模',
    fields: ['img', 'move', 'desc', 'dur'],
    promptLabel: '补充描述（可选）',
    placeholder: '例如：夜色中的门店门头，灯光渐亮。'
  },
  {
    code: 'VEXT',
    name: '视频续写',
    desc: '选成片延长 · 最长 2 分钟',
    version: '多底模',
    fields: ['source', 'extend', 'desc'],
    promptLabel: '续写描述（可选）',
    placeholder: '例如：镜头继续拉远，露出城市天际线。'
  },
  {
    code: 'VHD',
    name: '补帧高清化',
    desc: '成片补帧 · 升级 1080P/4K',
    version: '多底模',
    fields: ['source', 'target', 'fps']
  },
  {
    code: 'LIP',
    name: '对口型',
    desc: '音频驱动口型 · 数字人',
    version: '多底模',
    fields: ['source', 'audio']
  }
];

export const VIDEO_MODELS: StudioModel[] = [
  { code: 'H3', name: 'MiniMax H3', desc: '闭源 · 标准快', version: 'v1.0.2', license: 'closed', recommended: true },
  { code: 'H3P', name: 'H3 Pro', desc: '闭源 · 高质感', version: 'v1.0.1', license: 'closed' },
  { code: 'WAN', name: 'WAN 2.1', desc: '开源 · 阿里通义', version: 'v0.2.0', license: 'open' },
  { code: 'HUN', name: 'HunyuanVideo', desc: '开源 · 腾讯混元', version: 'v0.1.0', license: 'open' },
  { code: 'LTX', name: 'LTX-Video', desc: '开源 · 实时快出', version: 'v0.1.0', license: 'open' },
  { code: 'COG', name: 'CogVideoX', desc: '开源 · 智谱', version: 'v0.1.0', license: 'open' }
];

export const MODEL_COSTS: Record<string, number> = { H3: 10, H3P: 15 };
export const MODEL_ETAS: Record<string, string> = {
  WAN: '约 4 分钟',
  HUN: '约 8 分钟',
  LTX: '约 2 分钟',
  COG: '约 6 分钟'
};

export const PROMPT_CHIPS = ['镜头缓慢推进', '自然光流动', '电影级质感', '平滑连续运动'];
export const COMPLETED_VIDEOS = ['新品发布主视频', '品牌 LOGO 动效', '门店氛围短片'];

export const INSPIRATIONS: Inspiration[] = [
  {
    title: '霓虹城市夜景',
    module: 'T2V',
    model: 'H3',
    prompt: '城市夜景延时，霓虹灯光沿街道流动并汇聚成品牌标志，电影级质感。',
    tone: 'violet'
  },
  {
    title: '新品包装特写',
    module: 'I2V',
    model: 'H3P',
    prompt: '镜头从包装细节缓慢拉远，柔和轮廓光勾勒产品边缘，质感高级。',
    tone: 'cyan'
  },
  {
    title: '门店空间巡游',
    module: 'CAMMOVE',
    model: 'WAN',
    prompt: '镜头平稳穿过门店空间，暖色灯光依次点亮，最终停留在品牌墙。',
    tone: 'rose'
  }
];
