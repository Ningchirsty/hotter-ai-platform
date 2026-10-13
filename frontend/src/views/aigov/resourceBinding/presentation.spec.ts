import { describe, expect, it } from 'vitest';
import {
  bindingDeleteConfirmText,
  declaredHint,
  declaredItems,
  declaredOptionLabel,
  forbiddenNote,
  hasDeletableId,
  resourceTypeLabel,
  scopeLabel,
  scopeSummary
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

  it('范围摘要：四列一起给，空列显式写成「不限」', () => {
    expect(scopeSummary(null)).toBe('不限 / 不限 / 不限 / 不限');
    expect(scopeSummary({})).toBe('不限 / 不限 / 不限 / 不限');
    expect(scopeSummary({ companyId: 88, scenarioCode: 'PLANNING' })).toBe('88 / 不限 / PLANNING / 不限');
  });

  it('★ 删除确认文案必须同时说清三件事：删哪一条、什么范围、删完能否重绑', () => {
    const text = bindingDeleteConfirmText({
      bindingId: '123',
      resourceType: 'TOOL',
      resourceCode: 'mt_verify_tool',
      companyId: 88
    });
    // ① 删哪一条（类型 + 编码）
    expect(text).toContain('工具');
    expect(text).toContain('mt_verify_tool');
    // ② 什么范围（否则按编码看一眼就点了，可能删错范围）
    expect(text).toContain('范围：88 / 不限 / 不限 / 不限');
    // ③ 删完能否重绑（少这句，人就不敢点，绑错的范围会永远卡在那里）
    expect(text).toContain('删除后同范围可以重新绑定');

    // 缺字段也不炸：编码缺失时明确写出来，而不是拼出「工具「undefined」」
    const bare = bindingDeleteConfirmText({});
    expect(bare).toContain('（无编码）');
    expect(bare).not.toContain('undefined');
  });

  it('★ 没有可用绑定ID时不许发删除请求（空 ID 会打到一个不存在的地址上）', () => {
    expect(hasDeletableId({ bindingId: '2109546394996940802' })).toBe(true);
    expect(hasDeletableId({ bindingId: 88 })).toBe(true);
    expect(hasDeletableId({})).toBe(false);
    expect(hasDeletableId(null)).toBe(false);
    expect(hasDeletableId({ bindingId: '' })).toBe(false);
    expect(hasDeletableId({ bindingId: null })).toBe(false);
    // 服务端真发过这种"字符串化的空值"：它不是空，但也不能拿去拼 URL
    expect(hasDeletableId({ bindingId: 'undefined' })).toBe(false);
    expect(hasDeletableId({ bindingId: '   ' })).toBe(false);
  });
});
