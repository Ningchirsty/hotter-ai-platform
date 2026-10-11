import type { LocalWorkflowOption } from '../LocalWorkflowPicker/types';
import type { CreativeAbility } from './types';

export interface LocalCreationChoice {
  key: string;
  name: string;
  desc: string;
  kind: 'purpose' | 'general';
  workflows: LocalWorkflowOption[];
}

export interface LocalCreationModel {
  code: string;
  name: string;
  family: string;
  choices: LocalCreationChoice[];
}

const families: Record<string, string> = {
  QWEN21: 'Qwen', QWEN2512: 'Qwen', ZIMAGE: 'Z-Image', FLUX4B: 'FLUX',
  KREA: 'Krea', IDEOGRAM: 'Ideogram', WAN: 'Wan', H3: 'MiniMax',
  LTX: 'LTX', HUN: 'Hunyuan', KAND: 'Kandinsky'
};

/** Every displayed choice comes from an executable binding for this model and media. */
export function localCreationModels(media: 'image' | 'video', abilities: CreativeAbility[],
  catalog: LocalWorkflowOption[], general: Array<{ code: string; name: string; desc: string }>): LocalCreationModel[] {
  const models = new Map<string, LocalCreationModel>();
  function add(workflow: LocalWorkflowOption, choice: Omit<LocalCreationChoice, 'workflows'>) {
    if (workflow.media !== media) return;
    let model = models.get(workflow.modelCode);
    if (!model) {
      model = { code: workflow.modelCode, name: workflow.modelName,
        family: families[workflow.modelCode] ?? workflow.modelName, choices: [] };
      models.set(model.code, model);
    }
    let entry = model.choices.find(item => item.key === choice.key);
    if (!entry) { entry = { ...choice, workflows: [] }; model.choices.push(entry); }
    if (!entry.workflows.some(item => item.workflowCode === workflow.workflowCode)) entry.workflows.push(workflow);
  }
  for (const ability of abilities) {
    if (ability.media !== media) continue;
    for (const workflow of ability.workflows) {
      if (workflow.capabilityCode !== ability.capabilityCode) continue;
      add(workflow, { key: 'purpose:' + ability.code, name: ability.name, desc: ability.desc, kind: 'purpose' });
    }
  }
  for (const workflow of catalog) {
    const capability = general.find(item => item.code === workflow.capabilityCode);
    if (capability) add(workflow, { key: 'general:' + capability.code, name: capability.name, desc: capability.desc, kind: 'general' });
  }
  const order = media === 'image'
    ? ['QWEN2512', 'QWEN21', 'ZIMAGE', 'FLUX4B', 'KREA', 'IDEOGRAM']
    : ['WAN', 'LTX', 'H3', 'HUN', 'KAND'];
  return [...order.flatMap(code => { const model = models.get(code); return model ? [model] : []; }),
    ...[...models.values()].filter(model => !order.includes(model.code))];
}

/** Keep the same purpose when supported; otherwise choose a real choice on the new model. */
export function localModelWorkflow(models: LocalCreationModel[], modelCode: string,
  currentCode: string, publishedCodes: string[] = []): string | undefined {
  const target = models.find(item => item.code === modelCode);
  if (!target) return undefined;
  const previous = models.flatMap(item => item.choices).find(item => item.workflows.some(w => w.workflowCode === currentCode));
  const choice = target.choices.find(item => item.key === previous?.key) ?? target.choices[0];
  if (!choice) return undefined;
  return choice.workflows.find(item => item.workflowCode === currentCode)?.workflowCode
    ?? choice.workflows.find(item => publishedCodes.includes(item.workflowCode))?.workflowCode
    ?? choice.workflows[0]?.workflowCode;
}
