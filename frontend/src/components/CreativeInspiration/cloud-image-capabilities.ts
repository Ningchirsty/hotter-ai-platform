import type { CloudImageModelsVO } from '@/api/image/types';
export const IMAGE_CLOUD_CAPABILITIES = [
  { code: 'T2I', name: '文生图', description: '用文字描述生成画面', prompt: '描述主体、构图、光线、材质和风格…' },
  { code: 'EDIT', name: '参考图编辑', description: '换背景、换风格、修改主体细节', prompt: '描述希望如何修改参考图，以及哪些细节需要保留…' },
  { code: 'MULTI', name: '多图融合', description: '组合主体、场景与风格参考', prompt: '说明每张参考图的用途，以及如何组合成一张图…' },
  { code: 'MASK', name: '局部重绘', description: '使用蒙版指定需要修改的区域', prompt: '描述蒙版区域内需要修改的内容…' },
  { code: 'OUTPAINT', name: '画面扩展', description: '向四周延伸画面，补全场景', prompt: '描述原图四周需要延续的场景、光线和构图…' },
  { code: 'TRANSPARENT', name: '透明背景', description: '生成透明 PNG 素材', prompt: '描述需要生成的独立主体，例如产品、图标或装饰素材…' }
] as const;
export type ImageCloudCapability = typeof IMAGE_CLOUD_CAPABILITIES[number]['code'];
export function imageCapabilityVerified(status: CloudImageModelsVO | undefined, model: string, code: string): boolean {
  return status?.profiles?.find(p => p.model === model)?.capabilities.some(c => c.code === code && c.verified) === true;
}
export function cloudImageCapabilityName(code: string): string {
  return IMAGE_CLOUD_CAPABILITIES.find(c => c.code === code)?.name ?? code;
}

/** 已对接的能力范围；能否提交仍由服务端逐项验收状态决定。 */
export function imageCapabilitiesForModel(model: string) {
  if (['gpt-image-2.5-flare','gpt-image-2.5-sunburst'].includes(model)) return [...IMAGE_CLOUD_CAPABILITIES];
  if (['qwen-image-3.0','qwen-image-3.0-pro','wan2.7-image','wan2.7-image-pro'].includes(model)) return IMAGE_CLOUD_CAPABILITIES.slice(0,3);
  return [];
}
export function imageCapabilityStatusLabel(status?: string): string {
  if (status === 'PASSED') return '已验证';
  if (status === 'RESULT_UNKNOWN') return '结果未确认 · 暂不可用';
  if (status === 'HTTP_504') return '供应商超时 · 暂不可用';
  if (status === 'OUTPUT_UNVERIFIED') return '图片验收待完成';
  if (status === 'CAPABILITY_OUTPUT_MISMATCH') return '输出不符合要求';
  if (status?.startsWith('HTTP_')) return `接口 ${status.slice(5)} · 暂不可用`;
  return '待验证 · 可预览';
}
