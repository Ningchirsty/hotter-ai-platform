import { describe, expect, it } from 'vitest';
import {
  ARTIFACT_KIND_TABS,
  declarationRows,
  emptySectionCount,
  generateSummary,
  kindLabel,
  kindPath,
  sectionRows
} from './presentation';
import { PROMPT_SECTION_LABELS } from '../studio/diff';

/**
 * 草案生成器展示助手测试。
 *
 * 这里的错都不报错：路径片段映射错会让"预览"404；声明字段读空会让人以为生成物是空的；
 * 分节漏一行会让人以为生成器没填那一节。
 */
describe('草案生成器展示助手', () => {
  const content: Record<string, any> = {
    agentName: 'SKU 详情助手',
    agentCategory: 'PLANNING',
    providerCapability: 'text_generation',
    allowExternal: 'N',
    coreCapabilities: ['text_generation'],
    allowedSkills: ['brand_brief'],
    allowedTools: ['brand_brief', 'image_search'],
    knowledgeScope: ['brand-kb'],
    promptSections: {
      role: '电商 SKU 资料助手',
      objective: '把 SKU 事实整理成可用的卖点',
      inputs: '',
      workflow_rules: '先查事实再写',
      tools_and_skills: '',
      constraints: '',
      output_contract: '',
      uncertainty_policy: ''
    }
  };

  it('三类页签的路径片段必须与后端一致（拼错就是 404）', () => {
    expect(ARTIFACT_KIND_TABS.map(item => item.path)).toEqual(['skill', 'child-agent', 'action']);
    expect(kindPath('SKILL')).toBe('skill');
    expect(kindPath('CHILD_AGENT')).toBe('child-agent');
    expect(kindPath('ACTION')).toBe('action');
  });

  it('类型中文名；认不出的 code 原样返回，路径片段认不出给空串', () => {
    expect(kindLabel('SKILL')).toBe('技能草案');
    expect(kindLabel('CHILD_AGENT')).toBe('子 Agent 草案');
    expect(kindLabel('ACTION')).toBe('快捷指令草案');
    expect(kindLabel('NOPE')).toBe('NOPE');
    expect(kindLabel(null)).toBe('未知');
    expect(kindPath('NOPE')).toBe('');
    expect(kindPath(undefined)).toBe('');
  });

  it('★ 声明字段：数组用「、」连、空值跳过、允许外发显示成人读的中文', () => {
    const rows = declarationRows(content);
    const map = Object.fromEntries(rows.map(row => [row.label, row.value]));
    expect(map['Agent 名称']).toBe('SKU 详情助手');
    expect(map['类别']).toBe('PLANNING');
    expect(map['所需能力']).toBe('text_generation');
    expect(map['允许外发']).toBe('否（N）');
    expect(map['允许工具']).toBe('brand_brief、image_search');
    expect(map['知识范围']).toBe('brand-kb');
    expect(map['技能（allowedSkills）']).toBe('brand_brief');
    // 空值不占行（也不要铺一堆"（未填）"）
    expect(map['主要责任']).toBeUndefined();
    expect(map['禁止事项']).toBeUndefined();
  });

  it('声明字段：内容缺失时不炸，返回空清单', () => {
    expect(declarationRows(undefined)).toEqual([]);
    expect(declarationRows({})).toEqual([]);
  });

  it('★ 分节：永远八行且顺序固定（空的也要让人看到"这一节是空的"）', () => {
    const rows = sectionRows(content);
    expect(rows.map(row => row.key)).toEqual(Object.keys(PROMPT_SECTION_LABELS));
    expect(rows).toHaveLength(8);
    expect(rows[0].label).toBe(PROMPT_SECTION_LABELS.role);
    expect(rows.find(row => row.key === 'workflow_rules')?.empty).toBe(false);
    expect(rows.find(row => row.key === 'inputs')?.empty).toBe(true);
    expect(emptySectionCount(content)).toBe(5);
  });

  it('分节：内容缺失时八行都在且都标空（不返回空清单）', () => {
    const rows = sectionRows(undefined);
    expect(rows).toHaveLength(8);
    expect(rows.every(row => row.empty)).toBe(true);
    expect(emptySectionCount(undefined)).toBe(8);
  });

  it('★ 摘要：预览说"预览 + 几节待补"，已生成说清落到哪个草稿的哪一版', () => {
    expect(generateSummary(undefined)).toBe('还没有生成结果');
    expect(generateSummary({ kind: 'SKILL', sourceDraftCode: 'SKILL-A', content })).toBe(
      '技能草案「SKILL-A」生成预览：5 节留空待补充'
    );
    expect(
      generateSummary({
        kind: 'ACTION',
        sourceDraftCode: 'ACT-A',
        content,
        revisionCreated: true,
        targetDraftId: 9001,
        revision: 3
      })
    ).toBe('快捷指令草案「ACT-A」已生成到草稿 #9001 的修订 #3');
  });
});
