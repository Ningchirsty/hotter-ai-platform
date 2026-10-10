/**
 * 训练台：草稿内容的解析与「分节差异」计算（纯函数）。
 *
 * 为什么把它抽出来单测：训练台的核心是"改了什么、比哪一版"——
 * 差异算错的表现是**静默的**（页面照样渲染，只是高亮了错误的节，或该高亮的没高亮），
 * 而人正是靠这个决定"要不要回滚"。纯函数才能把这几个分支钉死。
 *
 * @author ai-gov
 */

/** Prompt 八个标准分节的展示名（与后端 AigStudioDraftContent.PROMPT_SECTION_KEYS 一一对应） */
export const PROMPT_SECTION_LABELS: Record<string, string> = {
  role: '角色（role）',
  objective: '目标（objective）',
  inputs: '输入（inputs）',
  workflow_rules: '工作规则（workflow_rules）',
  tools_and_skills: '工具与技能（tools_and_skills）',
  constraints: '约束（constraints）',
  output_contract: '输出契约（output_contract）',
  uncertainty_policy: '不确定性处理（uncertainty_policy）'
};

/** 分节差异的一项 */
export interface StudioSectionDiff {
  /** 分节键 */
  key: string;
  /** 展示名 */
  label: string;
  /** 当前内容里的正文 */
  current: string;
  /** 被比较修订里的正文 */
  revision: string;
  /** 两者是否不同 */
  changed: boolean;
  /** 只在修订里有（当前已删） */
  onlyInRevision: boolean;
  /** 只在当前有（修订里还没有） */
  onlyInCurrent: boolean;
}

/** 草稿内容（宽松类型：页面只展示，不做结构校验——校验在后端） */
export type StudioDraftContent = Record<string, any>;

/**
 * 解析草稿内容 JSON；解析不了返回 null（页面据此显示"内容不可解析"，不抛异常）。
 *
 * @param json 内容 JSON
 * @returns 解析结果或 null
 */
export function parseDraftContent(json?: string | null): StudioDraftContent | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed as StudioDraftContent;
    return null;
  } catch {
    return null;
  }
}

/**
 * 取某份内容里的分节表（拿不到就返回空对象）。
 *
 * @param json 内容 JSON
 * @returns 分节键 → 正文
 */
export function promptSections(json?: string | null): Record<string, string> {
  const content = parseDraftContent(json);
  const sections = content?.promptSections;
  if (!sections || typeof sections !== 'object' || Array.isArray(sections)) return {};
  const result: Record<string, string> = {};
  for (const [key, value] of Object.entries(sections as Record<string, unknown>)) {
    result[key] = value === null || value === undefined ? '' : String(value);
  }
  return result;
}

/**
 * 计算「当前内容」相对「某一版修订」的分节差异。
 *
 * 标准八节永远出现在结果里（即使两边都没有，也让人看到"这一节是空的"），
 * 非标准的额外键按字母序追加在最后——它们同样会被比较，因为"多出来的一节"也是改动。
 *
 * @param currentJson  当前内容 JSON
 * @param revisionJson 被比较的修订内容 JSON
 * @returns 差异列表（顺序：标准八节 → 额外键按字母序）
 */
export function sectionDiff(currentJson?: string | null, revisionJson?: string | null): StudioSectionDiff[] {
  const current = promptSections(currentJson);
  const revision = promptSections(revisionJson);
  const standard = Object.keys(PROMPT_SECTION_LABELS);
  const extras = Array.from(new Set([...Object.keys(current), ...Object.keys(revision)]))
    .filter(key => !standard.includes(key))
    .toSorted();
  return [...standard, ...extras].map(key => {
    const cur = current[key] ?? '';
    const rev = revision[key] ?? '';
    const onlyInRevision = cur === '' && rev !== '';
    const onlyInCurrent = cur !== '' && rev === '';
    return {
      key,
      label: PROMPT_SECTION_LABELS[key] ?? key,
      current: cur,
      revision: rev,
      changed: cur !== rev,
      onlyInRevision,
      onlyInCurrent
    };
  });
}

/**
 * 只返回"有改动"的分节键（用于在列表/修订历史里显示"这一版改了哪几节"）。
 *
 * @param currentJson  当前内容 JSON
 * @param revisionJson 被比较的修订内容 JSON
 * @returns 有改动的分节键
 */
export function changedSectionKeys(currentJson?: string | null, revisionJson?: string | null): string[] {
  return sectionDiff(currentJson, revisionJson)
    .filter(item => item.changed)
    .map(item => item.key);
}

/**
 * 差异摘要文案（用于页面上那句"相对这一版改了 3 节"）。
 *
 * @param currentJson  当前内容 JSON
 * @param revisionJson 被比较的修订内容 JSON
 * @returns 人读摘要
 */
export function diffSummary(currentJson?: string | null, revisionJson?: string | null): string {
  const changed = changedSectionKeys(currentJson, revisionJson);
  if (changed.length === 0) return '与这一版内容一致';
  const labels = changed.map(key => PROMPT_SECTION_LABELS[key] ?? key).join('、');
  return `与这一版相比，改动 ${changed.length} 节：${labels}`;
}
