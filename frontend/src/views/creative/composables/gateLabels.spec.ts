import { describe, expect, it } from 'vitest';
import { dnaEvidenceKindLabel, gateLevelLabel } from './gateLabels';
import { taskStatusLabel } from '@/api/content/task/status';

/**
 * 「界面上不印枚举码」的回归钉子（v1 人工测试反馈：去英文/去编号）。
 *
 * <p>这些标签以前散在各处：闸门等级在评审页写死「硬性/建议」，事实区块却直接打印
 * `BLOCK`；内容协同状态在内容任务页有中文映射，视觉项目页却直接打印 `CONDITIONAL_READY`；
 * DNA 证据链的「类型」列打印 `FACT/DEFAULT/MANUAL`。这里把口径钉住。</p>
 *
 * @author creative
 */
describe('gateLevelLabel', () => {
  it('闸门等级：BLOCK→硬性、CONDITION→建议、NOTICE→非阻断提醒', () => {
    expect(gateLevelLabel('BLOCK')).toBe('硬性');
    expect(gateLevelLabel('CONDITION')).toBe('建议');
    // 事实字段还会出现第三档（真机上「品牌调性说明」就是 NOTICE）——三档都得翻
    expect(gateLevelLabel('NOTICE')).toBe('非阻断提醒');
  });

  it('认不出的等级原样返回，不编一个等级出来', () => {
    expect(gateLevelLabel('SOMETHING')).toBe('SOMETHING');
    expect(gateLevelLabel('')).toBe('');
    expect(gateLevelLabel(null)).toBe('');
  });
});

describe('dnaEvidenceKindLabel', () => {
  it('证据类型：FACT→事实、DEFAULT→默认值、MANUAL→人工编辑、MODEL→模型、REFERENCE→参考图', () => {
    expect(dnaEvidenceKindLabel('FACT')).toBe('事实');
    expect(dnaEvidenceKindLabel('DEFAULT')).toBe('默认值');
    expect(dnaEvidenceKindLabel('MANUAL')).toBe('人工编辑');
    expect(dnaEvidenceKindLabel('MODEL')).toBe('模型');
    expect(dnaEvidenceKindLabel('REFERENCE')).toBe('参考图');
  });

  it('认不出的类型原样返回', () => {
    expect(dnaEvidenceKindLabel('FUTURE_KIND')).toBe('FUTURE_KIND');
    expect(dnaEvidenceKindLabel(null)).toBe('');
  });
});

describe('taskStatusLabel', () => {
  it('内容协同状态：与内容任务页同一套中文（视觉项目页不再打印 CONDITIONAL_READY）', () => {
    expect(taskStatusLabel('DRAFT')).toBe('草稿');
    expect(taskStatusLabel('PARSING')).toBe('解析中');
    expect(taskStatusLabel('PENDING_CONFIRM')).toBe('待确认/待补料');
    expect(taskStatusLabel('CONDITIONAL_READY')).toBe('条件开工');
    expect(taskStatusLabel('READY')).toBe('可开工');
  });

  it('认不出的状态原样返回（不吞掉、也不编）', () => {
    expect(taskStatusLabel('SOME_NEW_STATUS')).toBe('SOME_NEW_STATUS');
    expect(taskStatusLabel('')).toBe('-');
    expect(taskStatusLabel(undefined)).toBe('-');
  });
});
