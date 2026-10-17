import { describe, expect, it } from 'vitest';
import {
  RETRIEVAL_MODE_OPTIONS,
  STATUS_OPTIONS,
  canAdvance,
  canRetire,
  chunkExcerptText,
  chunkIndexText,
  chunkSummary,
  compareVersionId,
  documentReadiness,
  documentStatusSummary,
  documentStatusSummaryText,
  indexingSummary,
  isNotRetrievable,
  latestVersion,
  referenceAgentText,
  referenceScopeText,
  referenceSummary,
  registerValidation,
  retrievalModeHint,
  retrievalModeLabel,
  retrievalSummary,
  sha256Valid,
  sourceTypeLabel,
  statusLabel,
  statusTagType,
  versionRetrievable
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

  it('★ 引用影响面：启用/停用分开数（按虚高的影响面退役会误判）', () => {
    expect(referenceSummary([])).toContain('没有 Agent 引用');
    expect(referenceSummary(undefined)).toContain('没有 Agent 引用');
    const summary = referenceSummary([{ enabled: 'Y' }, { enabled: 'Y' }, { enabled: 'N' }]);
    expect(summary).toContain('共 3 条引用');
    expect(summary).toContain('启用中 2 条');
    expect(summary).toContain('已停用 1 条');
  });

  it('★ 引用行：名称解析不到就说"已删除"；范围为空就是"不限"（不是空白）', () => {
    expect(referenceAgentText({ agentName: '产品问答', agentVersion: '0.2.0' })).toBe(
      '产品问答 · 0.2.0'
    );
    expect(referenceAgentText({ agentCode: 'PRODUCT_QA' })).toBe('PRODUCT_QA');
    expect(referenceAgentText({})).toBe('（Agent 或版本已删除）');
    expect(referenceAgentText(undefined)).toBe('（Agent 或版本已删除）');

    expect(referenceScopeText({})).toBe('不限');
    expect(referenceScopeText(undefined)).toBe('不限');
    expect(referenceScopeText({ companyId: 9, brandId: 3, scenarioCode: 'POSTER', roleScope: 'a,b' })).toBe(
      '公司 9，品牌 3，场景 POSTER，角色 a,b'
    );
  });

  it('★ 检索模式：两种、默认关键字；向量模式必须提醒「没命中≠资料里没有」', () => {
    expect(RETRIEVAL_MODE_OPTIONS.map(item => item.code)).toEqual(['KEYWORD', 'VECTOR']);
    expect(retrievalModeLabel('VECTOR')).toContain('向量');
    expect(retrievalModeLabel(undefined)).toContain('关键字');

    expect(retrievalModeHint('VECTOR')).toContain('不代表资料里没有');
    expect(retrievalModeHint('KEYWORD')).toContain('正式执行路径');
    // 空值按默认模式给提示，界面不出现空白
    expect(retrievalModeHint(undefined)).toContain('正式执行路径');
  });

  it('★ 解析并索引结果：失败带分类与原因；成功要说清引擎与段数', () => {
    expect(indexingSummary(undefined)).toBe('');
    expect(
      indexingSummary({ status: 'FAILED', errorCode: 'PARSE_FAILED', message: 'PDF 一页文本都没取到' })
    ).toBe('处理失败（PARSE_FAILED）：PDF 一页文本都没取到');
    expect(indexingSummary({ status: 'FAILED' })).toContain('未分类');

    const ok = indexingSummary({
      status: 'READY',
      chunkCount: 12,
      parserEngineId: 'pdfbox-3',
      embeddingEngineId: 'builtin-hash-ngram-v1'
    });
    expect(ok).toContain('12 段');
    expect(ok).toContain('pdfbox-3');
    expect(ok).toContain('builtin-hash-ngram-v1');
  });

  it('最新版本按版本ID取最大：19 位雪花 ID 不能用 Number 比（会丢精度）', () => {
    // 这两个 ID 只差 1，转 Number 后会相等，比错顺序
    const a = { versionId: '1763000000000000001', version: '1', status: 'READY' };
    const b = { versionId: '1763000000000000002', version: '2', status: 'FAILED' };
    expect(latestVersion([a, b])?.versionId).toBe(b.versionId);
    expect(latestVersion([b, a])?.versionId).toBe(b.versionId);
    expect(compareVersionId(a.versionId, b.versionId)).toBe(-1);
    expect(latestVersion([])).toBeUndefined();
    expect(latestVersion(undefined)).toBeUndefined();
  });

  it('★ 能不能被检索：按"所有版本"算，最新版失败但旧版可用时不能说成不可检索', () => {
    const now = new Date('2026-06-01T00:00:00Z');
    const ready = { versionId: '10', version: '1', status: 'READY' };

    expect(documentReadiness({ versions: [] }, now).code).toBe('NO_VERSION');
    expect(documentReadiness(undefined, now).code).toBe('NO_VERSION');
    expect(documentReadiness({ versions: [ready] }, now).code).toBe('RETRIEVABLE');
    expect(documentReadiness({ versions: [ready] }, now).label).toBe('可检索');

    // 最新版失败、旧版仍可检索：标红但要说明旧版仍在用
    const failedLatest = documentReadiness({ versions: [{ versionId: '11', version: '2', status: 'FAILED' }, ready] }, now);
    expect(failedLatest.code).toBe('FAILED');
    expect(failedLatest.label).toContain('旧版仍可检索');
    expect(failedLatest.hint).toContain('旧内容');

    // 最新版失败且没有可用旧版：明确说检索不到
    const failedOnly = documentReadiness({ versions: [{ versionId: '11', version: '2', status: 'FAILED' }] }, now);
    expect(failedOnly.code).toBe('FAILED');
    expect(failedOnly.hint).toContain('检索不到');

    // 处理中
    expect(documentReadiness({ versions: [{ versionId: '12', version: '3', status: 'PARSING' }] }, now).code).toBe(
      'IN_PROGRESS'
    );
    // 已退役
    expect(documentReadiness({ versions: [{ versionId: '13', version: '4', status: 'RETIRED' }] }, now).code).toBe(
      'RETIRED'
    );
  });

  it('★ 已就绪但不在有效期：状态是绿的却查不到，必须单独一态说清是有效期问题', () => {
    const now = new Date('2026-06-01T00:00:00Z');
    const expired = {
      versionId: '20',
      version: '1',
      status: 'READY',
      effectiveTo: '2026-05-01T00:00:00Z'
    };
    const notYet = {
      versionId: '21',
      version: '2',
      status: 'READY',
      effectiveFrom: '2026-07-01T00:00:00Z'
    };
    const inWindow = {
      versionId: '22',
      version: '3',
      status: 'READY',
      effectiveFrom: '2026-01-01T00:00:00Z',
      effectiveTo: '2026-12-31T00:00:00Z'
    };

    expect(documentReadiness({ versions: [expired] }, now).code).toBe('READY_OUT_OF_WINDOW');
    expect(documentReadiness({ versions: [expired] }, now).hint).toContain('有效期');
    expect(documentReadiness({ versions: [notYet] }, now).code).toBe('READY_OUT_OF_WINDOW');
    expect(documentReadiness({ versions: [inWindow] }, now).code).toBe('RETRIEVABLE');
    // 闭区间：截止时刻本身仍有效
    expect(versionRetrievable(inWindow, new Date('2026-12-31T00:00:00Z'))).toBe(true);
    expect(versionRetrievable(expired, now)).toBe(false);
  });

  it('文档摘要：分别数出可检索/处理中/失败/还未就绪，并能一句话说清', () => {
    const now = new Date('2026-06-01T00:00:00Z');
    const rows = [
      { documentId: '1', versions: [{ versionId: '11', version: '1', status: 'READY' }] },
      { documentId: '2', versions: [{ versionId: '12', version: '1', status: 'PARSING' }] },
      { documentId: '3', versions: [{ versionId: '13', version: '1', status: 'FAILED' }] },
      { documentId: '4', versions: [] }
    ];
    const summary = documentStatusSummary(rows, now);
    expect(summary).toEqual({ total: 4, retrievable: 1, inProgress: 1, failed: 1, other: 1 });
    expect(documentStatusSummaryText(summary)).toBe(
      '共 4 个文档 · 可检索 1 · 处理中 1 · 失败 1 · 还未就绪 1'
    );
    expect(documentStatusSummary(undefined, now)).toEqual({
      total: 0,
      retrievable: 0,
      inProgress: 0,
      failed: 0,
      other: 0
    });
    // 「只看检索不到」的判据与胶囊同一处口径
    expect(isNotRetrievable(rows[0], now)).toBe(false);
    expect(isNotRetrievable(rows[2], now)).toBe(true);
  });
});
