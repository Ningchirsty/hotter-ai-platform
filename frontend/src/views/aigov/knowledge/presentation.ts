/**
 * 知识中心展示口径（纯函数，可单测）。
 *
 * @author ai-gov
 */

export interface CodeOption {
  code: string;
  label: string;
}

/** 处理/生效状态（与后端 AigKnowledgeStatusEnum 一致，顺序即管道顺序） */
export const STATUS_OPTIONS: readonly CodeOption[] = [
  { code: 'UPLOADED', label: '已上传' },
  { code: 'SCANNING', label: '安全扫描中' },
  { code: 'PARSING', label: '解析中' },
  { code: 'CHUNKING', label: '分段中' },
  { code: 'INDEXING', label: '索引中' },
  { code: 'READY', label: '已就绪' },
  { code: 'FAILED', label: '已失败' },
  { code: 'RETIRED', label: '已退役' }
] as const;

/** 来源类型（首期支持） */
export const SOURCE_TYPE_OPTIONS: readonly CodeOption[] = [
  { code: 'PDF', label: 'PDF' },
  { code: 'DOCX', label: 'Word 文档' },
  { code: 'TXT', label: '纯文本' },
  { code: 'MARKDOWN', label: 'Markdown' }
] as const;

/** 数据等级 */
export const DATA_LEVEL_OPTIONS: readonly CodeOption[] = [
  { code: 'PUBLIC', label: 'PUBLIC（公开）' },
  { code: 'INTERNAL', label: 'INTERNAL（内部）' },
  { code: 'RESTRICTED', label: 'RESTRICTED（限制）' },
  { code: 'STRICT', label: 'STRICT（严格）' }
] as const;

/**
 * 按码取文案；认不出的码原样展示（不显示空白）。
 */
export function labelOf(options: readonly CodeOption[], code?: string): string {
  if (!code) {
    return '—';
  }
  return options.find(item => item.code === code)?.label ?? code;
}

export function statusLabel(code?: string): string {
  return labelOf(STATUS_OPTIONS, code);
}

export function sourceTypeLabel(code?: string): string {
  return labelOf(SOURCE_TYPE_OPTIONS, code);
}

/** 状态标签色：就绪=绿、失败=红、退役=灰、处理中=橙、已上传=蓝 */
export function statusTagType(code?: string): 'success' | 'danger' | 'info' | 'warning' | 'primary' {
  if (code === 'READY') {
    return 'success';
  }
  if (code === 'FAILED') {
    return 'danger';
  }
  if (code === 'RETIRED') {
    return 'info';
  }
  if (code === 'UPLOADED') {
    return 'primary';
  }
  return 'warning';
}

/** 是否可以推进下一阶段（已就绪/已退役没有"下一阶段"）。失败可重试，因此返回 true */
export function canAdvance(status?: string): boolean {
  return status !== 'READY' && status !== 'RETIRED';
}

/** 是否可以退役（已退役无需再退役） */
export function canRetire(status?: string): boolean {
  return !!status && status !== 'RETIRED';
}

/**
 * 原文哈希校验：必须是 64 位十六进制。
 *
 * <p>前端先校验一次，是为了让"哈希填错"在点击时就有明确提示，而不是提交后才从后端拿到
 * "必须是 64 位 SHA-256"。</p>
 */
export function sha256Valid(value?: string): boolean {
  return !!value && /^[0-9a-fA-F]{64}$/.test(value.trim());
}

/**
 * 登记表单校验（与后端同口径）。
 *
 * @returns 通过返回 null；否则返回可显示的拒绝原因
 */
export function registerValidation(form: {
  collectionId?: string | number;
  sourceName?: string;
  sha256?: string;
}): string | null {
  if (!form?.collectionId) {
    return '请选择知识集合';
  }
  if (!form?.sourceName || !form.sourceName.trim()) {
    return '请填写文档名（用于识别来源类型）';
  }
  if (!sha256Valid(form.sha256)) {
    return '原文哈希必须是 64 位 SHA-256（上传链路计算）';
  }
  return null;
}

/** 检索结果摘要（没有支持资料时给出原因，而不是留空） */
export function retrievalSummary(result?: { citations?: unknown[]; noSupport?: boolean; reason?: string }): string {
  if (!result) {
    return '';
  }
  if (result.noSupport) {
    return '没有支持资料：' + (result.reason || '无命中');
  }
  const count = result.citations?.length ?? 0;
  return '命中 ' + count + ' 段可引用资料';
}

