import { describe, expect, it } from 'vitest';
import { hashShort, schemaVerdictText, testPreviewSummary, testStatusText } from './testPreview';

/**
 * 测试预览口径测试（设计 §6.6）。
 *
 * <p>最要命的一条是 **Schema 结论**：只有走到校验那一步的失败才算"不通过"，
 * 其它失败是"未判定"。把未判定写成"不通过"会让人去改 Schema，
 * 而真正的问题可能只是密钥过期。</p>
 */
describe('studio test preview', () => {
  it('状态文案：成功要带上"不等于业务通过"，认不出的码原样显示', () => {
    expect(testStatusText('SUCCEEDED')).toContain('不等于业务通过');
    expect(testStatusText('FAILED')).toBe('调用失败');
    expect(testStatusText('SOMETHING')).toBe('SOMETHING');
    expect(testStatusText(undefined)).toBe('未知');
  });

  it('★ 哈希短显：留前 8 位（够辨认"是不是同一版内容"），空值给 —', () => {
    const long = 'a'.repeat(64);
    expect(hashShort(long)).toBe(`${'a'.repeat(8)}…${'a'.repeat(4)}`);
    expect(hashShort(' short ')).toBe('short');
    expect(hashShort('')).toBe('—');
    expect(hashShort(undefined)).toBe('—');
  });

  it('★ Schema 结论：只有 OUTPUT_UNPARSABLE 才算"不通过"，其它失败是"未判定"', () => {
    expect(schemaVerdictText({ testStatus: 'SUCCEEDED' })).toContain('通过（输出符合 Schema）');
    expect(schemaVerdictText({ testStatus: 'FAILED', errorCode: 'OUTPUT_UNPARSABLE' })).toContain(
      '不通过'
    );
    expect(schemaVerdictText({ testStatus: 'FAILED', errorCode: 'AUTH_FAILED' })).toContain('未判定');
    expect(schemaVerdictText({ testStatus: 'FAILED' })).toContain('未判定');
    expect(schemaVerdictText(undefined)).toBe('未判定');
  });

  it('摘要：没跑过给"怎么跑"的提示；跑过则带状态/修订/耗时/Schema 结论', () => {
    expect(testPreviewSummary(undefined)).toContain('还没跑过测试');

    const summary = testPreviewSummary({
      testStatus: 'SUCCEEDED',
      revision: 3,
      latencyMs: 120,
      contentHash: 'b'.repeat(64)
    });
    expect(summary).toContain('调用成功');
    expect(summary).toContain('修订 #3');
    expect(summary).toContain('耗时 120ms');
    expect(summary).toContain('Schema 通过');
  });
});
