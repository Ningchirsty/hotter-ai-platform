import { describe, expect, it } from 'vitest';
import {
  durationText,
  mergeScreenMap,
  productVerdictLabel,
  qaVerdictLabel,
  screenLabel,
  screenTypeDesc,
  sizeText
} from './generationText';
import type { DpGenerationVO, DpStoryboardScreenVO } from '@/api/creative/types';

/**
 * 出图候选展示口径的单测（v1 人工测试反馈期间补的）。
 *
 * <p>这个模块是"生产页与出图步共用的一套口径"，但一直没有自己的测试。
 * 这轮在**无项目状态**的冒烟里抓到一处真实错误，正是出在这里的用法上：
 * 跨项目预览同时显示「用途 卖点」与「屏 屏 2104582808140242946」——
 * 屏号退化成了 id，因为「用途」用的屏表与「屏」用的不是同一张。</p>
 *
 * @author creative
 */
const screen = (id: string, screenNo: string, desc: string): DpStoryboardScreenVO =>
  ({ id, screenNo, screenTypeDesc: desc } as DpStoryboardScreenVO);

const gen = (over: Partial<DpGenerationVO>): DpGenerationVO =>
  ({ id: '1', taskId: '9', ...over } as DpGenerationVO);

describe('screenLabel / screenTypeDesc', () => {
  it('取到屏时给屏号与类型描述', () => {
    const map = { '1001': screen('1001', 'S02', '卖点') };
    const row = gen({ screenId: '1001' });
    expect(screenLabel(row, map)).toBe('S02');
    expect(screenTypeDesc(row, map)).toBe('卖点');
  });

  it('取不到屏时屏号退化成 id（这是最后手段），类型描述给「—」而不是编一个', () => {
    const row = gen({ screenId: '2104582808140242946' });
    expect(screenLabel(row, {})).toBe('屏 2104582808140242946');
    expect(screenTypeDesc(row, {})).toBe('—');
  });

  it('候选本来就不属于某一屏时说「未归属屏」，不编一个 id 出来', () => {
    expect(screenLabel(gen({}), {})).toBe('未归属屏');
    expect(screenLabel(gen({ screenId: null as unknown as undefined }), {})).toBe('未归属屏');
  });
});

describe('mergeScreenMap', () => {
  it('把按需取到的那一屏并进屏表（跨项目预览的正确性靠它）', () => {
    const extra = screen('2104582808140242946', 'S02', '卖点');
    const merged = mergeScreenMap({}, extra);
    const row = gen({ screenId: '2104582808140242946' });
    // 关键回归：不合并的话这里会退化成「屏 2104582808140242946」
    expect(screenLabel(row, merged)).toBe('S02');
    expect(screenTypeDesc(row, merged)).toBe('卖点');
  });

  it('extra 为空时原样返回，不制造空表以外的副作用', () => {
    const map = { '1': screen('1', 'S01', '主图') };
    expect(mergeScreenMap(map, null)).toEqual(map);
    expect(mergeScreenMap(map, undefined)).toEqual(map);
  });

  it('同一屏在两边都在时以 extra 为准（它是最新按需取到的）', () => {
    const map = { '5': screen('5', 'S02', '旧描述') };
    const merged = mergeScreenMap(map, screen('5', 'S02', '卖点'));
    expect(screenTypeDesc(gen({ screenId: '5' }), merged)).toBe('卖点');
  });
});

describe('结论与数字的展示', () => {
  it('质检/产品基准结论为空时显示「未质检」，绝不当成通过', () => {
    expect(qaVerdictLabel(undefined)).toBe('未质检');
    expect(productVerdictLabel(null as unknown as undefined)).toBe('未质检');
    expect(qaVerdictLabel('CONSISTENT')).toBe('一致');
  });

  it('尺寸与耗时：没有值就给「—」，不显示 0 或 undefined', () => {
    expect(sizeText(gen({ outputWidth: 512, outputHeight: 512 }))).toBe('512×512');
    expect(sizeText(gen({}))).toBe('—');
    expect(durationText(3000)).toBe('3.0s');
    expect(durationText(undefined)).toBe('—');
  });
});
