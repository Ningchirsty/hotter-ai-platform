import { describe, expect, it } from 'vitest';
import {
  ACTION_OPTIONS,
  QUEUE_ACTION_FILTERS,
  QUEUE_REASON_FILTERS,
  REASON_OPTIONS,
  VERDICT_OPTIONS,
  actionLabel,
  faultClassRows,
  handleValidation,
  queueActionHint,
  rateText,
  reasonLabel,
  verdictLabel,
  verdictTagType
} from './presentation';

/**
 * 反馈页展示口径测试（设计 §6.9）。
 *
 * <p>最要命的是"提交前校验与后端分叉"：界面允许提交、后端拒绝，用户点下去才知道。</p>
 */
describe('feedback presentation', () => {
  it('原因/结论/动作词表与后端一致且非空', () => {
    expect(REASON_OPTIONS).toHaveLength(6);
    expect(VERDICT_OPTIONS.map(item => item.code)).toEqual(['PENDING', 'CONFIRMED', 'REJECTED']);
    expect(ACTION_OPTIONS.map(item => item.code)).toEqual([
      'ADD_KNOWLEDGE',
      'CHANGE_RULE',
      'ADD_CASE',
      'CLOSE'
    ]);
  });

  it('按码取文案；认不出的码原样展示而不是空白', () => {
    expect(reasonLabel('FACT_ERROR')).toBe('事实有误');
    expect(verdictLabel('CONFIRMED')).toBe('已确认问题');
    expect(actionLabel('ADD_CASE')).toBe('加入用例');
    expect(reasonLabel('NO_SUCH')).toBe('NO_SUCH');
    expect(reasonLabel(undefined)).toBe('—');
  });

  it('结论标签色：待处理=橙、已确认=绿、其余=灰', () => {
    expect(verdictTagType('PENDING')).toBe('warning');
    expect(verdictTagType('CONFIRMED')).toBe('success');
    expect(verdictTagType('REJECTED')).toBe('info');
    expect(verdictTagType(undefined)).toBe('info');
  });

  it('★ 提交前校验与后端同口径：确认问题必须给动作；PENDING 不是可提交结论', () => {
    expect(handleValidation(undefined)).not.toBeNull();
    expect(handleValidation({ verdict: 'PENDING' })).not.toBeNull();
    expect(handleValidation({ verdict: 'CONFIRMED' })).not.toBeNull();
    expect(handleValidation({ verdict: 'CONFIRMED', actionCode: 'ADD_KNOWLEDGE' })).toBeNull();
    expect(handleValidation({ verdict: 'REJECTED' })).toBeNull();
  });

  it('比率格式化：百分比保留 1 位；无值显示 —', () => {
    expect(rateText(0.5)).toBe('50.0%');
    expect(rateText(0.3333)).toBe('33.3%');
    expect(rateText(undefined)).toBe('—');
  });

  it('故障分类行：保持后端顺序并转成数组', () => {
    expect(faultClassRows({ UNKNOWN: 3, TIMEOUT: 2 })).toEqual([
      { code: 'UNKNOWN', count: 3 },
      { code: 'TIMEOUT', count: 2 }
    ]);
    expect(faultClassRows(undefined)).toEqual([]);
  });

  it('★ 队列过滤词表：动作/原因首项是"全部"（空=不限，不是"等于空"）', () => {
    expect(QUEUE_ACTION_FILTERS[0].code).toBe('');
    expect(QUEUE_ACTION_FILTERS.map(item => item.code).slice(1)).toEqual(
      ACTION_OPTIONS.map(item => item.code)
    );
    expect(QUEUE_REASON_FILTERS[0].code).toBe('');
    expect(QUEUE_REASON_FILTERS.map(item => item.code).slice(1)).toEqual(
      REASON_OPTIONS.map(item => item.code)
    );
  });

  it('★ 按动作过滤要给出去向：动作只送进改进流程，不直接改线上版本', () => {
    expect(queueActionHint('ADD_CASE')).toContain('golden_cases');
    expect(queueActionHint('ADD_CASE')).toContain('不直接进通用黄金数据集');
    expect(queueActionHint('ADD_KNOWLEDGE')).toContain('知识中心');
    expect(queueActionHint('CHANGE_RULE')).toContain('训练台');
    expect(queueActionHint('CLOSE')).toContain('无需动作');
    expect(queueActionHint('')).toContain('按动作过滤');
  });
});
