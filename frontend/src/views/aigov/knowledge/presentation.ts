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
