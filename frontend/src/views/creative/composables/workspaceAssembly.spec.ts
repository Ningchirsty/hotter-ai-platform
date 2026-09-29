import { describe, expect, it } from 'vitest';
import {
  ASSEMBLY_KIND_LABELS,
  CODE_COMPONENT_REGISTRY,
  diffWorkspaceAssembly,
  formatAssemblyChip,
  layoutOfProfile,
  parseWorkspaceLayout
} from './workspaceAssembly';

/**
 * 工作台装配只读对照的单元测试（V0.2 D 阶段最后一块第一步，R17）。
 *
 * <p>基准数据是**生产种子**（`dp_creative_r11_scenario_foundation.sql` 的 `WS_LONG_PAGE`，
 * 文档 §49 口径）。这里钉的是"配置与实现的差异被如实算出来"：
 * 分类错了不会抛异常，只会让"还要做什么"看起来比实际少（或我凭空说某件没做的事已经做了）。</p>
 */
const SEED_JSON =
  '{"workspace":"LONG_PAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],' +
  '"steps":[{"code":"INPUT","component":"ProjectInputPanel"},{"code":"FACT","component":"FactPanel"},' +
  '{"code":"DNA","component":"VisualDnaPanel"},{"code":"DIRECTION","component":"DirectionBoard"},' +
  '{"code":"STORYBOARD","component":"StoryboardBoard"},{"code":"GATE","component":"GatePanel"},' +
  '{"code":"GENERATION","component":"GenerationBoard"},{"code":"QA","component":"QaPanel"},' +
  '{"code":"LAYOUT","component":"LongPageCanvas"},{"code":"FINAL","component":"FinalReviewPanel"}]}';

describe('workspaceAssembly：解析', () => {
  it('解析生产种子：5 个面板 + 10 个步骤组件，工作台 LONG_PAGE', () => {
    const layout = parseWorkspaceLayout(SEED_JSON);
    expect(layout).not.toBeNull();
    expect(layout!.workspace).toBe('LONG_PAGE');
    expect(layout!.panels).toEqual([
      'PROJECT_HEADER', 'STEP_NAVIGATOR', 'MAIN_STAGE', 'INSPECTOR', 'ASSET_DRAWER'
    ]);
    expect(layout!.steps.map((s) => s.code)).toEqual([
      'INPUT', 'FACT', 'DNA', 'DIRECTION', 'STORYBOARD', 'GATE', 'GENERATION', 'QA', 'LAYOUT', 'FINAL'
    ]);
    expect(layout!.steps[2]).toEqual({ code: 'DNA', component: 'VisualDnaPanel' });
  });

  it('从场景档案取（layout_json 空 / 坏 JSON / 空对象都不编造）', () => {
    expect(layoutOfProfile({ workspaceSchemaJson: SEED_JSON })!.workspace).toBe('LONG_PAGE');
    expect(layoutOfProfile({ workspaceSchemaJson: '' })).toBeNull();
    expect(layoutOfProfile({ workspaceSchemaJson: '{bad json' })).toBeNull();
    expect(layoutOfProfile({ workspaceSchemaJson: '{}' })).toBeNull();
    expect(layoutOfProfile({})).toBeNull();
    expect(layoutOfProfile(null)).toBeNull();
    expect(layoutOfProfile(undefined)).toBeNull();
  });

  it('容错：缺 steps、steps 里有脏项、panel 不是字符串', () => {
    const layout = parseWorkspaceLayout(
      '{"workspace":"W","panels":["A",5,null],"steps":[{"code":"X"},{"component":"Y"},null,7]}'
    );
    expect(layout).not.toBeNull();
    expect(layout!.panels).toEqual(['A']);
    // 脏项不丢：code/component 缺哪个就补空串，交给对照去报"没给组件"
    expect(layout!.steps).toEqual([
      { code: 'X', component: '' },
      { code: '', component: 'Y' }
    ]);
  });
});

