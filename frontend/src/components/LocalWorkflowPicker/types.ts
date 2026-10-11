/** Public display metadata only. Executable graphs and node mappings stay in script/. */
export interface LocalWorkflowOption {
  workflowCode: string;
  media: 'image' | 'video';
  capabilityCode: string;
  modelCode: string;
  modelName: string;
  name: string;
  version: string;
  fields: string[];
  verifiedAt?: string;
  hasAudio?: boolean;
  fps?: number;
  sizes?: Array<{ label: string; width: number; height: number }>;
  supportedTiers?: string[];
}

export interface RegisteredWorkflow {
  workflowCode: string;
  capabilityCode: string;
  modelCode?: string | null;
  version: string;
  status: string;
  submittable: boolean;
}

/** An image response cannot authorize a video selection (or vice versa). */
export function matchRegisteredWorkflow<T extends RegisteredWorkflow>(
  media: 'image' | 'video', option: LocalWorkflowOption | undefined, registry: T[]
): T | undefined {
  if (!option || option.media !== media) return undefined;
  return registry.find(row => row.workflowCode === option.workflowCode
    && row.capabilityCode === option.capabilityCode
    && (option.verifiedAt ? row.modelCode === option.modelCode : !row.modelCode || row.modelCode === option.modelCode)
    && (!option.verifiedAt || row.version === option.version));
}

export function workflowFields(option: LocalWorkflowOption, values: Record<string, unknown>) {
  return Object.fromEntries(option.fields.filter(key => values[key] !== undefined).map(key => [key, values[key]]));
}