/**
 * 检索模式（评测对比用；附件「关键词检索与向量检索可组合……排序参数通过评测确定」）。
 *
 * 只给两路、不给混合：混合要定权重，而权重正是要评测出来的东西。
 */
export const RETRIEVAL_MODE_OPTIONS: readonly CodeOption[] = [
  { code: 'KEYWORD', label: '关键字（同正式执行路径）' },
  { code: 'VECTOR', label: '向量（需先解析并索引）' }
];

/** 检索模式文案（未知值按默认模式显示，避免界面上出现空白） */
export function retrievalModeLabel(code?: string): string {
  return labelOf(RETRIEVAL_MODE_OPTIONS, code || 'KEYWORD');
}

/**
 * 检索模式的提醒。
 *
 * 向量模式**没有命中不等于资料里没有**：可能是这一版还没执行「解析并索引」。
 * 不把这句话写在界面上，评测的人会把"没索引"读成"向量检索没用"。
 */
export function retrievalModeHint(mode?: string): string {
  return mode === 'VECTOR'
    ? '向量模式只返回向量索引命中的片段：没有命中不代表资料里没有，可能是这一版还没执行「解析并索引」。'
    : '关键字模式与正式执行路径同口径；要对比向量效果，请切换模式后用同一条查询重跑。';
}

/** 解析并索引结果摘要（失败时必须带分类与原因，不能只说"处理失败"） */
export function indexingSummary(result?: {
  status?: string;
  chunkCount?: number;
  parserEngineId?: string;
  embeddingEngineId?: string;
  errorCode?: string;
  message?: string;
}): string {
  if (!result) {
    return '';
  }
  if (result.status === 'FAILED') {
    return '处理失败（' + (result.errorCode || '未分类') + '）：' + (result.message || '无说明');
  }
  return (
    '已就绪：' +
    (result.chunkCount ?? 0) +
    ' 段｜解析引擎=' +
    (result.parserEngineId || '未知') +
    '｜嵌入引擎=' +
    (result.embeddingEngineId || '未知')
  );
}

/** 分段预览文案（没有预览就如实说，不显示成空白） */
export function chunkExcerptText(chunk?: { textExcerpt?: string }): string {
  const text = chunk?.textExcerpt?.trim();
  return text ? text : '（无预览）';
}

/** 索引状态文案：这是"解析之后有没有真的进检索"的唯一可见信号 */
export function chunkIndexText(chunk?: { indexed?: boolean }): string {
  return chunk?.indexed ? '已进索引' : '未进索引';
}

/**
 * 分段预览摘要。
 *
 * <p>空列表**不能只说"没有数据"**：维护人需要区分"还没跑到分段阶段"与"解析没产出内容"，
 * 所以这里把两种可能都写出来，并把"正文在哪、有没有进索引"一并说明——
 * 只显示 excerpt 会让人以为"知识就是这一小段"。</p>
 */
export function chunkSummary(chunks?: { hasFullText?: boolean; indexed?: boolean }[]): string {
  const list = chunks ?? [];
  if (!list.length) {
    return '这一版还没有分段：要么还没跑到「分段」阶段，要么解析没产出内容。';
  }
  const withFullText = list.filter((item) => item.hasFullText).length;
  const indexed = list.filter((item) => item.indexed).length;
  return `共 ${list.length} 段（每段只显示受限长度的预览）：正文在受控存储 ${withFullText} 段、已进检索索引 ${indexed} 段。`;
}

/** 引用行的名称文案：解析不到就如实说"已删除"，不显示成空白 */
export function referenceAgentText(ref?: {
  agentName?: string;
  agentCode?: string;
  agentVersion?: string;
}): string {
  const name = ref?.agentName?.trim() || ref?.agentCode?.trim();
  if (!name) {
    return '（Agent 或版本已删除）';
  }
  return ref?.agentVersion ? `${name} · ${ref.agentVersion}` : name;
}

/**
 * 引用收窄范围文案：**空 = 不限**（与后端同一口径），不要显示成空白。
 *
 * 留空被读成"没有范围限制"还是"范围未知"，是运维判断影响面的关键差别。
 */
