import { describe, expect, it } from 'vitest';
import {
  directionStrategyKeys,
  directionStrategyLabel,
  directionStrategyValue
} from './directionStrategy';

/**
 * 方向卡片取舍维度中文口径的单测（v1 人工测试反馈：卡片上全是英文键）。
 *
 * @author creative
 */
describe('directionStrategyLabel', () => {
  it('已知维度翻成中文', () => {
    expect(directionStrategyLabel('scene')).toBe('场景');
    expect(directionStrategyLabel('lighting')).toBe('光线');
    expect(directionStrategyLabel('background')).toBe('背景色');
    expect(directionStrategyLabel('composition')).toBe('构图');
    expect(directionStrategyLabel('mood')).toBe('情绪/调性');
    expect(directionStrategyLabel('productRatio')).toBe('产品占比');
    expect(directionStrategyLabel('dnaBasis')).toBe('基因依据');
  });

  it('认不出的键原样返回——不静默丢掉一个真实存在的维度', () => {
    expect(directionStrategyLabel('brandNewKey')).toBe('brandNewKey');
  });
});

describe('directionStrategyKeys', () => {
  it('去掉 schema / differences（它们不是"取舍"，另有展示位置）', () => {
    expect(directionStrategyKeys({ schema: 'visual-direction/1', differences: ['a'], scene: 'x' }))
      .toEqual(['scene']);
  });

  it('按阅读顺序排，而不是按存储顺序排', () => {
    // 存储顺序是 background → scene → lighting → ……（生产的 JSON 就是这个顺序）
    const stored = {
      schema: 'visual-direction/1',
      background: '#F5F5F3',
      scene: '纯色底',
      lighting: '柔光',
      composition: '居中',
      mood: '克制',
      productRatio: '45%~65%',
      referenceSceneType: '纯色底',
      dnaBasis: '主色 未测'
    };
    expect(directionStrategyKeys(stored)).toEqual([
      'scene', 'lighting', 'background', 'composition', 'mood',
      'productRatio', 'referenceSceneType', 'dnaBasis'
    ]);
  });

  it('未知维度排在已知维度之后，且不丢', () => {
    expect(directionStrategyKeys({ foo: 1, scene: 'x', bar: 2 }))
      .toEqual(['scene', 'foo', 'bar']);
  });

  it('空策略给空数组（调用方据此不渲染明细行）', () => {
    expect(directionStrategyKeys(null)).toEqual([]);
    expect(directionStrategyKeys({})).toEqual([]);
    expect(directionStrategyKeys(undefined)).toEqual([]);
  });
});

describe('directionStrategyValue', () => {
  it('字符串原样、缺值给空串', () => {
    expect(directionStrategyValue({ scene: '纯色底' }, 'scene')).toBe('纯色底');
    expect(directionStrategyValue({ scene: '纯色底' }, 'mood')).toBe('');
    expect(directionStrategyValue(null, 'scene')).toBe('');
  });

  it('非字符串值序列化，绝不显示成 "undefined"', () => {
    expect(directionStrategyValue({ n: 3 }, 'n')).toBe('3');
    expect(directionStrategyValue({ list: ['a', 'b'] }, 'list')).toBe('["a","b"]');
  });
});
