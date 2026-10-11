import type { LocalWorkflowOption } from '../LocalWorkflowPicker/types';

export interface CreativeAbility {
  code: string;
  media: 'image' | 'video';
  name: string;
  desc: string;
  capabilityCode: string;
  inputs: Array<{ key: string; label: string; required: boolean; maxLength: number; placeholder?: string; options?: string[] }>;
  assets: Array<{ key: string; label: string; required: boolean }>;
  promptTemplate: string;
  note: string;
  acceptance: string[];
  recommendedWorkflowCode: string;
  workflows: Array<LocalWorkflowOption & { abilityCode: string; sourceWorkflowCode: string }>;
}

export function abilityDefaults(ability: CreativeAbility): Record<string, string> {
  return Object.fromEntries(ability.inputs.map(field => [field.key, field.options?.[0] ?? '']));
}

/** Preview only. The backend independently validates and compiles the same contract. */
export function compileAbility(ability: CreativeAbility, inputs: Record<string, string>) {
  const errors: string[] = [];
  const clean: Record<string, string> = {};
  for (const field of ability.inputs) {
    const value = (inputs[field.key] ?? '').trim();
    if (field.required && !value) errors.push(`请填写${field.label}`);
    if (value.length > field.maxLength) errors.push(`${field.label}最多 ${field.maxLength} 字`);
    if (field.options && value && !field.options.includes(value)) errors.push(`${field.label}选项无效`);
    clean[field.key] = value;
  }
  const prompt = ability.promptTemplate.replace(/\$\{([a-zA-Z][a-zA-Z0-9_]*)}/g, (_, key: string) => clean[key] ?? '');
  if (prompt.length > 1000) errors.push('组合后的创作描述超过 1000 字，请缩短输入');
  return { inputs: clean, prompt, errors };
}

export function abilityRequest(ability: CreativeAbility, media: 'image' | 'video', workflowCode: string,
  inputs: Record<string, string>, assets: Record<string, unknown>, output: Record<string, unknown>, idempotencyKey: string) {
  if (ability.media !== media || !ability.workflows.some(w => w.media === media && w.workflowCode === workflowCode)) throw new Error('用途与工作流不匹配');
  const compiled = compileAbility(ability, inputs);
  if (compiled.errors.length) throw new Error(compiled.errors[0]);
  const binding = ability.workflows.find(w => w.workflowCode === workflowCode)!;
  const cleanAssets = Object.fromEntries(ability.assets.filter(f => assets[f.key] !== undefined).map(f => [f.key, assets[f.key]]));
  for (const field of ability.assets) if (field.required && cleanAssets[field.key] === undefined) throw new Error(`请上传${field.label}`);
  const cleanOutput = Object.fromEntries(binding.fields.filter(key => ['size', 'strength', 'tier', 'dur'].includes(key) && output[key] !== undefined).map(key => [key, output[key]]));
  return { abilityCode: ability.code, workflowCode, inputs: compiled.inputs, assets: cleanAssets, output: cleanOutput, idempotencyKey };
}