export function referenceScopeText(ref?: {
  companyId?: string | number;
  brandId?: string | number;
  scenarioCode?: string;
  roleScope?: string;
}): string {
  const parts: string[] = [];
  if (ref?.companyId) {
    parts.push(`公司 ${ref.companyId}`);
  }
  if (ref?.brandId) {
    parts.push(`品牌 ${ref.brandId}`);
  }
  if (ref?.scenarioCode) {
    parts.push(`场景 ${ref.scenarioCode}`);
  }
  if (ref?.roleScope) {
    parts.push(`角色 ${ref.roleScope}`);
  }
  return parts.length ? parts.join('，') : '不限';
}

/**
 * 引用清单摘要（设计 §6.4「引用该版本的 Agent」）。
 *
 * 只说"共 N 条"不够：停用中的绑定**不会被采用**，必须与启用中的分开数，
 * 否则维护人会按一个虚高的影响面去做退役决策。
 */
export function referenceSummary(references?: { enabled?: string }[]): string {
  const list = references ?? [];
  if (!list.length) {
    return '没有 Agent 引用这个集合：退役/停用不会影响任何已登记的使用方。';
  }
  const enabled = list.filter((item) => item.enabled === 'Y').length;
  return `共 ${list.length} 条引用：启用中 ${enabled} 条、已停用 ${list.length - enabled} 条（停用中的不会被采用，但仍保留为使用记录）。`;
}

/** 版本摘要（文档状态视图里每个文档带的那一小段） */
export interface VersionBrief {
  versionId?: string | number;
  version?: string;
  status?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
}

/** 文档 + 各自版本的只读视图（与后端 AigKnowledgeDocumentStatusVo 对齐） */
export interface DocumentStatusRow {
  documentId?: string | number;
  sourceName?: string;
  sourceType?: string;
  ownerId?: string | number;
  versions?: VersionBrief[];
}

/**
 * 版本ID比较（雪花 ID 是 19 位，**不能转 Number**：超过 2^53 会静默丢精度，比错顺序）。
 *
 * 能按数值比就按数值比（用 BigInt），解析不了退回字符串比较——只用于"谁是最新"，
 * 比错也不会比出错误结论之外的东西，但仍要避免 Number 精度问题。
 */
export function compareVersionId(a?: string | number, b?: string | number): number {
  const left = String(a ?? '');
  const right = String(b ?? '');
  if (!left || !right) {
    return left.localeCompare(right);
  }
  try {
    const diff = BigInt(left) - BigInt(right);
    return diff === 0n ? 0 : diff > 0n ? 1 : -1;
  } catch {
    return left.localeCompare(right);
  }
}

/** 最新版本（按版本ID取最大；没有版本返回 undefined） */
export function latestVersion(versions?: VersionBrief[]): VersionBrief | undefined {
  const list = versions ?? [];
  if (!list.length) {
    return undefined;
  }
  return list.reduce((latest, item) => (compareVersionId(item.versionId, latest.versionId) > 0 ? item : latest));
}

/** 该版本此刻是否会被检索采用（READY + 在有效期内，闭区间；两端为空表示该侧不限） */
export function versionRetrievable(version?: VersionBrief, now: Date = new Date()): boolean {
  if (!version || version.status !== 'READY') {
    return false;
  }
  const time = now.getTime();
  if (version.effectiveFrom) {
    const from = Date.parse(version.effectiveFrom);
    if (!Number.isNaN(from) && time < from) {
      return false;
    }
  }
  if (version.effectiveTo) {
    const to = Date.parse(version.effectiveTo);
    if (!Number.isNaN(to) && time > to) {
      return false;
    }
  }
  return true;
}

export type ReadinessCode = 'RETRIEVABLE' | 'READY_OUT_OF_WINDOW' | 'FAILED' | 'IN_PROGRESS' | 'RETIRED' | 'NO_VERSION';

export interface Readiness {
  code: ReadinessCode;
  label: string;
  tagType: 'success' | 'danger' | 'warning' | 'info';
  hint: string;
}

