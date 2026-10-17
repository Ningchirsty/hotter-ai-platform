import { describe, expect, it } from 'vitest';
import { releaseStatusLabel, releaseStatusMeta } from './releaseStatus';

/**
 * 发布状态口径（走读 §5.5：不再直出裸枚举）。
 *
 * <p>重点钉一件：**认不出的状态原样回显**，不能默认成"草稿"或"已发布"——
 * 那会让一个未知状态被当成可发布/已发布，直接影响审批与上线判断。</p>
 */
describe('release status meta', () => {
  it('七种状态各有中文与色调（与后端 AigReleaseStatusEnum 对齐）', () => {
    expect(releaseStatusLabel('DRAFT')).toBe('草稿');
    expect(releaseStatusLabel('STABLE')).toContain('已发布');
    expect(releaseStatusMeta('STABLE').tone).toBe('success');
    expect(releaseStatusMeta('CANDIDATE').tone).toBe('warning');
    expect(releaseStatusMeta('DISABLED').tone).toBe('danger');
    // 大小写与空白容错：库里大小写不一致不该显示成未知
    expect(releaseStatusLabel('  stable ')).toBe(releaseStatusLabel('STABLE'));
  });

  it('★ 认不出的状态原样回显（不默认成草稿/已发布）', () => {
    expect(releaseStatusMeta('SOMETHING_NEW')).toEqual({ label: 'SOMETHING_NEW', tone: 'info' });
    expect(releaseStatusMeta('sOmEtHiNg').label).toBe('SOMETHING');
  });

  it('空值说"未设置"而不是空白', () => {
    expect(releaseStatusLabel(undefined)).toBe('未设置');
    expect(releaseStatusLabel('')).toBe('未设置');
    expect(releaseStatusLabel('   ')).toBe('未设置');
  });
});
