import { describe, expect, it } from 'vitest';
import { dnaIssueTarget, hasMeasurableIssue } from './dnaIssues';

/**
 * 「基因为什么锁不上 → 去填哪一项」映射的单测（v1 人工测试反馈）。
 *
 * 断言里用的 issue 原文**逐字取自**后端 `VisualDnaSchema.validate`
 * （`help: 主色未设置 / 主色不是合法的 #RRGGBB：xxx / 产品占比区间颠倒：下限 80 大于上限 60`…），
 * 这样后端改措辞时这里会先红，而不是页面上悄悄少一个按钮。
 *
 * @author creative
 */
describe('dnaIssueTarget', () => {
  it('颜色类：四种颜色各自对上自己的输入框', () => {
    expect(dnaIssueTarget('主色未设置')?.field).toBe('colorPrimary');
    expect(dnaIssueTarget('主色不是合法的 #RRGGBB：RED')?.field).toBe('colorPrimary');
    expect(dnaIssueTarget('辅色不是合法的 #RRGGBB：xyz')?.field).toBe('colorSecondary');
    expect(dnaIssueTarget('点缀色不是合法的 #RRGGBB：xyz')?.field).toBe('colorAccent');
    expect(dnaIssueTarget('背景色未设置')?.field).toBe('colorBg');
  });

  it('「背景色」不会被「主色」规则抢走（两者字面上都含"色"）', () => {
    expect(dnaIssueTarget('背景色不是合法的 #RRGGBB：#1234567')?.label).toBe('背景色');
  });

  it('档位类：饱和度/对比度/留白各对各', () => {
    expect(dnaIssueTarget('饱和度取值非法（应为 LOW/MEDIUM/HIGH）：X')?.field).toBe('saturation');
    expect(dnaIssueTarget('对比度取值非法（应为 LOW/MEDIUM/HIGH）：X')?.field).toBe('contrastLevel');
    expect(dnaIssueTarget('留白取值非法（应为 LOW/MEDIUM/HIGH）：X')?.field).toBe('whitespaceLevel');
  });

  it('光线类：类型与光位分开', () => {
    expect(dnaIssueTarget('光线类型取值非法：NEON')?.field).toBe('lightingType');
    expect(dnaIssueTarget('光位取值非法：TOP')?.field).toBe('lightingDir');
  });

  it('产品占比：同时指向下限与上限（只高亮一个会让人以为改一个就行）', () => {
    const target = dnaIssueTarget('产品占比区间颠倒：下限 80 大于上限 60');
    expect(target?.field).toBe('productRatioMin');
    expect(target?.also).toBe('productRatioMax');
    expect(dnaIssueTarget('产品占比区间未设置')?.field).toBe('productRatioMin');
  });

  it('风格关键词为空 → 指向关键词（但它不是"实测"能给值的项）', () => {
    expect(dnaIssueTarget('风格关键词为空：出图提示词将缺少统一语气')?.field).toBe('styleKeywords');
  });

  it('认不出的 issue 不给动作——宁可不给按钮，也不要把人送到无关的输入框', () => {
    expect(dnaIssueTarget('缺少 schema 标识（应为 visual-dna/1）')).toBeNull();
    expect(dnaIssueTarget('将来后端新加的一条校验')).toBeNull();
    expect(dnaIssueTarget('')).toBeNull();
    expect(dnaIssueTarget(null)).toBeNull();
  });
});

describe('hasMeasurableIssue', () => {
  it('有一次实测能给的项就算「可推荐」', () => {
    expect(hasMeasurableIssue(['主色未设置'])).toBe(true);
    expect(hasMeasurableIssue(['产品占比区间颠倒：下限 80 大于上限 60'])).toBe(true);
  });

  it('只有"测不出来"的项（风格关键词/结构问题）时不算可推荐——不拿按钮骗人点', () => {
    expect(hasMeasurableIssue(['风格关键词为空：出图提示词将缺少统一语气'])).toBe(false);
    expect(hasMeasurableIssue(['缺少 schema 标识（应为 visual-dna/1）'])).toBe(false);
    expect(hasMeasurableIssue([])).toBe(false);
    expect(hasMeasurableIssue(null)).toBe(false);
  });
});
