import { describe, it, expect } from 'vitest';
import { adaptTemplateOutput, creationMessage, creationTitle, taskModelLabel, templateCreatePlan, templateEditorOutput } from './template-reuse';
import { outputVerified, updateOutput } from './cloud-image-output';
import type { CloudImageModelsVO } from '@/api/image/types';
const model = 'gpt-image-2.5-sunburst';
const status: CloudImageModelsVO = {configured:true, models:[model], capabilities:['T2I'], verified:true, profiles:[{model,testedAt:'2026-10-10',capabilities:[{code:'T2I',verified:true,status:'PASSED'}],outputProfiles:[{model,capability:'T2I',label:'tested',output:{size:'1024x1536',n:1,outputFormat:'png'},verifiedFields:['size','n','outputFormat']}]}]};
describe('editable template output', () => {
  it('prefills original template parameters even when verification requires the user to edit them', () => {
    const original = { size: '1024x1536', n: 1, quality: 'high' };
    const result = templateEditorOutput(status, model, 'T2I', original);
    expect(result.output).toEqual(original); expect(result.output).not.toBe(original);
    expect(result.note).toContain('画面质量尚未验证通过');
    expect(outputVerified(status, model, 'T2I', result.output)).toBe(false);
    expect(outputVerified(status, model, 'T2I', updateOutput(result.output, 'quality', ''))).toBe(true);
    expect(original.quality).toBe('high');
  });
  it('does not insert default dimensions or format into a verified template recipe', () => {
    const original = { size: '1024x1536', n: 1 };
    expect(templateEditorOutput(status, model, 'T2I', original)).toEqual({ output: original, note: '模板参数已完整带入，可继续调整。' });
    expect(templateEditorOutput(status, 'gpt-image-2.5-flare', 'T2I', original).note).toContain('图像尺寸尚未验证通过');
  });
  it('shows the exact measured output before one-click creation and keeps unverified models unavailable', () => {
    const plan = templateCreatePlan(status, { model, outputSize: '1024x1024' });
    expect(plan.available).toBe(true);
    expect(plan.label).toBe('1024x1536 · 1 张 · PNG');
    expect(plan.output).toEqual({size:'1024x1536',n:1,outputFormat:'png'});
    expect(templateCreatePlan(status, { model: 'gpt-image-2.5-flare', outputSize: '1024x1024' }).available).toBe(false);
    expect(templateCreatePlan(undefined, { model, outputSize: '1024x1024' }).available).toBe(false);
  });
  it('hides historical provider names and workflow IDs while retaining model names', () => {
    expect(creationTitle('中文海报 · wf-ability-image-poster-qwen2512')).toBe('中文海报');
    expect(creationTitle('蓝章鱼 · ComfyUI')).not.toMatch(/蓝章鱼|ComfyUI/);
    expect(taskModelLabel('wf-local-qwen', 'QWEN21', [{workflowCode:'wf-local-qwen',modelCode:'QWEN21',modelName:'Qwen-Image 2.1'}])).toBe('Qwen-Image 2.1');
  });
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
