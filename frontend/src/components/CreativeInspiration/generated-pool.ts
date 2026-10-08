import type { CloudImageModelsVO } from '@/api/image/types';
import type { GeneratedInspiration } from '@/api/image/inspiration';
import { imageCapabilityVerified } from './cloud-image-capabilities';
import type { ImageCloudCapability } from './cloud-image-capabilities';
import type { ImageInspirationWork, InspirationRoute, InspirationWorkflow } from './types';
import { isRouteAvailable } from './types';

/** 用真实描述归类，不将分类推断当成供应商返回元数据。 */
export function generatedCategory(prompt: string): string {
  const rules: [string, RegExp][] = [
    ['产品设计', /product|bottle|teapot|vase|产品|瓶|茶壶|花瓶/i],
    ['建筑设计', /architecture|building|interior|建筑|室内|空间/i],
    ['角色设计', /character|portrait|person|角色|人物|肖像/i],
    ['海报与广告', /poster|advertis|海报|广告/i],
    ['插画', /illustration|cartoon|插画|卡通/i]
  ];
  return rules.find(([, rule]) => rule.test(prompt))?.[0] ?? '风格与构图';
}
export function generatedWork(item: GeneratedInspiration): ImageInspirationWork {
  return {
    id: 'generated-' + item.id, media: 'image', title: item.title || item.prompt.slice(0, 32),
    category: generatedCategory(item.prompt), tags: [item.model, item.width && item.height ? `${item.width} × ${item.height}` : '真实云端作品'],
    cover: { x: 0, y: 0, width: 1, height: 1 }, assetId: item.assetId,
    provenance: item, routes: []
  };
}
/** 先选作品，再推荐真实已接入且通过验收的模型；来源型号优先。 */
export function generatedRoutes(work: ImageInspirationWork, status: CloudImageModelsVO | undefined,
                                workflows: InspirationWorkflow[]): InspirationRoute[] {
  if (!work.provenance) return [];
  const item = work.provenance;
  const routes: InspirationRoute[] = [];
  const models = status?.configured ? status.models.filter(model => model !== 'flux-2-pro') : [];
  models.sort((a, b) => Number(b === item.model) - Number(a === item.model));
  for (const model of models) {
    const mode = imageCapabilityVerified(status, model, item.capability) ? item.capability
      : imageCapabilityVerified(status, model, 'T2I') ? 'T2I' : '';
    if (!mode) continue;
    routes.push({ source: 'cloud', media: 'image', model, capability: mode as ImageCloudCapability,
      workflowCode: 'cloud-bluocto-t2i', prompt: item.prompt,
      reason: model === item.model ? '此作品的实际生成模型，可参考原创作描述继续创作。' : '此模型已通过相应能力验收，可尝试相近的创作方向。',
      referenceHint: ['EDIT', 'MULTI', 'MASK', 'OUTPAINT'].includes(mode)
        ? '创作描述将带入；请按所选能力补充参考图或蒙版，确认后再提交。' : '创作描述将带入；可继续修改尺寸与内容，确认后再提交。'
    });
  }
  for (const flow of workflows.filter(flow => flow.capabilityCode === 'T2I')) {
    const route: InspirationRoute = { source: 'local', media: 'image', model: flow.modelCode || flow.workflowCode,
      capability: 'T2I', workflowCode: flow.workflowCode, prompt: item.prompt,
      reason: '已发布的本地文生图工作流，可参考相同主题、构图和风格。', referenceHint: '带入描述后仍通过现有本地工作流执行。' };
    if (isRouteAvailable(route, workflows)) routes.push(route);
  }
  return routes;
}
