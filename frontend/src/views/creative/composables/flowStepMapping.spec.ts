import { describe, expect, it } from 'vitest';
import { STEP_META } from './creativeFlowSteps';
import {
  CONFIG_STATUS_LABELS,
  REQUIRE_TO_STEP,
  STEP_CODE_TO_CODE_KEY,
  STEP_CODE_TO_PAGE,
  buildFallbackSteps,
  buildGuideSteps,
  formatMappingChip,
  mapFlowSteps,
  stepProgress
} from './flowStepMapping';
import type { ProjectStepState, ScenarioStep } from '@/api/creative/scenario';

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

/**
 * R16：把指引线切成配置驱动。
 *
 * <p>这里钉的是"驱动"这件事本身：① 十步各占一格；② 状态以后端 `dp_project_step_state` 为准；
 * ③ 前置条件来自配置 `entry_condition_json`。三条错了都不会报错，只会把人指到错的环节。</p>
 */
describe('配置驱动：指引线计划', () => {
  /** 生产种子里的 10 步（含 entry_condition_json，与库中一致） */
  const CONFIG: ScenarioStep[] = [
    { stepCode: 'INPUT', stepName: '产品资料与参考图', sortNo: 10, required: '1', entryConditionJson: '{"requireProject":true}' },
    { stepCode: 'FACT', stepName: '事实确认', sortNo: 20, required: '1', entryConditionJson: '{"requireInput":true}' },
    { stepCode: 'DNA', stepName: '视觉基因', sortNo: 30, required: '1', entryConditionJson: '{"requireFact":true}' },
    { stepCode: 'DIRECTION', stepName: '视觉方向', sortNo: 40, required: '1', entryConditionJson: '{"requireDna":true}' },
    { stepCode: 'STORYBOARD', stepName: '分镜', sortNo: 50, required: '1', entryConditionJson: '{"requireDirection":true}' },
    { stepCode: 'GATE', stepName: '视觉门', sortNo: 60, required: '1', entryConditionJson: '{"requireStoryboard":true}' },
    { stepCode: 'GENERATION', stepName: '出图', sortNo: 70, required: '1', entryConditionJson: '{"requireGate":true}' },
    { stepCode: 'QA', stepName: '质检', sortNo: 80, required: '0', entryConditionJson: '{"requireGeneration":true}' },
    { stepCode: 'LAYOUT', stepName: '长图排版', sortNo: 90, required: '1', entryConditionJson: '{"requireGate":true}' },
    { stepCode: 'FINAL', stepName: '终审交付', sortNo: 100, required: '1', entryConditionJson: '{"requireLayout":true}' }
  ];

  /**
   * 造项目步骤状态。
   *
   * @param pairs [stepCode, status] 列表
   * @returns 状态数组
   */
  const states = (...pairs: Array<[string, string]>): ProjectStepState[] =>
    pairs.map(([stepCode, status]) => ({ stepCode, status }));

  it('十步各占一格：顺序、名称、页面都按配置与已确认的页面口径', () => {
    const plan = buildGuideSteps(CONFIG, states());
    expect(plan.map((p) => p.no)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    expect(plan.map((p) => p.key)).toEqual([
      'INPUT', 'FACT', 'DNA', 'DIRECTION', 'STORYBOARD', 'GATE', 'GENERATION', 'QA', 'LAYOUT', 'FINAL'
    ]);
    expect(plan.map((p) => p.name)).toEqual([
      '产品资料与参考图', '事实确认', '视觉基因', '视觉方向', '分镜', '视觉门', '出图', '质检', '长图排版', '终审交付'
    ]);
    // 已确认的口径：QA 不新增页面（复用出图页）；排版与终审都在评审页
    expect(plan[7].page).toBe('/creative/production');
    expect(plan[6].page).toBe('/creative/production');
    expect(plan[8].page).toBe('/creative/review');
    expect(plan[9].page).toBe('/creative/review');
    expect(plan[0].page).toBe('/creative/project');
    expect(plan[2].page).toBe('/creative/dna');
    expect(plan[4].page).toBe('/creative/storyboard');
    expect(plan[5].page).toBe('/creative/review');
    expect(plan.every((p) => p.source === 'CONFIG')).toBe(true);
  });

  it('状态以后端为准：DONE→已完成、ACTIVE→进行中、PENDING→待办或前置未完成', () => {
    const plan = buildGuideSteps(
      CONFIG,
      states(['INPUT', 'DONE'], ['FACT', 'DONE'], ['DNA', 'ACTIVE'])
    );
    expect(plan.slice(0, 3).map((p) => p.status)).toEqual(['done', 'done', 'doing']);
    // 方向声明 requireDna，而 DNA 还没 DONE → 前置未完成；分镜同理
    expect(plan[3].status).toBe('blocked');
    expect(plan[3].waiting).toEqual(['视觉基因']);
    // 同一阶段里两步同时进行中（资料与事实都覆盖 MATERIAL_READY）是配置的真实粒度
    const both = buildGuideSteps(CONFIG, states(['INPUT', 'ACTIVE'], ['FACT', 'ACTIVE']));
    expect(both[0].status).toBe('doing');
    expect(both[1].status).toBe('doing');
  });

  it('进行中的步骤不会被判成"前置未完成"（它已经开始了）', () => {
    // 配置说 DNA 需要 FACT 完成；但如果后端已把 DNA 标成 ACTIVE，就不该显示"前置未完成"
    const plan = buildGuideSteps(CONFIG, states(['INPUT', 'DONE'], ['DNA', 'ACTIVE']));
    const dna = plan[2];
    expect(dna.status).toBe('doing');
    expect(dna.waiting).toEqual(['事实确认']); // 事实仍如实列出来，只是不用它判定状态
  });

  it('全部 DONE 时十步全绿（已交付项目不该留"还差一步"）', () => {
    const plan = buildGuideSteps(
      CONFIG,
      states(...CONFIG.map((s) => [s.stepCode!, 'DONE'] as [string, string]))
    );
    expect(plan.every((p) => p.status === 'done')).toBe(true);
  });

  it('前置条件来自配置：改配置就改阻塞（不写死在前端）', () => {
    // 把 DNA 的进入条件从 requireFact 改成 requireProject → 不再等事实
    const relaxed = CONFIG.map((s) =>
      s.stepCode === 'DNA' ? { ...s, entryConditionJson: '{"requireProject":true}' } : s
    );
    expect(buildGuideSteps(CONFIG, states())[2].status).toBe('blocked');
    expect(buildGuideSteps(relaxed, states())[2].status).toBe('todo');
  });

  it('不认识的进入条件如实报出来，且不拿它当阻塞理由（不猜）', () => {
    const weird = CONFIG.map((s) =>
      s.stepCode === 'DNA'
        ? { ...s, entryConditionJson: '{"requireBrandGuide":true,"requireFact":true}' }
        : s
    );
    const dna = buildGuideSteps(weird, states())[2];
    expect(dna.unknownConditions).toEqual(['requireBrandGuide']);
    expect(dna.waiting).toEqual(['事实确认']); // 只等能翻译成步骤的那一条
  });

  it('自依赖（配置里 require 自己）不算阻塞，否则那一步永远红着', () => {
    const selfish = CONFIG.map((s) =>
      s.stepCode === 'DNA' ? { ...s, entryConditionJson: '{"requireDna":true}' } : s
    );
    expect(buildGuideSteps(selfish, states())[2].status).toBe('todo');
  });

  it('坏 JSON / 空条件不编造：解析不出来就当没有前置', () => {
    const broken = CONFIG.map((s) =>
      s.stepCode === 'DNA' ? { ...s, entryConditionJson: '{requireFact' } : s
    );
    expect(buildGuideSteps(broken, states())[2].status).toBe('todo');
    const none = CONFIG.map((s) => ({ ...s, entryConditionJson: undefined }));
    expect(buildGuideSteps(none, states()).every((p) => p.status === 'todo')).toBe(true);
  });

  it('required=0 的步骤如实标出（QA 在配置里是可选步骤）', () => {
    const plan = buildGuideSteps(CONFIG, states());
    expect(plan[7].required).toBe(false);
    expect(plan.filter((p) => !p.required).map((p) => p.key)).toEqual(['QA']);
  });

  it('配置为空 → 空计划（由调用方回落到代码八步，不是"零步完成"）', () => {
    expect(buildGuideSteps([], states())).toEqual([]);
    expect(buildGuideSteps(undefined, states())).toEqual([]);
  });

  it('回落计划就是代码八步（配置读不到时指引线不能消失）', () => {
    const fallback = buildFallbackSteps();
    expect(fallback).toHaveLength(8);
    expect(fallback.map((p) => p.key)).toEqual(STEP_META.map((m) => m.key));
    expect(fallback.map((p) => p.page)).toEqual(STEP_META.map((m) => m.page));
    expect(fallback.every((p) => p.source === 'CODE' && p.status === 'todo')).toBe(true);
  });

  it('页面与前置映射表只引用种子里的步骤编码（改表时这里会红）', () => {
    const codes = new Set(CONFIG.map((s) => s.stepCode!));
    for (const [stepCode, page] of Object.entries(STEP_CODE_TO_PAGE)) {
      expect(codes.has(stepCode), `${stepCode} 不在配置步骤里`).toBe(true);
      expect(page.startsWith('/creative/'), `${stepCode} 的页面 ${page} 不像站内路径`).toBe(true);
    }
    for (const [condition, stepCode] of Object.entries(REQUIRE_TO_STEP)) {
      expect(condition.startsWith('require'), `${condition} 不是 requireXxx 形式`).toBe(true);
      expect(codes.has(stepCode), `${condition} → ${stepCode} 不是配置步骤`).toBe(true);
    }
    // 种子里的每个步骤都要有页面归属，否则点击会跳到项目页（默许的兜底不该被用到）
    for (const step of CONFIG) {
      expect(STEP_CODE_TO_PAGE[step.stepCode!], `${step.stepCode} 缺页面归属`).toBeTruthy();
    }
  });

  it('配置驱动下的状态文案：blocked 说"前置未完成"（不是"被阻塞"）', () => {
    expect(CONFIG_STATUS_LABELS.blocked).toBe('前置未完成');
    expect(CONFIG_STATUS_LABELS.done).toBe('已完成');
    expect(CONFIG_STATUS_LABELS.doing).toBe('进行中');
    expect(CONFIG_STATUS_LABELS.todo).toBe('未开始');
    expect(CONFIG_STATUS_LABELS.skipped).toBe('已跳过');
  });
});

/**
 * R36：跳过步骤的语义（前端侧）。
 *
 * <p>这里钉的是"跳过"在**界面上**的含义：① 已跳过就是已跳过，不显示成进行中/前置未完成；
 * ② 跳过的步骤不再阻塞它后面的步骤（否则跳过白跳）；③ 进度口径与后端
 * {@code CreativeStepProjection#progress} 一致——跳过从分母去掉、不算分子、分子不越过分母。</p>
 */
describe('R36：跳过步骤', () => {
  const CONFIG: ScenarioStep[] = [
    { stepCode: 'INPUT', stepName: '产品资料与参考图', sortNo: 10, required: '1', entryConditionJson: '{"requireProject":true}' },
    { stepCode: 'FACT', stepName: '事实确认', sortNo: 20, required: '1', entryConditionJson: '{"requireInput":true}' },
    { stepCode: 'QA', stepName: '质检', sortNo: 80, required: '0', entryConditionJson: '{"requireFact":true}' },
    // 故意让终审也依赖 INPUT：用来验证"被跳过的前置算已了结"
    { stepCode: 'FINAL', stepName: '终审交付', sortNo: 100, required: '1', entryConditionJson: '{"requireInput":true}' }
  ];

  it('后端 SKIPPED → 已跳过（不是"进行中"，也不是"前置未完成"）', () => {
    const plan = buildGuideSteps(CONFIG, [
      { stepCode: 'INPUT', status: 'SKIPPED', skippable: false, skipReason: '客户自带资料，不走这一步' }
    ]);
    expect(plan[0].status).toBe('skipped');
    expect(plan[0].backendStatus).toBe('SKIPPED');
    expect(plan[0].skipReason).toBe('客户自带资料，不走这一步');
  });

  it('跳过的步骤不再阻塞后面的步骤（否则跳过就白跳了）', () => {
    const plan = buildGuideSteps(CONFIG, [{ stepCode: 'INPUT', status: 'SKIPPED' }]);
    expect(plan[1].status).toBe('todo');
    expect(plan[1].waiting).toEqual([]);
    expect(plan[3].status).toBe('todo');
  });

  it('没有落库行的步骤不会凭空带上跳过原因', () => {
    const plan = buildGuideSteps(CONFIG, [
      { stepCode: 'FACT', status: 'DONE' },
      { stepCode: 'QA', status: 'PENDING', skippable: true, skipReason: '上一次跳过的旧原因' }
    ]);
    expect(plan[2].status).toBe('todo');
    expect(plan[2].skippable).toBe(true);
    expect(plan[2].skipReason).toBeNull();
  });

  it('能不能跳过取自后端（前端不重算"可选 + 无闸门"）', () => {
    const plan = buildGuideSteps(CONFIG, [
      { stepCode: 'QA', status: 'PENDING', skippable: true },
      { stepCode: 'FACT', status: 'PENDING', skippable: false }
    ]);
    expect(plan[2].skippable).toBe(true);
    expect(plan[1].skippable).toBe(false);
    // 回落模式（配置读不到）一律不给跳过：按配置判定可选/闸门的前提是配置在
    expect(buildFallbackSteps().every((p) => !p.skippable && p.skipReason === null)).toBe(true);
  });

  it('进度：做过 2 步、跳过 1 步 → 2 / 3 已完成 · 已跳过 1 步', () => {
    const plan = buildGuideSteps(CONFIG, [
      { stepCode: 'INPUT', status: 'DONE' },
      { stepCode: 'FACT', status: 'DONE' },
      { stepCode: 'QA', status: 'SKIPPED' }
    ]);
    const progress = stepProgress(plan);
    expect(progress).toMatchObject({ done: 2, total: 4, skipped: 1, denominator: 3, pct: 67 });
    expect(progress.text).toBe('2 / 3 已完成 · 已跳过 1 步');
  });

  it('没有跳过时进度就是"已完成 / 总步数"（与 R16 之前一致）', () => {
    const plan = buildGuideSteps(CONFIG, [{ stepCode: 'INPUT', status: 'DONE' }]);
    expect(stepProgress(plan).text).toBe('1 / 4 已完成');
    // 回落八步同理
    expect(stepProgress(buildFallbackSteps()).text).toBe('0 / 8 已完成');
    // 空计划不出现 NaN
    expect(stepProgress([])).toMatchObject({ denominator: 0, pct: 0, text: '0 / 0 已完成' });
  });

  it('跳过把分母变小，比例不会超过 100%', () => {
    // 4 步里做完 1 步、跳过 1 步 → 分母只剩 3
    const progress = stepProgress(
      buildGuideSteps(CONFIG, [
        { stepCode: 'INPUT', status: 'DONE' },
        { stepCode: 'FACT', status: 'SKIPPED' }
      ])
    );
    expect(progress).toMatchObject({ done: 1, skipped: 1, denominator: 3, pct: 33 });

    // 3 步里做完 2 步、跳过 1 步 → 分子与分母正好都是 2（恰好 100%）。
    // 注意：前端其实算不出"分子大于分母"（done + skipped ≤ total），
    // 真正的越界保护在后端 `CreativeStepProjection#progress` 里，这里只钉住边界。
    const rows = buildFallbackSteps();
    const boundary = stepProgress([
      { ...rows[0], status: 'done' },
      { ...rows[1], status: 'done' },
      { ...rows[2], status: 'skipped' }
    ]);
    expect(boundary).toMatchObject({ done: 2, total: 3, skipped: 1, denominator: 2, pct: 100 });
    expect(boundary.text).toBe('2 / 2 已完成 · 已跳过 1 步');
  });
});
