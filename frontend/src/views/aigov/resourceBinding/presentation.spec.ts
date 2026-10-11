import { describe, expect, it } from 'vitest';
import {
  declaredHint,
  declaredItems,
  declaredOptionLabel,
  forbiddenNote,
  resourceTypeLabel,
  scopeLabel
} from './presentation';
import type { AigAgentDeclaredResourceVO } from '@/api/aigov/resourceBinding/types';

/**
 * 工具/知识绑定展示助手测试。
 *
 * 这里的每一句都是"错了不报错"：标签映射错只显示错的中文；声明读空了会让下拉变空，
 * 人只会以为"这个版本没声明"；"已有启用绑定"标错会让人以为不能再绑。
 */
describe('工具/知识绑定展示助手', () => {
  const declared: AigAgentDeclaredResourceVO = {
    agentVersionId: 1,
    version: '0.1.3',
    allowedTools: [
      { code: 'brand_brief', bound: true },
      { code: 'image_search', bound: false }
    ],
    forbiddenTools: ['shell', 'ssh'],
    knowledgeScopes: [{ code: 'brand-kb', bound: false }]
  };

  it('类型中文映射与服务端枚举一致，认不出的 code 原样返回', () => {
    expect(resourceTypeLabel('TOOL')).toBe('工具');
    expect(resourceTypeLabel('KNOWLEDGE')).toBe('知识范围');
    expect(resourceTypeLabel('SOMETHING')).toBe('SOMETHING');
    expect(resourceTypeLabel(null)).toBe('未知');
  });

  it('按类型取声明项：TOOL 取 allowedTools、KNOWLEDGE 取 knowledgeScopes', () => {
    expect(declaredItems(declared, 'TOOL').map(i => i.code)).toEqual(['brand_brief', 'image_search']);
    expect(declaredItems(declared, 'KNOWLEDGE').map(i => i.code)).toEqual(['brand-kb']);
  });

  it('★ 声明读取是防御的：缺字段 / 非数组 / 形状不对都不炸，只是取不到', () => {
    expect(declaredItems(undefined, 'TOOL')).toEqual([]);
    expect(declaredItems({}, 'TOOL')).toEqual([]);
    // @ts-expect-error 故意给非数组
    expect(declaredItems({ allowedTools: 'oops' }, 'TOOL')).toEqual([]);
    // @ts-expect-error 故意给形状不对的项
    expect(declaredItems({ allowedTools: [{ bound: true }, null] }, 'TOOL')).toEqual([]);
  });

  it('★「已有启用绑定」只标注、不禁用（换个范围再绑一条是合法的）', () => {
    expect(declaredOptionLabel({ code: 'brand_brief', bound: true })).toBe('brand_brief（已有启用绑定）');
    expect(declaredOptionLabel({ code: 'brand_brief', bound: false })).toBe('brand_brief');
  });

  it('未声明任何项时给出可操作提示，并点名要补的字段', () => {
    expect(declaredHint(declared, 'TOOL')).toBe('');
    const empty = declaredHint({ allowedTools: [] }, 'TOOL');
    expect(empty).toContain('没有声明任何工具');
    expect(empty).toContain('allowed_tools');
    expect(declaredHint({ knowledgeScopes: [] }, 'KNOWLEDGE')).toContain('knowledge_scope_json');
    expect(declaredHint(null, 'TOOL')).toContain('读取声明');
  });

  it('禁止工具提示：没有禁止项时返回空串（不输出"禁止：（无）"这种噪声）', () => {
    expect(forbiddenNote(declared)).toBe('该版本明确禁止：shell、ssh（这些工具绑不上，服务端会拒绝）');
    expect(forbiddenNote({ forbiddenTools: [] })).toBe('');
    expect(forbiddenNote(undefined)).toBe('');
  });

  it('范围展示：空=不限（必须显式说出来，否则会被以为漏填）', () => {
    expect(scopeLabel(null)).toBe('不限');
    expect(scopeLabel(undefined)).toBe('不限');
    expect(scopeLabel('')).toBe('不限');
    expect(scopeLabel(88)).toBe('88');
    expect(scopeLabel('PLANNING')).toBe('PLANNING');
  });
});
