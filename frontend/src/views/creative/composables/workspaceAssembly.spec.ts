import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  ASSEMBLY_KIND_LABELS,
  CODE_COMPONENT_REGISTRY,
  assembledSlots,
  buildAssemblyPlan,
  describeAssembly,
  diffWorkspaceAssembly,
  formatAssemblyChip,
  hostedSteps,
  layoutOfWorkspace,
  layoutStepsFromRows,
  parseWorkspaceLayout,
  pickVisibleStep,
  referencedSchemaCode
} from './workspaceAssembly';
import {
  IMPLEMENTED_COMPONENT_NAMES,
  resolveWorkspaceComponent
} from '../components/workspace/registry';

/**
 * 工作台装配只读对照的单元测试（V0.2 D 阶段最后一块第一步，R17；R37 起按步骤装配）。
 *
 * <p>基准数据是**生产配置**（`WS_LONG_PAGE`，R37 起步骤组件名改成代码里的真名、
 * 且允许一步多个组件）。这里钉的是"配置与实现的差异被如实算出来"：
 * 分类错了不会抛异常，只会让"还要做什么"看起来比实际少（或我凭空说某件没做的事已经做了）。</p>
 */
const SEED_JSON =
  '{"workspace":"LONG_PAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],' +
  '"steps":[{"code":"INPUT","components":["ProjectAssetsBlock","ProjectBriefBlock"]},' +
  '{"code":"FACT","components":["ProjectFactsBlock","ProjectCopyBlock"]},' +
  '{"code":"DNA","component":"VisualDnaPanel"},{"code":"DIRECTION","component":"DirectionBoard"},' +
  '{"code":"STORYBOARD","component":"StoryboardBoard"},{"code":"GATE","component":"GatePanel"},' +
  '{"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock"]},' +
  '{"code":"QA","component":"QaPanel"},' +
  '{"code":"LAYOUT","component":"LongPageCanvas"},{"code":"FINAL","component":"FinalReviewPanel"}]}';

/**
 * 递归列出某个目录下的所有 `.vue` 文件。
 *
 * @param dir 目录（URL，便于从 import.meta.url 推）
 * @returns 文件路径数组
 */
function listVueFiles(dir: URL): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const child = new URL(entry.name + (entry.isDirectory() ? '/' : ''), dir);
    if (entry.isDirectory()) {
      out.push(...listVueFiles(child));
    } else if (entry.name.endsWith('.vue')) {
      out.push(fileURLToPath(child));
    }
  }
  return out;
}

describe('workspaceAssembly：解析', () => {
  it('解析生产配置：5 个面板 + 10 个步骤（13 个步骤组件，工作台 LONG_PAGE）', () => {
    const layout = parseWorkspaceLayout(SEED_JSON);
    expect(layout).not.toBeNull();
    expect(layout!.workspace).toBe('LONG_PAGE');
    expect(layout!.panels).toEqual([
      'PROJECT_HEADER', 'STEP_NAVIGATOR', 'MAIN_STAGE', 'INSPECTOR', 'ASSET_DRAWER'
    ]);
    expect(layout!.steps.map((s) => s.code)).toEqual([
      'INPUT', 'FACT', 'DNA', 'DIRECTION', 'STORYBOARD', 'GATE', 'GENERATION', 'QA', 'LAYOUT', 'FINAL'
    ]);
    // 一步多组件（R37）与旧写法（单 component）归一成同一个形状
    expect(layout!.steps[0]).toEqual({
      code: 'INPUT', components: ['ProjectAssetsBlock', 'ProjectBriefBlock']
    });
    expect(layout!.steps[2]).toEqual({ code: 'DNA', components: ['VisualDnaPanel'] });
  });

  it('一步多组件：components 优先，旧 component 写法也认（配置改一半也不会突然空掉）', () => {
    const both = parseWorkspaceLayout(
      '{"workspace":"W","panels":[],"steps":[{"code":"X","component":"Old","components":["New1","New2"]}]}'
    )!;
    expect(both.steps[0].components).toEqual(['New1', 'New2']);
    const legacy = parseWorkspaceLayout(
      '{"workspace":"W","panels":[],"steps":[{"code":"X","component":"Old"}]}'
    )!;
    expect(legacy.steps[0].components).toEqual(['Old']);
    // 空数组 / 空串 / 非字符串项都不算组件（不编造）
    const dirty = parseWorkspaceLayout(
      '{"workspace":"W","panels":[],"steps":[{"code":"X","components":["",5,null,"Y"]},{"code":"Z","components":[]}]}'
    )!;
    expect(dirty.steps[0].components).toEqual(['Y']);
    expect(dirty.steps[1].components).toEqual([]);
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
    // 脏项不丢：code 缺就补空串、组件缺就是空数组，交给对照去报"没给组件"
    expect(layout!.steps).toEqual([
      { code: 'X', components: [] },
      { code: '', components: ['Y'] }
    ]);
  });
});

