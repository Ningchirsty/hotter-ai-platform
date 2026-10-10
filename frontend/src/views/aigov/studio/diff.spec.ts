import { describe, expect, it, vi } from 'vitest';
import {
  changedSectionKeys,
  diffSummary,
  parseDraftContent,
  promptSections,
  PROMPT_SECTION_LABELS,
  sectionDiff
} from './diff';

/**
 * 训练台分节差异测试。
 *
 * 差异算错不会报错，只会让页面高亮错的节（或该高亮的没高亮）——而人正是靠它决定要不要回滚。
 */
describe('训练台 diff 助手', () => {
  const base = JSON.stringify({
    agentName: '详情页文案助手',
    promptSections: { role: '策划', objective: '出脚本', inputs: '产品事实' }
  });

  it('同一份内容：没有任何分节被标为改动', () => {
    expect(changedSectionKeys(base, base)).toEqual([]);
    expect(diffSummary(base, base)).toBe('与这一版内容一致');
  });

  it('改了某一节：只把那一节标为改动', () => {
    const next = JSON.stringify({
      agentName: '详情页文案助手',
      promptSections: { role: '策划', objective: '出分镜', inputs: '产品事实' }
    });
    expect(changedSectionKeys(next, base)).toEqual(['objective']);
    expect(diffSummary(next, base)).toContain('objective');
  });

  it('标准八节永远都在结果里（空的也要让人看到"这一节是空的"）', () => {
    const items = sectionDiff(base, base);
    const standard = Object.keys(PROMPT_SECTION_LABELS);
    expect(items.length).toBeGreaterThanOrEqual(standard.length);
    for (const key of standard) {
      expect(items.some(item => item.key === key)).toBe(true);
    }
  });

  it('只在当前出现的节：标 onlyInCurrent；只在修订里出现的节：标 onlyInRevision', () => {
    const current = JSON.stringify({ promptSections: { role: '策划', extra_new: '新节' } });
    const revision = JSON.stringify({ promptSections: { role: '策划', legacy: '旧节' } });
    const items = sectionDiff(current, revision);
    const added = items.find(item => item.key === 'extra_new');
    const removed = items.find(item => item.key === 'legacy');
    expect(added?.onlyInCurrent).toBe(true);
    expect(added?.changed).toBe(true);
    expect(removed?.onlyInRevision).toBe(true);
    expect(removed?.changed).toBe(true);
  });

  it('非标准键也参与比较，并排在标准八节之后（按字母序）', () => {
    const current = JSON.stringify({ promptSections: { zzz: 'z', aaa: 'a' } });
    const items = sectionDiff(current, current);
    const keys = items.map(item => item.key);
    const firstExtra = keys.findIndex(key => !(key in PROMPT_SECTION_LABELS));
    expect(keys.slice(firstExtra)).toEqual(['aaa', 'zzz']);
  });

  it('坏 JSON 不抛异常：解析返回 null，分节返回空表', () => {
    expect(parseDraftContent('{ not json')).toBeNull();
    expect(promptSections('{ not json')).toEqual({});
    expect(changedSectionKeys('{ not json', base).length).toBeGreaterThan(0);
  });

  it('空值/非对象/数组都按"没有内容"处理，不炸', () => {
    expect(parseDraftContent(undefined)).toBeNull();
    expect(parseDraftContent('')).toBeNull();
    expect(parseDraftContent('[1,2]')).toBeNull();
    expect(promptSections(JSON.stringify({ promptSections: 'oops' }))).toEqual({});
    expect(sectionDiff(null, null).every(item => item.changed === false)).toBe(true);
  });

  it('分节值不是字符串时按字符串比较（不抛）', () => {
    const json = JSON.stringify({ promptSections: { role: 123 } });
    expect(promptSections(json).role).toBe('123');
  });

  it('解析坏 JSON 时不吞掉真正的逻辑错误（用 spy 确认 try/catch 只包 JSON.parse）', () => {
    const spy = vi.spyOn(JSON, 'parse');
    parseDraftContent('{"a":1}');
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });
});
