/**
 * 视频创作模块 ↔ ComfyUI 工作流契约（前端安全视图）
 *
 * 边界规则（AGENTS 章程硬约束）：
 *  - 前端只持有：能力编码、workflowCode、模型编码、字段 Schema 白名单、计费展示。
 *  - 节点 ID、API Format JSON、模型路径、大服务器地址只存在于后端，
 *    完整契约见 script/video/workflows/video-workflow-contracts.json（后端工件）。
 *  - 提交任务按此结构组装 payload：{ capabilityCode, workflowCode, modelCode, fields }，
 *    后端收到后深拷贝对应工作流模板，仅覆写 mapping_json 白名单内的节点输入键。
 *
 * 既有 H3 导演工作流由服务端发布状态控制。新增 20 个原生工作流的安全展示目录
 * 见 local-workflows.json；其 ComfyUI 验证成功不代替平台注册和发布验收。
 */

export type FieldKey =
  | 'reference1'
  | 'reference2'
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

/** 生成模型注册表（闭源按次积分 / 开源显示时长） */
export interface StudioModel {
  code: string;
  name: string;
  desc: string;
  version: string;
  license: 'closed' | 'open';
  recommended?: boolean;
}

/** 固定工具工作流（不消费生成底模，如补帧、对口型） */
export interface FixedWorkflow {
  code: string;
  name: string;
  version: string;
  eta: string;
}

export interface StudioModule {
  /** 能力编码，与后端契约 capabilityCode 一致 */
  code: string;
  name: string;
  desc: string;
  /** 字段 Schema 白名单：后端仅接受这些键并映射到工作流节点输入 */
  fields: FieldKey[];
  /** 该模块可用的生成模型（VIDEO_MODELS 的 code）；空数组 = 使用固定工作流 */
  models: string[];
  /** models 为空时的固定工作流绑定 */
  fixedWorkflow?: FixedWorkflow;
  defaultModel?: string;
  promptLabel?: string;
  placeholder?: string;
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
    name: '图生视频',
    desc: '单张图片 + 描述生成视频',
    fields: ['img', 'desc', 'tier', 'dur'],
    models: ['H3', 'WAN', 'HUN', 'LTX', 'KAND'],
    defaultModel: 'H3',
    promptLabel: '视频描述',
    placeholder: '例如：从产品特写缓缓拉远，镜头聚焦包装纹理，光影自然流动。'
  },
  {
    code: 'T2V',
    name: '文生视频',
    desc: '纯文字描述生成视频',
    fields: ['desc', 'tier', 'dur'],
    models: ['H3', 'WAN', 'HUN', 'LTX', 'KAND'],
    defaultModel: 'H3',
    promptLabel: '视频描述',
    placeholder: '例如：城市夜景延时，霓虹灯光汇聚成品牌 LOGO，大气收尾。'
  },
  {
    code: 'FL2V',
    name: '首尾帧生视频',
    desc: '首帧、尾帧 + 描述生成视频',
    fields: ['first', 'last', 'desc', 'tier', 'dur'],
    models: ['H3', 'WAN', 'HUN', 'LTX', 'KAND'],
    defaultModel: 'H3',
    promptLabel: '视频描述',
    placeholder: '例如：从产品特写切换至完整场景，镜头运动平滑自然。'
  },
  { code: 'R2V', name: '参考图生视频', desc: '两张参考图 + 描述生成音画视频',
    fields: ['reference1', 'reference2', 'desc', 'tier', 'dur'], models: ['H3'], defaultModel: 'H3',
    promptLabel: '视频描述', placeholder: '使用 <Picture 1> 和 <Picture 2> 描述参考图与镜头内容' }
];

export const VIDEO_MODELS: StudioModel[] = [
  {
    code: 'H3',
    name: 'MiniMax H3',
    desc: '工作流模板已导入',
    version: '8-step BF16',
    license: 'closed',
    recommended: true
  },
  { code: 'H3P', name: 'H3 Pro', desc: '闭源 · 高质感', version: 'v1.0.1', license: 'closed' },
  { code: 'WAN', name: 'Wan 2.2', desc: '开源 · 阿里通义', version: 'v0.2.0', license: 'open' },
  { code: 'HUN', name: 'HunyuanVideo 1.5', desc: '开源 · 腾讯混元', version: 'v0.1.0', license: 'open' },
  { code: 'LTX', name: 'LTX-2.5', desc: '开源 · 实时快出', version: 'v0.1.0', license: 'open' },
  { code: 'KAND', name: 'Kandinsky 5 Video Lite', desc: '本地轻量视频生成', version: 'Lite', license: 'open' },
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

/**
 * 由模块 + 模型推导 workflowCode。
 * 命名规则：wf-{module}-{model}（固定工作流直接取 fixedWorkflow.code）。
 */
export function resolveWorkflowCode(module: StudioModule, modelCode?: string): string {
  if (module.models.length === 0) return module.fixedWorkflow!.code;
  return `wf-${module.code.toLowerCase()}-${(modelCode ?? module.defaultModel ?? module.models[0])!.toLowerCase()}`;
}

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
    model: 'H3',
    prompt: '镜头从包装细节缓慢拉远，柔和轮廓光勾勒产品边缘，质感高级。',
    tone: 'cyan'
  },
  {
    title: '新品发布转场',
    module: 'FL2V',
    model: 'H3',
    prompt: '从产品细节平滑过渡到整体场景，主体保持稳定，光影层次自然。',
    tone: 'rose'
  }
];