describe('workspaceAssembly：对照', () => {
  const diff = diffWorkspaceAssembly(parseWorkspaceLayout(SEED_JSON))!;

  it('18 项全对照：5 面板 + 13 个步骤组件（10 步，其中 3 步各两块）', () => {
    expect(diff.panelRows).toHaveLength(5);
    expect(diff.stepRows).toHaveLength(13);
    expect(diff.panelRows.map((r) => r.code)).toEqual([
      'PROJECT_HEADER', 'STEP_NAVIGATOR', 'MAIN_STAGE', 'INSPECTOR', 'ASSET_DRAWER'
    ]);
  });

  it('分类如实：R39 起分镜页两步也拆出装配组件，只剩视觉门/长图/终审还是页面内区块', () => {
    const byName = Object.fromEntries(
      [...diff.panelRows, ...diff.stepRows].map((r) => [r.component || r.code, r.kind])
    );
    expect(byName.STEP_NAVIGATOR).toBe('COMPONENT');
    expect(byName.INSPECTOR).toBe('COMPONENT');
    expect(byName.ASSET_DRAWER).toBe('COMPONENT');
    // 【R31 起期望值有变，不是回归】这两个本轮真做了（此前是 SECTION / MISSING）
    expect(byName.PROJECT_HEADER).toBe('COMPONENT');
    expect(byName.QaPanel).toBe('COMPONENT');
    // 【R37 起期望值有变】项目页六个区块已是真组件，但它们要页面状态 → 由页面以同名插槽提供
    expect(byName.ProjectAssetsBlock).toBe('SLOT');
    expect(byName.ProjectBriefBlock).toBe('SLOT');
    expect(byName.ProjectFactsBlock).toBe('SLOT');
    expect(byName.ProjectCopyBlock).toBe('SLOT');
    expect(byName.ProjectHeroBlock).toBe('SLOT');
    expect(byName.ProjectGenerationsBlock).toBe('SLOT');
    // 【R38 起期望值有变】基因页整页拆成 VisualDnaPanel
    expect(byName.VisualDnaPanel).toBe('SLOT');
    // 【R39 起期望值有变】分镜页两个步骤各拆成一个组件
    expect(byName.DirectionBoard).toBe('SLOT');
    expect(byName.StoryboardBoard).toBe('SLOT');
    // 剩下三个还在评审页里（拆组件这件事一轮做不完，如实登记）
    expect(byName.GatePanel).toBe('SECTION');
    expect(byName.LongPageCanvas).toBe('SECTION');
    expect(byName.FinalReviewPanel).toBe('SECTION');
    expect(diff.componentCount).toBe(5);
    expect(diff.slotCount).toBe(9);
    expect(diff.sectionCount).toBe(4);
    expect(diff.missingCount).toBe(0);
    // 口径：就绪 = 已是独立组件（工作台自解析 5 + 宿主插槽 9）；分母是组件行数（不是步骤数）
    expect(diff.readyText).toBe('14/18');
    expect(formatAssemblyChip(diff)).toBe('工作台装配 14/18');
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

  it('结论句只用算出的事实拼（含工作台名、各计数、共 18 项）', () => {
    expect(diff.verdict).toContain('LONG_PAGE');
    expect(diff.verdict).toContain('配置声明 5 个面板 + 10 个步骤');
    expect(diff.verdict).toContain('共 13 个步骤组件');
    expect(diff.verdict).toContain('已是独立组件 14 个');
    expect(diff.verdict).toContain('工作台自行解析 5 个、宿主页面插槽提供 9 个');
    expect(diff.verdict).toContain('还没实现 0 个');
    expect(diff.verdict).toContain('共对照 18 项');
  });

  it('注册表与配置互相校验：注册表里多出来的名字要报"没用上"', () => {
    // 生产配置用到了注册表里的每一个名字，所以这里应当为空
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
    expect(d.unusedInConfig).toContain('ProjectAssetsBlock');
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

  it('注册表自身的口径：分类合法、落点与说明都不空、配置里的名字都有登记', () => {
    const kinds = ['COMPONENT', 'SLOT', 'SECTION', 'MISSING'];
    for (const [name, entry] of Object.entries(CODE_COMPONENT_REGISTRY)) {
      expect(kinds, `${name} 的分类 ${entry.kind} 不合法`).toContain(entry.kind);
      expect(entry.location, `${name} 缺落点`).toBeTruthy();
      expect(entry.note, `${name} 缺说明`).toBeTruthy();
    }
    const layout = parseWorkspaceLayout(SEED_JSON)!;
    const names = [...layout.panels, ...layout.steps.flatMap((s) => s.components)];
    for (const name of names) {
      expect(CODE_COMPONENT_REGISTRY[name], `配置里的 ${name} 没在注册表登记`).toBeTruthy();
    }
    expect(Object.keys(ASSEMBLY_KIND_LABELS)).toEqual(kinds);
  });

  it('描述表里标 SLOT 的组件必须由某个页面真实提供插槽（否则装配会少一块而没人发现）', () => {
    // 静态核对：扫 views/creative 下的所有 .vue，检查每个 SLOT 组件都有 `<template #名字>`
    // （R19 的教训就是"按钮存在、抽屉没人渲染"——只断言名单存在是不够的）。
    // R38 起组件分散在不同页面（项目页六个区块、基因页 VisualDnaPanel…），所以扫整棵目录。
    const slotFiles = new Map<string, string>();
    for (const file of listVueFiles(new URL('../', import.meta.url))) {
      slotFiles.set(file, readFileSync(file, 'utf-8'));
    }
    const slotNames = Object.entries(CODE_COMPONENT_REGISTRY)
      .filter(([, entry]) => entry.kind === 'SLOT')
      .map(([name]) => name);
    expect(slotNames.length).toBeGreaterThan(0);
    for (const name of slotNames) {
      const hit = [...slotFiles.entries()].find(([, text]) => text.includes(`#${name}`));
      expect(hit?.[0], `没有任何页面提供 #${name} 插槽（注册表说有、代码里没有）`).toBeTruthy();
    }
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
    // R37：宿主插槽组件**不能**被工作台自己解析出来——它们要页面状态，由页面提供插槽。
    // 万一有人把它们也加进注册表，工作台会用一个只有 taskId 的组件去渲染 → 满屏空数据。
    for (const [name, entry] of Object.entries(CODE_COMPONENT_REGISTRY)) {
      if (entry.kind === 'SLOT') {
        expect(resolveWorkspaceComponent(name), `${name} 是宿主插槽组件，不该被工作台自行解析`).toBeNull();
      }
    }
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

  it('只有真注册过的组件才会被装配（没拆成组件 / 宿主插槽 / 没实现的 → SKIP）', () => {
    // R38 起：SLOT（宿主插槽组件）也不能作为面板被工作台解析，理由要说清是"哪一类"
    const layout = parseWorkspaceLayout(
      '{"workspace":"W","panels":["STEP_NAVIGATOR","MAIN_STAGE","GatePanel","VisualDnaPanel"],"steps":[]}'
    );
    const d = diffWorkspaceAssembly(layout)!;
    const plan = buildAssemblyPlan(d.panelRows);
    expect(plan.map((s) => [s.code, s.target])).toEqual([
      ['STEP_NAVIGATOR', 'GUIDE'], ['MAIN_STAGE', 'MAIN'],
      ['GatePanel', 'SKIP'], ['VisualDnaPanel', 'SKIP']
    ]);
    expect(plan[2].reason).toContain('页面内区块');
    expect(plan[3].reason).toContain('宿主插槽组件');

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

/**
 * R37：按步骤装配——"这一步显示哪几块"。
 *
 * <p>钉的是**当前步判定**：判错不会报错，只会让页面显示另一批内容（用户以为功能坏了）。
 * 尤其是"本页早期步骤都做完之后"这一类：那时第一个未完成才是该看的那一步。</p>
 */
describe('R37：按步骤装配', () => {
  const diff = diffWorkspaceAssembly(parseWorkspaceLayout(SEED_JSON))!;

  it('从对照行还原步骤：一步多组件合并成一条，没给组件的步骤如实留着', () => {
    const steps = layoutStepsFromRows(diff.stepRows);
    expect(steps.map((s) => s.code)).toEqual([
      'INPUT', 'FACT', 'DNA', 'DIRECTION', 'STORYBOARD', 'GATE', 'GENERATION', 'QA', 'LAYOUT', 'FINAL'
    ]);
    expect(steps[0].components).toEqual(['ProjectAssetsBlock', 'ProjectBriefBlock']);
    expect(steps[6].components).toEqual(['ProjectHeroBlock', 'ProjectGenerationsBlock']);
    expect(steps[2].components).toEqual(['VisualDnaPanel']);
    // 没有步骤行 / 空 → 空数组（调用方据此回落到 #main，不白屏）
    expect(layoutStepsFromRows(null)).toEqual([]);
    expect(layoutStepsFromRows([])).toEqual([]);
    // 没给组件的步骤要留成空数组，而不是被丢掉
    const noComponent = layoutStepsFromRows(
      diffWorkspaceAssembly(parseWorkspaceLayout('{"workspace":"W","panels":[],"steps":[{"code":"X"}]}'))!.stepRows
    );
    expect(noComponent).toEqual([{ code: 'X', components: [] }]);
  });

  it('本页面托管的步骤 = 配置里有、且页面提供了插槽的那些（按配置顺序）', () => {
    const steps = layoutStepsFromRows(diff.stepRows);
    // 项目页只提供了六个区块的插槽 → 托管 INPUT / FACT / GENERATION 三步
    const hosted = hostedSteps(steps, [
      'ProjectAssetsBlock', 'ProjectBriefBlock', 'ProjectCopyBlock', 'ProjectFactsBlock',
      'ProjectHeroBlock', 'ProjectGenerationsBlock'
    ]);
    expect(hosted.map((s) => s.code)).toEqual(['INPUT', 'FACT', 'GENERATION']);
    expect(hosted[0].components).toEqual(['ProjectAssetsBlock', 'ProjectBriefBlock']);
    // 一个插槽都没提供（别的页面还是整页主区）→ 本页不托管任何步骤 → 回落到 #main
    expect(hostedSteps(steps, [])).toEqual([]);
    // 只提供了一半：那一步仍然是"托管的"，但组件如实少一个（剩下的由页面提示说明）
    const partial = hostedSteps(steps, ['ProjectHeroBlock']);
    expect(partial).toEqual([{ code: 'GENERATION', components: ['ProjectHeroBlock'] }]);
  });

  it('当前步判定：全局当前步优先，其次进行中，其次第一个没了结的，全了结时取最后一步', () => {
    const hosted = [
      { code: 'INPUT', status: 'done' },
      { code: 'FACT', status: 'done' },
      { code: 'GENERATION', status: 'todo' }
    ];
    // ① 全局当前步在本页 → 就用它（用户点了指引线跳过来的那一步）
    expect(pickVisibleStep(hosted, 'FACT')).toBe('FACT');
    // ② 全局当前步在别的页面（例如 DNA）→ 本页取第一个没了结的（不是回到已完成的 INPUT）
    expect(pickVisibleStep(hosted, 'DNA')).toBe('GENERATION');
    expect(pickVisibleStep(hosted, '')).toBe('GENERATION');
    // ③ 有进行中的 → 取进行中的（即使前面还有没了结的）
    expect(pickVisibleStep([
      { code: 'INPUT', status: 'done' },
      { code: 'FACT', status: 'blocked' },
      { code: 'GENERATION', status: 'doing' }
    ], 'DNA')).toBe('GENERATION');
    // ④ 全了结（含跳过）→ 取最后一步：刚做完的事还在眼前，而不是跳回第一步
    expect(pickVisibleStep([
      { code: 'INPUT', status: 'done' },
      { code: 'FACT', status: 'skipped' },
      { code: 'GENERATION', status: 'done' }
    ], 'DNA')).toBe('GENERATION');
    // 边界：托管列表为空 → null（调用方回落到 #main）
    expect(pickVisibleStep([], 'FACT')).toBeNull();
    // 边界：状态缺失按"没了结"处理（不猜成已完成）
    expect(pickVisibleStep([{ code: 'INPUT' }, { code: 'FACT', status: 'done' }], 'DNA')).toBe('INPUT');
  });
});

/**
 * R38：基因页这一步拆成装配组件（`VisualDnaPanel`）；R39：分镜页两步各拆一个。
 *
 * <p>组件拆分最容易出的两种问题，静态就能钉住：① 动作在搬运中丢了（模板里少了 `@save`，
 * 类型系统不会报——emit 没人接就是静默失效，R19 的"按钮在、抽屉没人渲染"是同一类）；
 * ② 内容搬了两份（页面与组件各留一份，改一边就不生效）。</p>
 */
describe('R38 / R39：页面这一步的装配组件', () => {
  const dnaPage = readFileSync(new URL('../dna/index.vue', import.meta.url), 'utf-8');
  const panel = readFileSync(new URL('../dna/components/VisualDnaPanel.vue', import.meta.url), 'utf-8');
  const sbPage = readFileSync(new URL('../storyboard/index.vue', import.meta.url), 'utf-8');
  const dirBoard = readFileSync(new URL('../storyboard/components/DirectionBoard.vue', import.meta.url), 'utf-8');
  const sbBoard = readFileSync(new URL('../storyboard/components/StoryboardBoard.vue', import.meta.url), 'utf-8');

  it('基因页用同名插槽把这一步交给工作台，并把每个动作都接上', () => {
    expect(dnaPage).toContain('#VisualDnaPanel');
    for (const binding of [
      '@generate="doGenerate"',
      '@save="doSave"',
      '@lock="doLock"',
      '@load-prompt="loadPrompt"',
      '@view-version="viewVersion"'
    ]) {
      expect(dnaPage, `基因页没有把 ${binding} 接上`).toContain(binding);
    }
    // 组件这一侧要真的声明并发出这些事件
    for (const name of ["'generate'", "'save'", "'lock'", "'load-prompt'", "'view-version'"]) {
      expect(panel, `组件没有声明事件 ${name}`).toContain(name);
    }
  });

  it('内容只留一份：页面里不再有这一步的内容（否则改一边不生效）', () => {
    expect(dnaPage).not.toContain('data-dna-section');
    expect(dnaPage).not.toContain('规范内容');
    expect(dnaPage).not.toContain('版本历史');
    // 组件里该有的分段标记齐全（验收脚本按它断言"五段都在"）
    for (const section of ['OVERVIEW', 'FORM', 'PROMPT', 'EVIDENCE', 'VERSIONS']) {
      expect(panel, `组件缺少 ${section} 分段标记`).toContain(`data-dna-section="${section}"`);
    }
  });

  it('分镜页：两步各自的插槽与动作都接上，页面里不再有这两块内容', () => {
    expect(sbPage).toContain('#DirectionBoard');
    expect(sbPage).toContain('#StoryboardBoard');
    // 页头与页面级跨步骤内容也要各就各位
    expect(sbPage).toContain('#page-head');
    expect(sbPage).toContain('#page-foot');
    for (const binding of [
      '@generate="doGenerateDirections"',
      '@select="doSelect"',
      '@edit="openDirectionEdit"',
      '@generate="doGenerateStoryboard"',
      '@lock="doLockStoryboard"',
      '@edit-screen="openScreenEdit"'
    ]) {
      expect(sbPage, `分镜页没有把 ${binding} 接上`).toContain(binding);
    }
    // 内容只留一份：方向卡片/分镜卡片的选择器不该再出现在页面里
    expect(sbPage).not.toContain('direction-card');
    expect(sbPage).not.toContain('screen-card');
    expect(dirBoard).toContain('data-board-section="DIRECTIONS"');
    expect(sbBoard).toContain('data-board-section="SCREENS"');
    // 两个组件各自声明了动作
    for (const name of ["'generate'", "'select'", "'edit'"]) {
      expect(dirBoard, `DirectionBoard 没有声明事件 ${name}`).toContain(name);
    }
    for (const name of ["'generate'", "'lock'", "'edit-screen'"]) {
      expect(sbBoard, `StoryboardBoard 没有声明事件 ${name}`).toContain(name);
    }
  });
});
