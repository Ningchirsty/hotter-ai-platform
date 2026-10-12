import type { CloudImageModelsVO, CloudImageOutputParams } from '@/api/image/types';
import { outputDefaults, outputFields, outputParameterVerified, outputValue } from './cloud-image-output';
import { imageCapabilityVerified } from './cloud-image-capabilities';

export function templateCreatePlan(status: CloudImageModelsVO | undefined, template?: { model?: string; outputSize: string }) {
  const model = template?.model || '';
  const result = adaptTemplateOutput(status, model, 'T2I', { size: template?.outputSize, n: 1, quality: 'high' });
  return { ...result, available: Boolean(status?.configured && status.models.includes(model) && imageCapabilityVerified(status, model, 'T2I')), label: `${result.output.size || '自动尺寸'} · ${result.output.n || 1} 张 · ${(result.output.outputFormat || '自动格式').toUpperCase()}` };
}

/** Preserve supported template settings; adapt only fields the selected model has not verified. */
export function adaptTemplateOutput(status: CloudImageModelsVO | undefined, model: string, capability: string, requested: CloudImageOutputParams) {
  const output = { ...outputDefaults(status, model, capability) };
  const adapted: string[] = [];
  for (const field of outputFields) {
    const value = requested[field.key];
    if (value == null) continue;
    if (outputParameterVerified(status, model, capability, field.key, outputValue(requested, field.key))) {
      Object.assign(output, { [field.key]: value });
    } else adapted.push(field.label);
  }
  return { output, note: adapted.length ? `${adapted.join('、')}已适配为当前模型的可用配置，请确认输出设置。` : '' };
}

export function creationMessage(value?: string | null) {
  return (value || '').replace(/https?:\/\/[^\s]*bluocto[^\s]*/gi, '云端服务')
    .replace(/蓝章鱼|BluOcto|供应商/gi, '云端服务')
    .replace(/API\s*Key/gi, '服务凭据').replace(/API/gi, '服务')
    .replace(/接口/g, '服务').replace(/ComfyUI/gi, '生成服务').replace(/wf-[a-z0-9_-]+/gi, '创作能力');
}

export function creationTitle(value?: string | null) { return creationMessage(value).replace(/\s*·\s*创作能力/g, '').trim(); }
export function taskModelLabel(code: string, model: string | undefined, catalog: Array<{workflowCode: string; modelCode: string; modelName: string}>) {
  return catalog.find(item => item.workflowCode === code)?.modelName || catalog.find(item => item.modelCode === model)?.modelName || creationTitle(model) || '本地生成';
}
