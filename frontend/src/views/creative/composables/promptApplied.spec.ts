import { describe, expect, it } from 'vitest';
import { appliedLabel, appliedText } from './promptApplied';

/**
 * 提示词「用到的维度」中文口径的单测（v1 人工测试反馈：英文 + 括号里的计数像编号）。
 *
 * @author creative
 */
describe('appliedLabel', () => {
  it('固定维度翻成中文，并标明它来自基因还是品牌要求', () => {
    expect(appliedLabel('colors')).toBe('基因·配色');
    expect(appliedLabel('styleKeywords')).toBe('基因·风格关键词');
    expect(appliedLabel('saturation/contrastLevel')).toBe('基因·饱和度与对比度');
    expect(appliedLabel('screenText(屏文案)')).toBe('本屏屏文案');
    expect(appliedLabel('module.visualRules')).toBe('该屏的模块视觉表达');
  });

  it('带条数的维度：括号里的数字不再是"编号"，而是"几条"', () => {
    expect(appliedLabel('brief.mustShow(2)')).toBe('品牌必显信息 2 条');
    expect(appliedLabel('brief.mainPush(3)')).toBe('品牌主推卖点 3 条');
    expect(appliedLabel('brief.forbiddenWords(0)')).toBe('品牌禁用词 0 条');
  });

  it('认不出的码原样返回——不静默丢掉"用到了某维度"这件事', () => {
    expect(appliedLabel('brandNewDimension')).toBe('brandNewDimension');
    expect(appliedLabel('brief.unknown(2)')).toBe('brief.unknown(2)');
  });

  it('空值给空串，不产生多余的顿号', () => {
    expect(appliedLabel('')).toBe('');
    expect(appliedLabel('   ')).toBe('');
  });
});

describe('appliedText', () => {
  it('拼成一句话用的中文串', () => {
    expect(appliedText(['colors', 'brief.mustShow(2)', 'avoidKeywords']))
      .toBe('基因·配色、品牌必显信息 2 条、基因·禁忌词');
  });

  it('空列表给空串（调用方据此显示"—"）', () => {
    expect(appliedText([])).toBe('');
    expect(appliedText(null)).toBe('');
    expect(appliedText(undefined)).toBe('');
  });

  it('重复维度只出现一次（同一句话里说两遍会让人以为用了两次）', () => {
    expect(appliedText(['colors', 'colors', 'brief.mustShow(1)', 'brief.mustShow(1)']))
      .toBe('基因·配色、品牌必显信息 1 条');
  });
});
