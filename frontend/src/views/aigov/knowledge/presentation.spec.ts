import { describe, expect, it } from 'vitest';
import {
  STATUS_OPTIONS,
  canAdvance,
  canRetire,
  chunkExcerptText,
  chunkIndexText,
  chunkSummary,
  registerValidation,
  retrievalSummary,
  sha256Valid,
  sourceTypeLabel,
  statusLabel,
  statusTagType
} from './presentation';

/**
 * 知识中心展示口径测试（设计 §6.4）。
 */
describe('knowledge presentation', () => {
  it('状态词表与后端管道一致', () => {
    expect(STATUS_OPTIONS.map(item => item.code)).toEqual([
      'UPLOADED',
      'SCANNING',
      'PARSING',
      'CHUNKING',
      'INDEXING',
      'READY',
      'FAILED',
      'RETIRED'
    ]);
  });

  it('码 → 文案；认不出的码原样展示', () => {
    expect(statusLabel('READY')).toBe('已就绪');
    expect(statusLabel('NO_SUCH')).toBe('NO_SUCH');
    expect(statusLabel(undefined)).toBe('—');
    expect(sourceTypeLabel('MARKDOWN')).toBe('Markdown');
  });

  it('状态标签色：就绪=绿、失败=红、退役=灰、处理中=橙', () => {
    expect(statusTagType('READY')).toBe('success');
    expect(statusTagType('FAILED')).toBe('danger');
    expect(statusTagType('RETIRED')).toBe('info');
    expect(statusTagType('PARSING')).toBe('warning');
    expect(statusTagType('UPLOADED')).toBe('primary');
  });

  it('★ 推进/退役可用性：已就绪与已退役没有"下一阶段"；失败可重试', () => {
    expect(canAdvance('UPLOADED')).toBe(true);
    expect(canAdvance('FAILED')).toBe(true);
    expect(canAdvance('READY')).toBe(false);
    expect(canAdvance('RETIRED')).toBe(false);

    expect(canRetire('READY')).toBe(true);
    expect(canRetire('RETIRED')).toBe(false);
    expect(canRetire(undefined)).toBe(false);
  });

  it('★ 原文哈希必须是 64 位十六进制（前端先拦，不让用户提交后才被拒）', () => {
    expect(sha256Valid('a'.repeat(64))).toBe(true);
    expect(sha256Valid('A'.repeat(64))).toBe(true);
    expect(sha256Valid('a'.repeat(63))).toBe(false);
    expect(sha256Valid('z'.repeat(64))).toBe(false);
    expect(sha256Valid(undefined)).toBe(false);
  });

  it('★ 登记校验与后端同口径', () => {
    expect(registerValidation({})).not.toBeNull();
    expect(registerValidation({ collectionId: 1, sourceName: ' ' })).not.toBeNull();
    expect(registerValidation({ collectionId: 1, sourceName: 'a.pdf', sha256: 'x' })).not.toBeNull();
    expect(
      registerValidation({ collectionId: 1, sourceName: 'a.pdf', sha256: 'a'.repeat(64) })
    ).toBeNull();
  });

  it('检索摘要：没有支持资料时给出原因；有命中时报段数', () => {
    expect(retrievalSummary({ noSupport: true, reason: '没有命中任何已授权片段' })).toContain(
      '没有支持资料'
    );
    expect(retrievalSummary({ citations: [{}, {}] })).toBe('命中 2 段可引用资料');
    expect(retrievalSummary(undefined)).toBe('');
  });

  it('★ 分段预览：空列表要区分「还没到分段阶段」与「解析没产出内容」', () => {
    expect(chunkSummary([])).toContain('还没有分段');
    expect(chunkSummary([])).toContain('解析没产出内容');
    expect(chunkSummary(undefined)).toContain('还没有分段');
  });

  it('★ 分段预览：摘要说清「正文在哪、有没有进索引」，预览缺失不显示成空白', () => {
    const summary = chunkSummary([
      { hasFullText: true, indexed: true },
      { hasFullText: true, indexed: false },
      { hasFullText: false, indexed: false }
    ]);
    expect(summary).toContain('共 3 段');
    expect(summary).toContain('正文在受控存储 2 段');
    expect(summary).toContain('已进检索索引 1 段');

    expect(chunkExcerptText({ textExcerpt: ' 第一段 ' })).toBe('第一段');
    expect(chunkExcerptText({ textExcerpt: '   ' })).toBe('（无预览）');
    expect(chunkExcerptText(undefined)).toBe('（无预览）');
    expect(chunkIndexText({ indexed: true })).toBe('已进索引');
    expect(chunkIndexText({})).toBe('未进索引');
  });
});
