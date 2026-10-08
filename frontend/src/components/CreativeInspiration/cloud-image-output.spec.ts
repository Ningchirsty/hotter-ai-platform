import {describe,it,expect} from 'vitest';
import {outputCandidates,outputParameterVerified,outputVerified,updateOutput,outputDefaults,observedOutputFormat,outputParameterStatusLabel} from './cloud-image-output';
import type {CloudImageModelsVO} from '@/api/image/types';
describe('independent cloud output controls',()=>{
  const status={profiles:[{model:'gpt-image-2.5-sunburst',outputProfiles:[{capability:'T2I',output:{size:'1024x1024',n:1,quality:'low',outputFormat:'png'},verifiedFields:['size','n','quality','outputFormat']}]}]} as CloudImageModelsVO;
  it('changing one field preserves other selections',()=>expect(updateOutput({size:'1024x1024',n:2,quality:'low',outputFormat:'png'},'quality','medium')).toEqual({size:'1024x1024',n:2,quality:'medium',outputFormat:'png'}));
  it('keeps verified values isolated by model and capability',()=>{expect(outputParameterVerified(status,'gpt-image-2.5-sunburst','T2I','size','1024x1024')).toBe(true);expect(outputParameterVerified(status,'gpt-image-2.5-sunburst','EDIT','size','1024x1024')).toBe(false);expect(outputParameterVerified(status,'gpt-image-2.5-flare','T2I','size','1024x1024')).toBe(false);});
  it('allows documented previews while rejecting unverified submissions',()=>{expect(outputCandidates('gpt-image-2.5-sunburst','T2I','quality')).toContain('high');expect(outputVerified(status,'gpt-image-2.5-sunburst','T2I',{quality:'high'})).toBe(false);expect(outputVerified(status,'gpt-image-2.5-sunburst','T2I',{quality:'low',size:'1024x1024'})).toBe(true);});
  it('does not apply GPT fields to Alibaba models or JPEG to transparent output',()=>{expect(outputCandidates('qwen-image-3.0','T2I','quality')).toEqual(['']);expect(outputCandidates('wan2.7-image','EDIT','outputFormat')).toEqual(['']);expect(outputCandidates('gpt-image-2.5-sunburst','TRANSPARENT','outputFormat')).not.toContain('jpeg');});
});

describe('concrete measured defaults',()=>{
  const status={profiles:[{model:'qwen-image-3.0',outputProfiles:[{capability:'T2I',output:{size:'1024x1024',n:1},verifiedFields:['size','n'],observedFormats:['png']},{capability:'EDIT',output:{size:'1536x1024',n:2,quality:'medium',outputFormat:'jpeg'},verifiedFields:['size','n'],observedFormats:['png']}]}]} as CloudImageModelsVO;
  it('defaults only the measured parameters of the selected model and capability',()=>{
    expect(outputDefaults(status,'qwen-image-3.0','T2I')).toEqual({size:'1024x1024',n:1});
    expect(outputDefaults(status,'qwen-image-3.0','EDIT')).toEqual({size:'1536x1024',n:2});
    expect(outputDefaults(status,'wan2.7-image','EDIT')).toEqual({n:1});
  });
  it('an explicit single-field change preserves measured defaults',()=>expect(updateOutput(outputDefaults(status,'qwen-image-3.0','EDIT'),'n','1')).toEqual({size:'1536x1024',n:1}));
  it('observed PNG does not become an explicit verified format parameter',()=>{
    expect(observedOutputFormat(status,'qwen-image-3.0','EDIT')).toBe('PNG');
    expect(outputParameterVerified(status,'qwen-image-3.0','EDIT','outputFormat','png')).toBe(false);
  });
});

describe('measured parameter outcomes',()=>{
  const evidence={profiles:[{model:'gpt-image-2.5-sunburst',outputProfiles:[
    {capability:'T2I',output:{size:'1024x1536',quality:'medium',outputFormat:'webp'},verifiedFields:['size'],parameterResults:{size:'PASSED',quality:'ACCEPTED_UNCONFIRMED',outputFormat:'OUTPUT_MISMATCH'}}
  ]}]} as CloudImageModelsVO;
  it('opens measured sizes while keeping ignored formats and unconfirmed quality blocked',()=>{
    expect(outputVerified(evidence,'gpt-image-2.5-sunburst','T2I',{size:'1024x1536',n:1})).toBe(true);
    expect(outputVerified(evidence,'gpt-image-2.5-sunburst','T2I',{size:'1024x1536',outputFormat:'webp'})).toBe(false);
    expect(outputVerified(evidence,'gpt-image-2.5-sunburst','T2I',{quality:'medium'})).toBe(false);
    expect(outputParameterStatusLabel(evidence,'gpt-image-2.5-sunburst','T2I','outputFormat','webp')).toContain('实测未生效');
    expect(outputParameterStatusLabel(evidence,'gpt-image-2.5-sunburst','T2I','quality','medium')).toContain('生效未确认');
  });
  it('does not apply parameter outcomes to another ability or model',()=>{
    expect(outputParameterStatusLabel(evidence,'gpt-image-2.5-sunburst','EDIT','outputFormat','webp')).toBe(' · 待验证');
    expect(outputParameterStatusLabel(evidence,'gpt-image-2.5-flare','T2I','quality','medium')).toBe(' · 待验证');
  });
});
