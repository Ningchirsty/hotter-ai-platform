import { describe, expect, it } from 'vitest';
import {
  formatDiffValue,
  isPending,
  previewItems,
  proposalSourceLabel,
  proposalStatusLabel,
  proposalStatusTag,
  proposalSummary
} from './proposal';
import type { AigStudioChangeProposalVO } from '@/api/aigov/studio/types';

/**
 * 变更提案展示助手测试。
 *
 * 映射错不会报错：状态标签显示错的中文、预览少了几条——而人正是靠这些决定要不要采纳。
 */
describe('训练台变更提案展示助手', () => {
  const pending: AigStudioChangeProposalVO = {
    proposalId: 5001,
    draftId: 9001,
    baseRevision: 1,
    source: 'COPILOT',
    status: 'PENDING',
    preview: [
      { path: '/objective', before: '旧目标', after: '新目标' },
      { path: '/promptSections/role', before: '策划', after: '资深策划' }
    ]
  };

  it('状态/来源的中文映射与服务端枚举一致', () => {
    expect(proposalStatusLabel('PENDING')).toBe('待处理');
    expect(proposalStatusLabel('APPLIED')).toBe('已采纳');
    expect(proposalStatusLabel('REJECTED')).toBe('已拒绝');
    expect(proposalSourceLabel('COPILOT')).toBe('培训助手');
    expect(proposalSourceLabel('MANUAL')).toBe('人工草拟');
  });

  it('认不出的 code 原样返回（比硬套「未知」更有助于排查）', () => {
    expect(proposalStatusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(proposalSourceLabel('AGENT')).toBe('AGENT');
    expect(proposalStatusLabel(null)).toBe('未知');
    expect(proposalSourceLabel(undefined)).toBe('未知');
  });

  it('状态标签色：待处理=warning、已采纳=success、已拒绝=info', () => {
    expect(proposalStatusTag('PENDING')).toBe('warning');
    expect(proposalStatusTag('APPLIED')).toBe('success');
    expect(proposalStatusTag('REJECTED')).toBe('info');
    expect(proposalStatusTag(undefined)).toBe('warning');
  });

  it('只有待处理的提案可以被采纳/拒绝', () => {
    expect(isPending(pending)).toBe(true);
    expect(isPending({ ...pending, status: 'APPLIED' })).toBe(false);
    expect(isPending({ ...pending, status: 'REJECTED' })).toBe(false);
    expect(isPending(null)).toBe(false);
  });

  it('★ 预览是防御读取：缺 preview / 非数组 / 缺 path 都不炸，只是少显示', () => {
    expect(previewItems(undefined)).toEqual([]);
    expect(previewItems({ ...pending, preview: undefined })).toEqual([]);
    // @ts-expect-error 故意给非数组，验证运行期降级
    expect(previewItems({ ...pending, preview: 'oops' })).toEqual([]);
    // @ts-expect-error 故意给缺 path 的项
    expect(previewItems({ ...pending, preview: [{ before: 'a' }, null] })).toEqual([]);
  });

  it('空值明确显示「（空）」而不是留白（留白会被当成漏了）', () => {
    expect(formatDiffValue(null)).toBe('（空）');
    expect(formatDiffValue(undefined)).toBe('（空）');
    expect(formatDiffValue('')).toBe('（空）');
    expect(formatDiffValue('新目标')).toBe('新目标');
  });

  it('摘要：≤3 处全列，>3 处只列前 3 条并提示还有更多', () => {
    expect(proposalSummary(pending)).toBe('改 2 处：/objective、/promptSections/role');
    expect(proposalSummary({ ...pending, preview: [{ path: '/a' }] })).toBe('改 1 处：/a');
    const many = { ...pending, preview: [{ path: '/a' }, { path: '/b' }, { path: '/c' }, { path: '/d' }] };
    expect(proposalSummary(many)).toBe('改 4 处：/a、/b、/c …');
  });

  it('没有可展示改动时给出明确说明（而不是空字符串）', () => {
    expect(proposalSummary(undefined)).toBe('这个提案没有可展示的改动');
    expect(proposalSummary({ ...pending, preview: [] })).toBe('这个提案没有可展示的改动');
  });
});
