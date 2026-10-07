import { describe, expect, it } from 'vitest';
import { imageCapabilityVerified, IMAGE_CLOUD_CAPABILITIES, imageCapabilitiesForModel, imageCapabilityStatusLabel } from './cloud-image-capabilities';
describe('云端能力的真实验收门槛', () => {
  it('缺少服务端验收状态时不开放提交', () => { expect(imageCapabilityVerified(undefined,'gpt-image-2.5-flare','T2I')).toBe(false); });
  it('模型和能力分别核验，不能继承另一型号的结果', () => {
    const state = {configured:true,models:['gpt-image-2.5-flare'],capabilities:['T2I'],verified:false,profiles:[{model:'gpt-image-2.5-flare',testedAt:'2026-10-07',capabilities:[{code:'T2I',verified:true,status:'PASSED'},{code:'EDIT',verified:false,status:'HTTP_404'}]}]};
    expect(imageCapabilityVerified(state,'gpt-image-2.5-flare','T2I')).toBe(true);
    expect(imageCapabilityVerified(state,'gpt-image-2.5-flare','EDIT')).toBe(false);
    expect(imageCapabilityVerified(state,'gpt-image-2.5-sunburst','T2I')).toBe(false);
    expect(IMAGE_CLOUD_CAPABILITIES.map(c=>c.code)).toEqual(['T2I','EDIT','MULTI','MASK','OUTPAINT','TRANSPARENT']);
  });
});

describe('型号能力与失败原因', () => {
  it('不同系列仅展示已对接的能力范围', () => {
    expect(imageCapabilitiesForModel('gpt-image-2.5-sunburst').map(c=>c.code)).toEqual(['T2I','EDIT','MULTI','MASK','OUTPAINT','TRANSPARENT']);
    expect(imageCapabilitiesForModel('qwen-image-3.0').map(c=>c.code)).toEqual(['T2I','EDIT','MULTI']);
    expect(imageCapabilitiesForModel('wan2.7-image-pro').map(c=>c.code)).toEqual(['T2I','EDIT','MULTI']);
    expect(imageCapabilitiesForModel('flux-2-pro')).toEqual([]);
    expect(imageCapabilitiesForModel('invented')).toEqual([]);
  });
  it('区分超时、HTTP 400 与图片验收缺失，不沿用过期 404', () => {
    expect(imageCapabilityStatusLabel('RESULT_UNKNOWN')).toContain('未确认');
    expect(imageCapabilityStatusLabel('HTTP_400')).toContain('400');
    expect(imageCapabilityStatusLabel('OUTPUT_UNVERIFIED')).toContain('待完成');
    expect(imageCapabilityStatusLabel('PASSED')).toBe('已验证');
  });
});
