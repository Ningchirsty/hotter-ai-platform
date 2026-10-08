import type { CloudImageModelsVO, CloudImageOutputParams } from '@/api/image/types';
export type OutputKey = keyof CloudImageOutputParams;
export const outputFields: { key: OutputKey; label: string }[] = [{key:'size',label:'图像尺寸'},{key:'n',label:'生成数量'},{key:'quality',label:'画面质量'},{key:'outputFormat',label:'输出格式'}];
export function outputValue(output:CloudImageOutputParams,key:OutputKey):string { return String(output[key] ?? (key === 'n' ? 1 : '')); }
export function outputCandidates(model:string,capability:string,key:OutputKey):string[] {
  const gpt=model.startsWith('gpt-');
  if(key === 'n') return ['1','2'];
  if(key === 'size') return ['', '1024x1024','1536x1024','1024x1536',...(!gpt ? ['2048x2048'] : [])];
  if(!gpt) return [''];
  if(key === 'quality') return ['','low','medium','high'];
  return capability === 'TRANSPARENT' ? ['','png','webp'] : ['','png','jpeg','webp'];
}
export function outputParameterVerified(status:CloudImageModelsVO|undefined,model:string,capability:string,key:OutputKey,value:string):boolean {
  if(!value || (key === 'n' && value === '1')) return true;
  return Boolean(status?.profiles?.find(p=>p.model === model)?.outputProfiles?.some(p=>p.capability === capability && p.verifiedFields?.includes(key) && outputValue(p.output,key) === value));
}
export function outputVerified(status:CloudImageModelsVO|undefined,model:string,capability:string,output:CloudImageOutputParams):boolean {
  return outputFields.every(f=>outputParameterVerified(status,model,capability,f.key,outputValue(output,f.key)));
}
export function updateOutput(output:CloudImageOutputParams,key:OutputKey,value:string):CloudImageOutputParams {
  return {...output,[key]:key === 'n' ? Number(value) : value || null};
}

export function outputDefaults(status:CloudImageModelsVO|undefined,model:string,capability:string):CloudImageOutputParams {
  const profiles=status?.profiles?.find(p=>p.model === model)?.outputProfiles ?? [];
  const profile=profiles.find(p=>p.capability === capability && p.verifiedFields?.includes('size'));
  const result:CloudImageOutputParams={n:1};
  for(const key of profile?.verifiedFields ?? []) {
    const value=profile?.output[key];
    if(value != null) Object.assign(result,{[key]:value});
  }
  return result;
}
export function observedOutputFormat(status:CloudImageModelsVO|undefined,model:string,capability:string):string|undefined {
  const formats=status?.profiles?.find(p=>p.model === model)?.outputProfiles?.filter(p=>p.capability === capability).flatMap(p=>p.observedFormats ?? []) ?? [];
  const unique=[...new Set(formats)];
  return unique.length === 1 ? unique[0].toUpperCase() : undefined;
}
