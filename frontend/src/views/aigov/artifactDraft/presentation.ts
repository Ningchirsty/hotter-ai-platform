import { PROMPT_SECTION_LABELS } from '../studio/diff';
import type {
  AigStudioArtifactGenerateVO,
  AigStudioArtifactKind
} from '@/api/aigov/artifactDraft/types';

/**
 * 草案生成器展示助手（纯函数）。
 *
 * <p>为什么抽出来单测：这几句错了都不报错——类型路径片段映射错会 404（点"预览"没反应）；
 * 声明字段读空会让人以为"生成物里什么都没有"；分节渲染漏掉一节会让人以为生成器没填。</p>
 *
 * @author ai-gov
 */

/** 三个页签（`path` 必须与后端 AigStudioArtifactKindEnum.pathSegment 一致，否则 404） */
export const ARTIFACT_KIND_TABS: Array<{ value: AigStudioArtifactKind; label: string; path: string }> = [
  { value: 'SKILL', label: '技能草案', path: 'skill' },
  { value: 'CHILD_AGENT', label: '子 Agent 草案', path: 'child-agent' },
  { value: 'ACTION', label: '快捷指令草案', path: 'action' }
];

/**
 * 草案类型中文名；认不出的 code 原样返回。
 *
 * @param kind 类型 code
 * @returns 展示名
 */
export function kindLabel(kind?: string | null): string {
  if (!kind) return '未知';
  return ARTIFACT_KIND_TABS.find(item => item.value === kind)?.label ?? kind;
}

/**
 * 类型 → 接口路径片段；认不出返回空串（调用方据此禁用按钮，而不是拼一个必然 404 的路径）。
 *
 * @param kind 类型 code
 * @returns 路径片段
 */
export function kindPath(kind?: string | null): string {
  if (!kind) return '';
  return ARTIFACT_KIND_TABS.find(item => item.value === kind)?.path ?? '';
}

/** 一行声明字段（生成物里那些"不在 Prompt 分节里"的部分） */
export interface DeclarationRow {
  label: string;
  value: string;
}

/**
 * 把生成物里的声明字段抽成可读行（跳过空值：空值在页面上显示成"未声明"比显示空白更清楚，
 * 但也不要为此铺一堆"（未填）"）。
 *
 * @param content 生成物内容
 * @returns 行清单
 */
export function declarationRows(content?: Record<string, any> | null): DeclarationRow[] {
  if (!content) return [];
  const rows: DeclarationRow[] = [];
  const push = (label: string, value: unknown) => {
    const text = renderValue(value);
    if (text) rows.push({ label, value: text });
  };
  push('Agent 名称', content.agentName);
  push('类别', content.agentCategory);
  push('所需能力', content.providerCapability);
  push('允许外发', content.allowExternal === 'Y' ? '是（Y）' : content.allowExternal === 'N' ? '否（N）' : undefined);
  push('核心能力（人读）', content.coreCapabilities);
  push('技能（allowedSkills）', content.allowedSkills);
  push('允许工具', content.allowedTools);
  push('禁止工具', content.forbiddenTools);
  push('知识范围', content.knowledgeScope);
  push('角色定位', content.roleDescription);
  push('主要责任', content.objective);
  push('禁止事项', content.prohibitions);
  push('输出成果说明', content.outputSummary);
  return rows;
}

/** 一个 Prompt 分节行 */
export interface SectionRow {
  key: string;
  label: string;
  text: string;
  /** 该节为空（生成器不编内容，页面要如实标出来） */
  empty: boolean;
}

/**
 * 八分节渲染行（顺序固定：与后端 PROMPT_SECTION_KEYS 一致）。
 *
 * @param content 生成物内容
 * @returns 行清单（永远八行——让人看到"这一节是空的"）
 */
export function sectionRows(content?: Record<string, any> | null): SectionRow[] {
  const sections = (content?.promptSections ?? {}) as Record<string, unknown>;
  return Object.keys(PROMPT_SECTION_LABELS).map(key => {
    const raw = sections[key];
    const text = raw === null || raw === undefined ? '' : String(raw);
    return { key, label: PROMPT_SECTION_LABELS[key], text, empty: text.trim() === '' };
  });
}

/**
 * 空分节数量（页面上的"N 节留空待补充"）。
 *
 * @param content 生成物内容
 * @returns 数量
 */
export function emptySectionCount(content?: Record<string, any> | null): number {
  return sectionRows(content).filter(row => row.empty).length;
}

/**
 * 生成结果的一句话摘要（预览/生成两种状态都要能说清"这份生成物是什么"）。
 *
 * @param generated 生成结果
 * @returns 摘要
 */
export function generateSummary(generated?: AigStudioArtifactGenerateVO | null): string {
  if (!generated) return '还没有生成结果';
  const kind = kindLabel(generated.kind);
  const code = generated.sourceDraftCode || generated.sourceDraftId || '-';
  const empty = emptySectionCount(generated.content);
  if (generated.revisionCreated) {
    return `${kind}「${code}」已生成到草稿 #${generated.targetDraftId} 的修订 #${generated.revision}`;
  }
  return `${kind}「${code}」生成预览：${empty} 节留空待补充`;
}

/**
 * 值渲染：数组用「、」连，其余转字符串；空值返回空串（由调用方决定要不要显示）。
 *
 * @param value 值
 * @returns 文本
 */
function renderValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '';
  if (Array.isArray(value)) return value.filter(item => item !== null && item !== undefined && item !== '').join('、');
  return String(value);
}