/**
 * 一份文档现在**能不能被检索**（这一页要回答的问题）。
 *
 * <p>口径必须按"所有版本"算，不能只看最新版本——检索采用的是"任何一个 READY 且在有效期内的版本"。
 * 于是会出现两种最容易读错的组合，这里分开说清：</p>
 * <ul>
 *     <li><b>最新版失败、旧版仍可检索</b>：不能报成"不可检索"（会有人去重跑已经好用的资料），
 *         也不能报成一片绿（会漏掉刚失败的那一版）。所以标红并点明"旧版仍可检索"。</li>
 *     <li><b>已就绪但不在有效期</b>：状态是绿的但此刻查不到——标黄并写明是哪一侧越界。</li>
 * </ul>
 *
 * @param row 文档状态行
 * @param now 判定时刻（默认当前）
 */
export function documentReadiness(row: DocumentStatusRow | undefined, now: Date = new Date()): Readiness {
  const versions = row?.versions ?? [];
  const latest = latestVersion(versions);
  if (!latest) {
    return {
      code: 'NO_VERSION',
      label: '未登记版本',
      tagType: 'info',
      hint: '只登记了文档、还没产生版本：这种资料一定检索不到。'
    };
  }
  const retrievable = versions.filter((item) => versionRetrievable(item, now));
  if (latest.status === 'FAILED') {
    return retrievable.length
      ? {
          code: 'FAILED',
          label: '最新版失败（旧版仍可检索）',
          tagType: 'danger',
          hint: `最新版本处理失败，但仍有 ${retrievable.length} 个更早的版本可被检索：修好新版之前，检索用的是旧内容。`
        }
      : {
          code: 'FAILED',
          label: '处理失败',
          tagType: 'danger',
          hint: '最新版本处理失败且没有可用的旧版本：这份资料现在检索不到，需要重新解析并索引。'
        };
  }
  if (retrievable.length) {
    return {
      code: 'RETRIEVABLE',
      label: '可检索',
      tagType: 'success',
      hint: `有 ${retrievable.length} 个版本可被检索（状态已就绪且在有效期内）。`
    };
  }
  if (latest.status === 'READY') {
    return {
      code: 'READY_OUT_OF_WINDOW',
      label: '已就绪但不在有效期',
      tagType: 'warning',
      hint: '版本状态是「已就绪」，但此刻不在生效区间内（还没开始或已结束），所以检索不到——这是有效期问题，不是处理失败。'
    };
  }
  if (latest.status === 'RETIRED') {
    return {
      code: 'RETIRED',
      label: '已退役',
      tagType: 'info',
      hint: '该版本已退役：要恢复可用需要一个新版本重新解析并索引。'
    };
  }
  return {
    code: 'IN_PROGRESS',
    label: '处理中（还不能检索）',
    tagType: 'warning',
    hint: '版本还在处理管道里；只有跑到「已就绪」的版本才会进检索。'
  };
}

/** 文档表格的筛选：只看"现在检索不到"的那些（列表本身是全量，客户端筛不会得出假结论） */
export function isNotRetrievable(row: DocumentStatusRow | undefined, now: Date = new Date()): boolean {
  return documentReadiness(row, now).code !== 'RETRIEVABLE';
}

export interface DocumentStatusSummary {
  total: number;
  retrievable: number;
  inProgress: number;
  failed: number;
  other: number;
}

/** 文档状态汇总（"我登记的资料现在能不能被检索"） */
export function documentStatusSummary(
  rows?: DocumentStatusRow[],
  now: Date = new Date()
): DocumentStatusSummary {
  const list = rows ?? [];
  const summary: DocumentStatusSummary = { total: list.length, retrievable: 0, inProgress: 0, failed: 0, other: 0 };
  for (const row of list) {
    const code = documentReadiness(row, now).code;
    if (code === 'RETRIEVABLE') {
      summary.retrievable += 1;
    } else if (code === 'IN_PROGRESS') {
      summary.inProgress += 1;
    } else if (code === 'FAILED') {
      summary.failed += 1;
    } else {
      summary.other += 1;
    }
  }
  return summary;
}

/**
 * 文档状态汇总的一句话（页面顶部摘要）。
 *
 * <p>写明"共 N 个文档 · 可检索 K · 处理中 J · 失败 L · 还未就绪 M"，并把口径点出来：
 * 这份列表是**整个集合的全量**（不分页），所以数字可以直接当结论用。</p>
 */
export function documentStatusSummaryText(summary: DocumentStatusSummary): string {
  return (
    `共 ${summary.total} 个文档 · 可检索 ${summary.retrievable} · 处理中 ${summary.inProgress}` +
    ` · 失败 ${summary.failed} · 还未就绪 ${summary.other}`
  );
}

