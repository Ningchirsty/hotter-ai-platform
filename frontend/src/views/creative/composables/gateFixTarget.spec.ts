import { describe, expect, it } from 'vitest';
import { GATE_FIX_TARGETS, gateFixRoute, gateFixTarget } from './gateFixTarget';

/**
 * 视觉门「去哪儿补」对照的单测（v1 人工测试反馈：未满足项没有跳转）。
 *
 * @author creative
 */
describe('gateFixTarget', () => {
  it('设计侧能补的项指到创作域对应页面', () => {
    expect(gateFixTarget('DNA_LOCKED')?.path).toBe('/creative/dna');
    expect(gateFixTarget('REFERENCE_IMAGE')?.path).toBe('/creative/project');
    expect(gateFixTarget('DIRECTION_SELECTED')?.path).toBe('/creative/storyboard');
    expect(gateFixTarget('STORYBOARD_LOCKED')?.path).toBe('/creative/storyboard');
  });

  it('品牌部的项指到内容任务（不是设计侧能自己做的）', () => {
    for (const code of ['BRAND_TONE_CONFIRMED', 'BRAND_BRIEF_CONFIRMED', 'FORBIDDEN_WORDS_DECLARED']) {
      expect(gateFixTarget(code)?.path).toBe('/business/content/task');
      expect(gateFixTarget(code)?.carriesTaskId).toBe(false);
    }
  });

  it('配置里的每个码都能给出文案（不留空按钮）', () => {
    for (const [code, target] of Object.entries(GATE_FIX_TARGETS)) {
      expect(target.label.length, `${code} 缺按钮文案`).toBeGreaterThan(0);
      expect(target.path.startsWith('/'), `${code} 的路径不合法`).toBe(true);
    }
  });

  it('认不出的码返回 null —— 宁可不给按钮，也不指错路', () => {
    expect(gateFixTarget('SOMETHING_NEW')).toBeNull();
    expect(gateFixTarget('')).toBeNull();
    expect(gateFixTarget(null)).toBeNull();
    expect(gateFixTarget(undefined)).toBeNull();
  });
});

describe('gateFixRoute', () => {
  it('支持深链的页面带上 taskId', () => {
    expect(gateFixRoute(gateFixTarget('DNA_LOCKED')!, '2104582766641799169'))
      .toBe('/creative/dna?taskId=2104582766641799169');
  });

  it('不支持的页面不硬塞 taskId（塞了反而让人以为已经定位到那一条）', () => {
    expect(gateFixRoute(gateFixTarget('BRAND_BRIEF_CONFIRMED')!, '2104582766641799169'))
      .toBe('/business/content/task');
  });

  it('没有 taskId 时只给路径，不产生 "?taskId=" 这种半截地址', () => {
    expect(gateFixRoute(gateFixTarget('DNA_LOCKED')!, '')).toBe('/creative/dna');
    expect(gateFixRoute(gateFixTarget('DNA_LOCKED')!, null)).toBe('/creative/dna');
    expect(gateFixRoute(gateFixTarget('DNA_LOCKED')!)).toBe('/creative/dna');
  });
});
