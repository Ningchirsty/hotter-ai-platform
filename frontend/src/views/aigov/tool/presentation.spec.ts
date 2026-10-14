import { describe, expect, it } from 'vitest';
import {
  OPERATION_TYPE_OPTIONS,
  confirmationHint,
  definitionValidation,
  executableTagType,
  executableText,
  operationTypeLabel,
  requiresConfirmation,
  versionValidation
} from './presentation';

/**
 * 工具页展示口径测试（设计 §6.5、§7.5）。
 *
 * <p>最要命的一条是"未接入被显示成可用"：界面若把 {@code executable=false} 显示成可用，
 * 用户会以为能跑，直到运行时才被拒。</p>
 */
describe('tool presentation', () => {
  it('操作类型词表与后端一致', () => {
    expect(OPERATION_TYPE_OPTIONS.map(item => item.code)).toEqual([
      'READ_ONLY_QUERY',
      'GENERATE_DRAFT',
      'MUTATE_BUSINESS_DATA',
      'EXTERNAL_SEND'
    ]);
  });

  it('码 → 文案；认不出的码原样展示', () => {
    expect(operationTypeLabel('MUTATE_BUSINESS_DATA')).toBe('变更业务数据');
    expect(operationTypeLabel('NO_SUCH')).toBe('NO_SUCH');
    expect(operationTypeLabel(undefined)).toBe('—');
  });

  it('★ 未接入如实显示「已登记 待接入」，不能显示为可用', () => {
    expect(executableText(false)).toBe('已登记 待接入');
    expect(executableText(true)).toBe('可执行');
    expect(executableText(undefined)).toBe('已登记 待接入');
    expect(executableTagType(false)).toBe('info');
    expect(executableTagType(true)).toBe('success');
  });

  it('★ 写操作必须人工确认；只读/草稿不需要', () => {
    expect(requiresConfirmation('MUTATE_BUSINESS_DATA')).toBe(true);
    expect(requiresConfirmation('EXTERNAL_SEND')).toBe(true);
    expect(requiresConfirmation('READ_ONLY_QUERY')).toBe(false);
    expect(requiresConfirmation('GENERATE_DRAFT')).toBe(false);
    expect(confirmationHint('EXTERNAL_SEND')).toContain('人工确认');
    expect(confirmationHint('READ_ONLY_QUERY')).toBe('');
  });

  it('★ 定义校验：编码与名称必填', () => {
    expect(definitionValidation({})).not.toBeNull();
    expect(definitionValidation({ toolCode: 't' })).not.toBeNull();
    expect(definitionValidation({ toolCode: 't', toolName: 'n' })).toBeNull();
  });

  it('★ 版本校验与后端同口径：编码/版本/操作类型必填，超时须为正', () => {
    expect(versionValidation({})).not.toBeNull();
    expect(versionValidation({ toolCode: 't', version: '1.0.0' })).not.toBeNull();
    expect(
      versionValidation({ toolCode: 't', version: '1.0.0', operationType: 'NO_SUCH' })
    ).not.toBeNull();
    expect(
      versionValidation({
        toolCode: 't',
        version: '1.0.0',
        operationType: 'READ_ONLY_QUERY',
        timeoutMs: 0
      })
    ).not.toBeNull();
    expect(
      versionValidation({ toolCode: 't', version: '1.0.0', operationType: 'READ_ONLY_QUERY' })
    ).toBeNull();
  });
});
