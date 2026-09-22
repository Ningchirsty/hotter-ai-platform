/**
 * 图像创作模块 ↔ ComfyUI 工作流契约（前端安全视图）
 *
 * 边界规则（与视频模块同一章程）：
 *  - 前端只持有：能力编码、workflowCode、字段 Schema 白名单、档位选项、计费展示。
 *  - 节点 ID、API Format JSON、模型路径只存在于后端，
 *    完整契约见 script/image/workflows/image-workflow-contracts.json（后端工件）。
 *  - 提交任务按 `{ capabilityCode, workflowCode, fields }` 组装 payload；
 *    后端收到后深拷贝对应模板，仅覆写 mapping_json 白名单内的节点输入键。
 *
 * 状态说明：五份 Qwen-Image-2.1 模板已在 ComfyUI 0.37.0 真机验证出图
 * （见 api/_validation-live.json），并已在生产环境完成端到端验收，业务已批准发布，
 * 故契约条目与 meta 均为 PUBLISHED。发布状态的**权威在数据库**（image_workflow_version
 * 的人工审核结果，启动时叠加到内存绑定，只提不降），契约里的 status 只是基线 ——
 * 因此换镜像不会再把「已发布」退回成「暂不可提交」。
 */

import type { ImageCapabilityCode } from '@/api/image/types';

/** 前端允许出现在 fields 里的键（必须与契约的能力字段一致）。 */
export type ImageFieldKey =
  | 'prompt'
  | 'negative_prompt'
  | 'size'
  | 'strength'
  | 'img'
  | 'image1'
  | 'image2'
  | 'image3';

/** 一个能力的展示定义。 */
export interface ImageCapabilityModule {
  code: ImageCapabilityCode;
  name: string;
  desc: string;
  /** 契约里的 workflowCode（后端注册后返回同名条目）。 */
  workflowCode: string;
  /** 该能力允许提交的字段。 */
  fields: ImageFieldKey[];
  /** 单图能力使用的字段。 */
  imageField?: ImageFieldKey;
  /** 多图能力使用的字段（按槽位顺序，第一个是编辑目标）。 */
  imageFields?: ImageFieldKey[];
  promptLabel?: string;
  placeholder?: string;
  tips: string[];
}

/** 五个能力：与契约 capabilities 一一对应。 */
export const IMAGE_MODULES: ImageCapabilityModule[] = [
  {
    code: 'T2I',
    name: '文生图',
    desc: '只用提示词生成图片，可选 1MP / 2K 比例档位',
    workflowCode: 'wf-t2i-qwen21',
    fields: ['prompt', 'negative_prompt', 'size'],
    promptLabel: '画面描述',
    placeholder: '例如：一只红色陶瓷茶壶放在木桌上，柔和影棚光，浅景深',
    tips: ['原生支持 2K 直接输出', 'cfg 固定为 1，负向提示词通常留空']
  },
  {
    code: 'I2I',
    name: '图生图',
    desc: '上传一张图，按提示词重绘，重绘幅度可调',
    workflowCode: 'wf-i2i-qwen21',
    fields: ['img', 'prompt', 'negative_prompt', 'strength'],
    imageField: 'img',
    promptLabel: '改图描述',
    placeholder: '例如：把画面变成柔和的水彩插画风格',
    tips: [
      '输出尺寸跟随输入图',
      '重绘幅度越大越偏离原图：0.4 轻微 / 0.75 标准 / 0.9 强烈',
      '注意：图生图是「整体重绘」，模型看不到参考图，只能改风格与氛围，不能替换背景（写「换成纯白背景」也没用，背景会被原样保留；调高幅度则会把主体一起重画坏）',
      '要替换背景：用「指令改图」；要纯白底电商图：用「白底图」'
    ]
  },
  {
    code: 'EDIT',
    name: '指令改图',
    desc: '用指令修改原图，可再带 2 张参考图（换装、换背景等）',
    workflowCode: 'wf-edit-qwen21',
    fields: ['image1', 'image2', 'image3', 'prompt', 'negative_prompt'],
    imageFields: ['image1', 'image2', 'image3'],
    promptLabel: '编辑指令',
    placeholder: '例如：只把 <image1> 的背景替换成纯白色无缝背景，产品本身保持不变',
    tips: [
      'prompt 里用 <image1>、<image2> 引用参考图，不写占位符参考图基本不生效',
      'image1 是编辑目标，决定输出画布尺寸',
      '换背景/换装等「改某一部分」的诉求走这里；模型会重画细节，产品需要像素级不变时请用「白底图」'
    ]
  },
  {
    code: 'BGREMOVE',
    name: '抠图去背景',
    desc: '固定提示词，直接输出透明背景 PNG（RGBA）',
    workflowCode: 'wf-bgremove-qwen21',
    fields: ['img'],
    imageField: 'img',
    tips: ['提示词由服务端固定，无需填写', '输出带 alpha 通道，保存为 PNG 即可']
  },
  {
    code: 'WHITEBG',
    name: '白底图',
    desc: '抠图后合成纯白底（255,255,255），产品像素级不变',
    workflowCode: 'wf-whitebg-qwen21',
    fields: ['img'],
    imageField: 'img',
    tips: [
      '提示词由服务端固定，无需填写',
      '背景为程序合成的纯白（不是模型画的），因此产品不会被重绘：贴花、文字、质感全部原样保留',
      '适合电商主图/详情页白底图；需要保留透明通道请用「抠图去背景」'
    ]
  }
];

/** 灵感示例（纯展示，点击后填入对应能力的提示词）。 */
export interface ImageInspiration {
  title: string;
  capability: ImageCapabilityCode;
  prompt: string;
}

export const INSPIRATIONS: ImageInspiration[] = [
  {
    title: '产品静物',
    capability: 'T2I',
    prompt: '极简产品摄影：白色陶瓷杯放在浅灰背景前，柔和侧光，干净留白，商业质感'
  },
  {
    title: '海报排版',
    capability: 'T2I',
    prompt: '复古旅行海报，构图中央是山峰剪影，顶部有粗体标题文字，颗粒感印刷质感'
  },
  {
    title: '水彩转换',
    capability: 'I2I',
    prompt: '整体转成淡雅水彩插画，保留原构图与主体位置，纸张纹理'
  },
  {
    title: '换装编辑',
    capability: 'EDIT',
    prompt: '保持 <image1> 的人物、姿态与背景不变，把 <image2> 的衣服穿到角色身上，保留面部特征'
  }
];

/** 按能力找模块定义。 */
export function moduleOf(code: string): ImageCapabilityModule | undefined {
  return IMAGE_MODULES.find((item) => item.code === code);
}