describe('workspaceAssembly：对照', () => {
  const diff = diffWorkspaceAssembly(parseWorkspaceLayout(SEED_JSON))!;

  it('15 项全对照：5 面板 + 10 步骤', () => {
    expect(diff.panelRows).toHaveLength(5);
    expect(diff.stepRows).toHaveLength(10);
    expect(diff.panelRows.map((r) => r.code)).toEqual([
      'PROJECT_HEADER', 'STEP_NAVIGATOR', 'MAIN_STAGE', 'INSPECTOR', 'ASSET_DRAWER'
    ]);
  });

  it('分类如实：只有 STEP_NAVIGATOR 是独立组件；INSPECTOR/ASSET_DRAWER/QaPanel 未实现', () => {
    const byName = Object.fromEntries(
      [...diff.panelRows, ...diff.stepRows].map((r) => [r.component || r.code, r.kind])
    );
    expect(byName.STEP_NAVIGATOR).toBe('COMPONENT');
    expect(byName.INSPECTOR).toBe('MISSING');
    expect(byName.ASSET_DRAWER).toBe('MISSING');
    expect(byName.QaPanel).toBe('MISSING');
    // 其余都是"写在页面里的一段"
    expect(byName.VisualDnaPanel).toBe('SECTION');
    expect(byName.LongPageCanvas).toBe('SECTION');
    expect(byName.FinalReviewPanel).toBe('SECTION');
    expect(diff.componentCount).toBe(1);
    expect(diff.sectionCount).toBe(11);
    expect(diff.missingCount).toBe(3);
    expect(diff.readyText).toBe('1/15');
    expect(formatAssemblyChip(diff)).toBe('工作台装配 1/15');
  });

  it('每一行都带"代码里在哪"与一句补充（没有落点的对照等于没对照）', () => {
    for (const row of [...diff.panelRows, ...diff.stepRows]) {
      expect(row.location, `${row.code} 缺落点`).toBeTruthy();
      expect(row.note, `${row.code} 缺补充说明`).toBeTruthy();
    }
    const qa = diff.stepRows.find((r) => r.code === 'QA')!;
    expect(qa.location).toBe('没有独立实现');
    expect(qa.note).toContain('没有独立面板');
  });

  it('结论句只用算出的事实拼（含工作台名、三类计数、共 15 项）', () => {
    expect(diff.verdict).toContain('LONG_PAGE');
    expect(diff.verdict).toContain('配置声明 5 个面板 + 10 个步骤组件');
    expect(diff.verdict).toContain('已是独立组件 1 个');
    expect(diff.verdict).toContain('共对照 15 项');
  });

  it('注册表与配置互相校验：注册表里多出来的名字要报"没用上"', () => {
    // 生产配置没有用到 QaPanel 之外的额外名字，所以这里应当为空
    expect(diff.unknownNames).toEqual([]);
    expect(diff.unusedInConfig).toEqual([]);
  });

  it('配置里出现注册表没登记的名字 → 算 MISSING 并单独列出（不静默略过）', () => {
    const layout = parseWorkspaceLayout(
      '{"workspace":"W","panels":["NEW_PANEL"],"steps":[{"code":"X","component":"FuturePanel"}]}'
    );
    const d = diffWorkspaceAssembly(layout)!;
    expect(d.unknownNames).toEqual(['NEW_PANEL', 'FuturePanel']);
    expect(d.missingCount).toBe(2);
    expect(d.verdict).toContain('注册表里没有登记');
    expect(d.panelRows[0].location).toBe('注册表里没有登记');
  });

  it('步骤声明了但没给组件要报出来（不是当作"已实现"）', () => {
    const layout = parseWorkspaceLayout('{"workspace":"W","panels":[],"steps":[{"code":"X"}]}');
    const d = diffWorkspaceAssembly(layout)!;
    expect(d.stepsWithoutComponent).toEqual(['X']);
    expect(d.verdict).toContain('配置里没给组件的步骤：X');
    expect(d.stepRows[0].kind).toBe('MISSING');
  });

  it('注册表里登记、配置里没用到 → 如实列为"没用上"（配置与代码谁先动都能看出来）', () => {
    const layout = parseWorkspaceLayout('{"workspace":"W","panels":["STEP_NAVIGATOR"],"steps":[]}');
    const d = diffWorkspaceAssembly(layout)!;
    expect(d.unusedInConfig).toContain('ProjectInputPanel');
    expect(d.unusedInConfig).toContain('QaPanel');
    expect(d.unusedInConfig).not.toContain('STEP_NAVIGATOR');
  });

  it('layout 为空 → 对照为 null（调用方据此整块不渲染）', () => {
    expect(diffWorkspaceAssembly(null)).toBeNull();
    expect(formatAssemblyChip(null)).toBe('');
  });

  it('注册表自身的口径：分类合法、落点与说明都不空、种子里的名字都有登记', () => {
    const kinds = ['COMPONENT', 'SECTION', 'MISSING'];
    for (const [name, entry] of Object.entries(CODE_COMPONENT_REGISTRY)) {
      expect(kinds, `${name} 的分类 ${entry.kind} 不合法`).toContain(entry.kind);
      expect(entry.location, `${name} 缺落点`).toBeTruthy();
      expect(entry.note, `${name} 缺说明`).toBeTruthy();
    }
    const layout = parseWorkspaceLayout(SEED_JSON)!;
    for (const name of [...layout.panels, ...layout.steps.map((s) => s.component)]) {
      expect(CODE_COMPONENT_REGISTRY[name], `种子里的 ${name} 没在注册表登记`).toBeTruthy();
    }
    expect(Object.keys(ASSEMBLY_KIND_LABELS)).toEqual(kinds);
  });
});
