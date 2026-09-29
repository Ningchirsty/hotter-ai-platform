import { describe, expect, it } from 'vitest';
import { STEP_META } from './creativeFlowSteps';
import {
  STEP_CODE_TO_CODE_KEY,
  formatMappingChip,
  mapFlowSteps
} from './flowStepMapping';
import type { ScenarioStep } from '@/api/creative/scenario';

/**
 * 步序对照的单元测试（V0.2 D 阶段第二刀，第一步）。
 *
 * <p>用**生产种子里的 10 步**（`dp_creative_r11_scenario_foundation.sql`）当基准数据：
 * 对照表的对错不会抛异常，只会让人在界面上看到错的映射，所以必须钉住。</p>
 */
const SEED: ScenarioStep[] = [
  { stepCode: 'INPUT', stepName: '产品资料与参考图', stageCodes: 'MATERIAL_READY', sortNo: 10 },
  { stepCode: 'FACT', stepName: '事实确认', stageCodes: 'MATERIAL_READY', sortNo: 20 },
  { stepCode: 'DNA', stepName: '视觉基因', stageCodes: 'DNA_GENERATING,DNA_REVIEW,DNA_LOCKED', sortNo: 30 },
  {
    stepCode: 'DIRECTION',
    stepName: '视觉方向',
    stageCodes: 'DIRECTION_GENERATING,DIRECTION_REVIEW,DIRECTION_LOCKED',
    sortNo: 40
  },
  {
    stepCode: 'STORYBOARD',
    stepName: '分镜',
    stageCodes: 'STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED',
    sortNo: 50
  },
  { stepCode: 'GATE', stepName: '视觉门', stageCodes: 'VISUAL_GATE,VISUAL_LOCKED', sortNo: 60 },
  { stepCode: 'GENERATION', stepName: '出图', stageCodes: 'PRODUCING', sortNo: 70 },
  { stepCode: 'QA', stepName: '质检', stageCodes: 'QA_PROCESSING', sortNo: 80 },
  { stepCode: 'LAYOUT', stepName: '长图排版', stageCodes: 'LAYOUT_PROCESSING,DESIGN_REFINING', sortNo: 90 },
  { stepCode: 'FINAL', stepName: '终审交付', stageCodes: 'FINAL_REVIEW,COMPLETED', sortNo: 100 }
];

describe('flowStepMapping', () => {
  it('生产种子 10 步逐条对映到代码八步（9 条有对应，QA 无对应）', () => {
    const m = mapFlowSteps(SEED);
    expect(m.rows).toHaveLength(10);
    expect(m.rows.map((r) => [r.stepCode, r.codeNo])).toEqual([
      ['INPUT', 1],
      ['FACT', 2],
      ['DNA', 3],
      ['DIRECTION', 4],
      ['STORYBOARD', 5],
      ['GATE', 6],
      ['GENERATION', 7],
      ['QA', null],
      ['LAYOUT', 8],
      ['FINAL', 8]
    ]);
    expect(m.unmapped.map((r) => r.stepCode)).toEqual(['QA']);
    expect(m.coverageText).toBe('9/10');
    expect(formatMappingChip(m)).toBe('步序对照 9/10');
  });

  it('对映目标是代码八步的真名（不是另抄一份名字）', () => {
    const m = mapFlowSteps(SEED);
    const byCode = Object.fromEntries(m.rows.map((r) => [r.stepCode, r.codeName]));
    for (const meta of STEP_META) {
      expect(Object.values(byCode)).toContain(meta.name);
    }
    expect(byCode.INPUT).toBe('项目资料与品牌要求');
    expect(byCode.GENERATION).toBe('出图');
  });

  it('两条配置覆盖同一步（LAYOUT/FINAL → 第 8 步）会被算出来', () => {
    const m = mapFlowSteps(SEED);
    expect(m.fanIn).toEqual([
      { codeNo: 8, codeName: '详情页排版与终审', stepCodes: ['LAYOUT', 'FINAL'] }
    ]);
    expect(m.uncoveredCodeNos).toEqual([]);
  });

  it('阶段覆盖：只有 V08_READY 没有被任何配置步骤覆盖（R14 的区间规则正是为它而存在）', () => {
    const m = mapFlowSteps(SEED);
    expect(m.uncoveredStages).toEqual(['V08_READY']);
    expect(m.unknownStages).toEqual([]);
    expect(m.verdict).toContain('QA');
    expect(m.verdict).toContain('V08_READY');
  });

  it('配置里出现代码不认识的阶段码要暴露出来（不是静默忽略）', () => {
    const m = mapFlowSteps([
      { stepCode: 'DNA', stepName: '视觉基因', stageCodes: 'DNA_LOCKED,SOME_NEW_STAGE', sortNo: 10 }
    ]);
    expect(m.rows[0].unknownStages).toEqual(['SOME_NEW_STAGE']);
    expect(m.unknownStages).toEqual(['SOME_NEW_STAGE']);
    expect(m.verdict).toContain('SOME_NEW_STAGE');
  });

  it('代码有、配置没有的步骤要报出来（而不是当成"已完成"）', () => {
    const m = mapFlowSteps([{ stepCode: 'DNA', stepName: '视觉基因', stageCodes: 'DNA_LOCKED', sortNo: 10 }]);
    expect(m.uncoveredCodeNos).toEqual([1, 2, 4, 5, 6, 7, 8]);
    expect(m.coverageText).toBe('1/1');
    expect(m.verdict).toContain('没有配置步骤对应');
  });

  it('空输入不编造：全空、coverageText 与 chip 都是空串', () => {
    for (const input of [undefined, null, []]) {
      const m = mapFlowSteps(input as ScenarioStep[]);
      expect(m.rows).toEqual([]);
      expect(m.coverageText).toBe('');
      expect(m.verdict).toBe('');
      expect(formatMappingChip(m)).toBe('');
    }
  });

  it('缺 stepCode 的脏数据不算对映，也不让结论里出现空括号', () => {
    const m = mapFlowSteps([{}]);
    expect(m.rows).toHaveLength(1);
    expect(m.rows[0].codeNo).toBeNull();
    expect(m.coverageText).toBe('0/1');
    expect(m.verdict).toContain('未命名');
    expect(m.verdict).not.toContain('（）');
  });

  it('顺序按 sortNo（后端已排序，前端再兜一层）', () => {
    const shuffled = [SEED[9], SEED[0], SEED[5]];
    expect(mapFlowSteps(shuffled).rows.map((r) => r.stepCode)).toEqual(['INPUT', 'GATE', 'FINAL']);
  });

  it('对映表本身只引用代码八步里存在的 key（改表时这里会红）', () => {
    const keys = new Set(STEP_META.map((m) => m.key));
    for (const [stepCode, key] of Object.entries(STEP_CODE_TO_CODE_KEY)) {
      expect(keys.has(key), `${stepCode} → ${key} 不是代码八步的 key`).toBe(true);
    }
    // 种子里的 10 步要么在对映表里、要么被明确认定为"无对应"（避免悄悄漏配）
    for (const step of SEED) {
      const known = STEP_CODE_TO_CODE_KEY[step.stepCode!] != null || step.stepCode === 'QA';
      expect(known, `${step.stepCode} 既没有对映、也不在已知的无对应清单里`).toBe(true);
    }
  });
});
