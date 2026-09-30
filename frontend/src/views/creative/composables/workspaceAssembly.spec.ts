import { describe, expect, it } from 'vitest';
import {
  ASSEMBLY_KIND_LABELS,
  CODE_COMPONENT_REGISTRY,
  assembledSlots,
  buildAssemblyPlan,
  describeAssembly,
  diffWorkspaceAssembly,
  formatAssemblyChip,
  layoutOfWorkspace,
  parseWorkspaceLayout,
  referencedSchemaCode
} from './workspaceAssembly';
import {
  IMPLEMENTED_COMPONENT_NAMES,
  resolveWorkspaceComponent
} from '../components/workspace/registry';

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

  it('从工作台装配取（layout_json 空 / 坏 JSON / 空对象都不编造）', () => {
    expect(layoutOfWorkspace({ layoutJson: SEED_JSON })!.workspace).toBe('LONG_PAGE');
    expect(layoutOfWorkspace({ layoutJson: '' })).toBeNull();
    expect(layoutOfWorkspace({ layoutJson: '{bad json' })).toBeNull();
    expect(layoutOfWorkspace({ layoutJson: '{}' })).toBeNull();
    expect(layoutOfWorkspace({})).toBeNull();
    expect(layoutOfWorkspace(null)).toBeNull();
    expect(layoutOfWorkspace(undefined)).toBeNull();
  });

  it('装配定义取自工作台表，不是取场景档案那个"引用"字段（R17 第一版就取错了源）', () => {
    // 生产上档案里存的是 {"schemaCode":"WS_LONG_PAGE"}——只有引用，没有 panels/steps
    const profileRef = '{"schemaCode":"WS_LONG_PAGE"}';
    expect(parseWorkspaceLayout(profileRef)).toBeNull();
    expect(referencedSchemaCode({ workspaceSchemaJson: profileRef })).toBe('WS_LONG_PAGE');
    // 真正能解析出装配的是工作台表的 layout_json
    expect(layoutOfWorkspace({ layoutJson: SEED_JSON })!.panels).toHaveLength(5);
    expect(referencedSchemaCode({ workspaceSchemaJson: '{bad' })).toBe('');
    expect(referencedSchemaCode(null)).toBe('');
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

  it('分类如实：R31 起五个面板/步骤组件全部是独立组件，不再有"未实现"', () => {
    const byName = Object.fromEntries(
      [...diff.panelRows, ...diff.stepRows].map((r) => [r.component || r.code, r.kind])
    );
    expect(byName.STEP_NAVIGATOR).toBe('COMPONENT');
    expect(byName.INSPECTOR).toBe('COMPONENT');
    expect(byName.ASSET_DRAWER).toBe('COMPONENT');
    // 【R31 起期望值有变，不是回归】这两个本轮真做了（此前是 SECTION / MISSING）
    expect(byName.PROJECT_HEADER).toBe('COMPONENT');
    expect(byName.QaPanel).toBe('COMPONENT');
    // 其余仍是"写在页面里的一段"（拆组件这件事一轮做不完，如实登记）
    expect(byName.VisualDnaPanel).toBe('SECTION');
    expect(byName.LongPageCanvas).toBe('SECTION');
    expect(byName.FinalReviewPanel).toBe('SECTION');
    expect(diff.componentCount).toBe(5);
    expect(diff.sectionCount).toBe(10);
    expect(diff.missingCount).toBe(0);
    expect(diff.readyText).toBe('5/15');
    expect(formatAssemblyChip(diff)).toBe('工作台装配 5/15');
  });

  it('每一行都带"代码里在哪"与一句补充（没有落点的对照等于没对照）', () => {
    for (const row of [...diff.panelRows, ...diff.stepRows]) {
      expect(row.location, `${row.code} 缺落点`).toBeTruthy();
      expect(row.note, `${row.code} 缺补充说明`).toBeTruthy();
    }
    // R31 真做之后，QaPanel 的落点必须指向真实文件（而不是还写着"没有实现"）
    const qa = diff.stepRows.find((r) => r.code === 'QA')!;
    expect(qa.location).toContain('CreativeQaPanel.vue');
    expect(qa.location).toContain('qaVerdicts.ts');
    expect(qa.note).toContain('四条证据线');
    // R18 真做之后，两个面板的落点必须指向真实文件
    const inspector = diff.panelRows.find((r) => r.code === 'INSPECTOR')!;
    expect(inspector.location).toContain('CreativeInspectorPanel.vue');
    expect(diff.panelRows.find((r) => r.code === 'ASSET_DRAWER')!.location)
      .toContain('CreativeAssetDrawer.vue');
    // R31：头部落点
    expect(diff.panelRows.find((r) => r.code === 'PROJECT_HEADER')!.location)
      .toContain('CreativeProjectHeader.vue');
  });

  it('结论句只用算出的事实拼（含工作台名、三类计数、共 15 项）', () => {
    expect(diff.verdict).toContain('LONG_PAGE');
    expect(diff.verdict).toContain('配置声明 5 个面板 + 10 个步骤组件');
    expect(diff.verdict).toContain('已是独立组件 5 个');
    expect(diff.verdict).toContain('还没实现 0 个');
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

  it('档案引用的工作台编码与实际发布的不一致 → 明确提示（不将就、不静默）', () => {
    const layout = parseWorkspaceLayout(SEED_JSON)!;
    const ok = diffWorkspaceAssembly(layout, {
      referencedCode: 'WS_LONG_PAGE',
      actualCode: 'WS_LONG_PAGE'
    })!;
    expect(ok.schemaCodeWarning).toBe('');
    expect(ok.verdict).not.toContain('不一致');

    const bad = diffWorkspaceAssembly(layout, {
      referencedCode: 'WS_LONG_PAGE',
      actualCode: 'WS_SOMETHING_ELSE'
    })!;
    expect(bad.schemaCodeWarning).toContain('WS_LONG_PAGE');
    expect(bad.schemaCodeWarning).toContain('WS_SOMETHING_ELSE');
    expect(bad.verdict).toContain('不一致');
    // 取不到引用（老档案没写）时不算不一致——不编造冲突
    expect(diffWorkspaceAssembly(layout, { actualCode: 'WS_X' })!.schemaCodeWarning).toBe('');
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

  it('描述表与真实组件注册表必须一致（R18 起：标了 COMPONENT 就得真有实现）', () => {
    const described = Object.entries(CODE_COMPONENT_REGISTRY)
      .filter(([, entry]) => entry.kind === 'COMPONENT')
      .map(([name]) => name)
      .toSorted();
    expect(described).toEqual([...IMPLEMENTED_COMPONENT_NAMES].toSorted());
    // 取组件实现：有实现的拿得到，没实现的必须返回 null（不能悄悄给个空组件）
    for (const name of IMPLEMENTED_COMPONENT_NAMES) {
      expect(resolveWorkspaceComponent(name), `${name} 应该能解析到组件`).toBeTruthy();
    }
    // R31 起 QaPanel 与 PROJECT_HEADER 也是真组件了
    expect(resolveWorkspaceComponent('QaPanel')).toBeTruthy();
    expect(resolveWorkspaceComponent('PROJECT_HEADER')).toBeTruthy();
    // 仍有一批步骤组件只是"页面内区块"：它们不该被解析出来（否则页面会渲染空白）
    expect(resolveWorkspaceComponent('VisualDnaPanel')).toBeNull();
    expect(resolveWorkspaceComponent('FuturePanel')).toBeNull();
    expect(resolveWorkspaceComponent('')).toBeNull();
    expect(resolveWorkspaceComponent(null)).toBeNull();
  });
});

/**
 * R19：装配运行时——把面板清单翻成槽位。
 *
 * <p>钉的是"渲染什么、跳过什么、为什么跳过"：跳过错了会让页面少一块（用户看不见原因），
 * 该跳过的没跳过会渲染出空白（更糟：看起来像坏了）。</p>
 */
describe('装配运行时：槽位计划', () => {
  const diff = diffWorkspaceAssembly(parseWorkspaceLayout(SEED_JSON))!;

  it('按配置顺序翻槽位：指引线→GUIDE、主舞台→MAIN、已实现组件→COMPONENT、其余→SKIP', () => {
    const plan = buildAssemblyPlan(diff.panelRows);
    expect(plan.map((s) => [s.code, s.target])).toEqual([
      ['PROJECT_HEADER', 'COMPONENT'],
      ['STEP_NAVIGATOR', 'GUIDE'],
      ['MAIN_STAGE', 'MAIN'],
      ['INSPECTOR', 'COMPONENT'],
      ['ASSET_DRAWER', 'COMPONENT']
    ]);
    // 跳过的槽位必须带原因（否则界面上只会"少一块"，没人知道为什么）
    for (const slot of plan.filter((s) => s.target === 'SKIP')) {
      expect(slot.reason, `${slot.code} 跳过却没写原因`).toBeTruthy();
    }
  });

  it('真正渲染的槽位与说明文本', () => {
    const plan = buildAssemblyPlan(diff.panelRows);
    expect(assembledSlots(plan).map((s) => s.code)).toEqual([
      'PROJECT_HEADER', 'STEP_NAVIGATOR', 'MAIN_STAGE', 'INSPECTOR', 'ASSET_DRAWER'
    ]);
    expect(describeAssembly(plan)).toBe(
      '装配 5 / 5 个槽位：PROJECT_HEADER、STEP_NAVIGATOR、MAIN_STAGE、INSPECTOR、ASSET_DRAWER'
    );
  });

  it('配置读不到 → 兜底计划仍含项目头部（页面不能连刷新按钮都没了）', () => {
    for (const empty of [null, undefined, []]) {
      const plan = buildAssemblyPlan(empty as never);
      expect(plan.map((s) => s.target)).toEqual(['COMPONENT', 'GUIDE', 'MAIN']);
      expect(plan[0].code).toBe('PROJECT_HEADER');
      expect(plan[0].reason).toContain('项目头部');
      expect(assembledSlots(plan)).toHaveLength(3);
      expect(describeAssembly(plan)).toBe(
        '装配 3 / 3 个槽位：PROJECT_HEADER、STEP_NAVIGATOR、MAIN_STAGE'
      );
    }
  });

  it('只有真注册过的组件才会被装配（没拆成组件 / 没实现的 → SKIP）', () => {
    // R31 起 "QaPanel" 已经是真组件，所以这里用仍是页面内区块的名字来钉同一条规则
    const layout = parseWorkspaceLayout(
      '{"workspace":"W","panels":["STEP_NAVIGATOR","MAIN_STAGE","VisualDnaPanel"],"steps":[]}'
    );
    const d = diffWorkspaceAssembly(layout)!;
    const plan = buildAssemblyPlan(d.panelRows);
    expect(plan.map((s) => [s.code, s.target])).toEqual([
      ['STEP_NAVIGATOR', 'GUIDE'], ['MAIN_STAGE', 'MAIN'], ['VisualDnaPanel', 'SKIP']
    ]);
    expect(plan[2].reason).toContain('页面内区块');

    // "还没实现"这条口径仍然成立：注册表里没有的名字照样只 SKIP 并说明原因
    const missing = parseWorkspaceLayout(
      '{"workspace":"W","panels":["STEP_NAVIGATOR","MAIN_STAGE","FuturePanel"],"steps":[]}'
    );
    const plan2 = buildAssemblyPlan(diffWorkspaceAssembly(missing)!.panelRows);
    expect(plan2[2].target).toBe('SKIP');
    expect(plan2[2].reason).toContain('没有登记');
  });

  it('注入注册表可以改变装配结果（运行时真的按注册表解析，而不是写死名单）', () => {
    const layout = parseWorkspaceLayout(
      '{"workspace":"W","panels":["STEP_NAVIGATOR","MAIN_STAGE","FUTURE_PANEL"],"steps":[]}'
    );
    const withFuture = {
      ...CODE_COMPONENT_REGISTRY,
      FUTURE_PANEL: { kind: 'COMPONENT' as const, location: 'components/FuturePanel.vue', note: '假设已实现' }
    };
    const d = diffWorkspaceAssembly(layout, { registry: withFuture })!;
    const plan = buildAssemblyPlan(d.panelRows, withFuture);
    expect(plan[2]).toEqual({
      code: 'FUTURE_PANEL',
      kind: 'COMPONENT',
      target: 'COMPONENT',
      reason: '已是独立组件，按名字解析'
    });
  });
});
