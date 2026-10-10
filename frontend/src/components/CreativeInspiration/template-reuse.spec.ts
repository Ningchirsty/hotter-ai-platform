import { describe, it, expect } from 'vitest';
import { adaptTemplateOutput, creationMessage } from './template-reuse';
import { outputVerified } from './cloud-image-output';
import type { CloudImageModelsVO } from '@/api/image/types';
const model = 'gpt-image-2.5-sunburst';
const status: CloudImageModelsVO = {configured:true, models:[model], capabilities:['T2I'], verified:true, profiles:[{model,testedAt:'2026-10-10',capabilities:[{code:'T2I',verified:true,status:'PASSED'}],outputProfiles:[{model,capability:'T2I',label:'tested',output:{size:'1024x1536',n:1,outputFormat:'png'},verifiedFields:['size','n','outputFormat']}]}]};
describe('editable template output', () => {
  it('adapts unsupported square/high options to the tested model profile without changing the original template', () => {
    const requested = {size:'1024x1024',n:1,quality:'high'};
    const result = adaptTemplateOutput(status,model,'T2I',requested);
    expect(result.output).toEqual({size:'1024x1536',n:1,outputFormat:'png'});
    expect(outputVerified(status,model,'T2I',result.output)).toBe(true);
    expect(result.note).toContain('图像尺寸、画面质量');
    expect(requested).toEqual({size:'1024x1024',n:1,quality:'high'});
  });
  it('preserves measured template parameters and never borrows verification from a different model', () => {
    expect(adaptTemplateOutput(status,model,'T2I',{size:'1024x1536',n:1,outputFormat:'png'}).note).toBe('');
    const result = adaptTemplateOutput(status,'gpt-image-2.5-flare','T2I',{size:'1024x1536',n:1,outputFormat:'png'});
    expect(result.output).toEqual({n:1});
    expect(result.note).toContain('图像尺寸、输出格式');
  });
  it('keeps ordinary messages while hiding provider and connection details in user-facing errors', () => {
    expect(creationMessage('蓝章鱼 API Key 未配置，供应商接口返回错误')).toBe('云端服务 服务凭据 未配置，云端服务服务返回错误');
    expect(creationMessage('BluOcto https://bluocto.com/v1/images/generations 返回错误')).not.toMatch(/bluocto|API|蓝章鱼/i);
    expect(creationMessage('模板已更新，请重新选择')).toBe('模板已更新，请重新选择');
  });
});
