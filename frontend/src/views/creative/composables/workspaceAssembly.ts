import type { ScenarioProfile, ScenarioWorkspace } from '@/api/creative/scenario';

/**
 * 工作台装配的**只读对照**（V0.2 D 阶段最后一块的第一步，R17）。
 *
 * <p>配置里（`dp_workspace_schema.layout_json`，文档 §49 口径）声明了工作台怎么装配：
 * 5 个面板（`PROJECT_HEADER / STEP_NAVIGATOR / MAIN_STAGE / INSPECTOR / ASSET_DRAWER`）+
 * 十步各自的组件（`INPUT→ProjectInputPanel`、`DNA→VisualDnaPanel`…）。</p>
 *
 * <p><b>但代码里现在不是这么长的</b>：六个页面是各自成篇的 `.vue`（`project/index.vue` 就有 10 万字节），
 * 还没有“面板/组件”这层装配结构。所以这一步**先做对照，不切装配**：
 * 把「配置声明了什么」与「代码里真实在哪」逐项摆出来，让"切换装配要做什么"变成一份可核对的清单，
 * 而不是一句"重构一下就行"。</p>
 *
 * <p><b>分类口径</b>（`AssemblyKind`）：</p>
 * <ul>
 *   <li>{@code COMPONENT}：已经是可复用的独立组件（装配能直接引它）；</li>
 *   <li>{@code SECTION}：现在写在某个页面里的一段（装配前要拆成组件）；</li>
 *   <li>{@code MISSING}：代码里还没有对应实现（要么新建，要么在配置里声明"并入某步"）。</li>
 * </ul>
 *
 * <p>纯函数：只 `import type`，可被 vitest 在 node 环境直接断言。</p>
 *
 * @author creative
 */

/** 代码侧的落点分类 */
export type AssemblyKind = 'COMPONENT' | 'SECTION' | 'MISSING';

/** 代码侧登记表的一行 */
export interface RegistryEntry {
  kind: AssemblyKind;
  /** 在代码里的落点（文件 + 位置说明）；MISSING 时为"没有实现"的说明 */
  location: string;
  /** 一句话补充（为什么这样分类 / 装配时要注意什么） */
  note: string;
}

/**
 * **代码侧组件注册表**：配置里出现的每个组件/面板名 → 它在代码里的真实落点。
 *
 * <p>这张表是**人的判断**，登记的是"此刻代码里到底有什么"，改代码就要改它。
 * 单测会钉住：配置里出现的每个名字都必须在这张表里有登记（否则对照会漏项），
 * 以及"名字不能凭空多出来"（表里有的、配置里没用到的会被报成 `unusedInConfig`）。</p>
 */
export const CODE_COMPONENT_REGISTRY: Record<string, RegistryEntry> = {
  // ---- 面板（工作台骨架）----
  PROJECT_HEADER: {
    kind: 'COMPONENT',
    location: 'components/CreativeProjectHeader.vue（R31 真做）',
    note: '文档 §23 头部：项目/SKU/渠道/当前阶段/负责人/输出规格（渠道与规格取 dp_output_spec 配置，不写死）'
  },
  STEP_NAVIGATOR: {
    kind: 'COMPONENT',
    location: 'components/CreativeFlowGuide.vue',
    note: 'R16 起就是配置驱动的十步指引线，已经是可复用组件（五个页面共用）'
  },
  MAIN_STAGE: {
    kind: 'SECTION',
    location: 'dna / storyboard / review / production 各页主区',
    note: '“主舞台”是概念上的位置，目前由各页面自己的主区承担，没有容器组件'
  },
  INSPECTOR: {
    kind: 'COMPONENT',
    location: 'components/CreativeInspectorPanel.vue（R18 真做）',
    note: '细节检查器：当前环节 + 这一步的判断依据 + 最近事件（只读，随指引线出现在每页）'
  },
  ASSET_DRAWER: {
    kind: 'COMPONENT',
    location: 'components/CreativeAssetDrawer.vue（R18 真做）',
    note: '资产抽屉：参考图/产品图、出图候选、排版版本集中预览（只读；缩略图自动加载上限 12 张）'
  },

  // ---- 十步各自的组件 ----
  ProjectInputPanel: {
    kind: 'SECTION',
    location: 'project/index.vue「参考图 / 产品图」（:87）',
    note: '含产品图绑定与附件上传，与品牌要求/文案同页'
  },
  FactPanel: {
    kind: 'SECTION',
    location: 'project/index.vue「事实确认」（:355，含闸门必填项 :383 与事实清单 :411）',
    note: '事实在内容域确认，创作域只读展示'
  },
  VisualDnaPanel: {
    kind: 'SECTION',
    location: 'dna/index.vue 整页（概览 :43 / 编辑 :97 / 证据链 :224 / 版本 :238）',
    note: '整页就是一个组件的工作量，装配时按「一步一页」的现状直接对应'
  },
  DirectionBoard: {
    kind: 'SECTION',
    location: 'storyboard/index.vue「方向」（:27）',
    note: '方向与分镜同页，装配时是同页两个组件'
  },
  StoryboardBoard: {
    kind: 'SECTION',
    location: 'storyboard/index.vue「分镜」（:96，逐屏生产 :160）',
    note: '同上'
  },
  GatePanel: {
    kind: 'SECTION',
    location: 'review/index.vue「门禁状态 :28 / 准入项 :77 / 人工确认 :101」',
    note: '闸门判定在后端，这里是展示与人工确认入口'
  },
  GenerationBoard: {
    kind: 'SECTION',
    location: 'production/index.vue 候选区（逐屏 :42 / 跨项目总览 :196）+ project/index.vue 出图区（:491）',
    note: '出图入口在两处：项目页发起、生产页管理候选'
  },
  QaPanel: {
    kind: 'COMPONENT',
    location: 'components/CreativeQaPanel.vue（R31 真做）+ composables/qaVerdicts.ts（口径，纯函数有单测）',
    note: '质检与交付：四条证据线并排（参考图基准 / 产品基准 / 规则体检 / 交付产物），只读；口径集中在 qaVerdicts'
  },
  LongPageCanvas: {
    kind: 'SECTION',
    location: 'review/index.vue「机排版与终审」（:141）',
    note: '长图画布与终审同区块'
  },
  FinalReviewPanel: {
    kind: 'SECTION',
    location: 'review/index.vue「机排版与终审」（:141）的终审/最终版部分',
    note: '与 LongPageCanvas 同区块，装配时要拆开'
  }
};

/** 工作台装配定义（解析 `layout_json` 的结果） */
export interface WorkspaceLayout {
  /** 工作台类型（如 LONG_PAGE） */
  workspace: string;
  /** 面板编码 */
  panels: string[];
  /** 步骤 → 组件 */
  steps: Array<{ code: string; component: string }>;
}

/** 对照结果的一行 */
export interface AssemblyRow {
  /** 步骤编码或面板编码 */
  code: string;
  /** 配置里声明的组件名；步骤没给组件时为空串 */
  component: string;
  kind: AssemblyKind;
  location: string;
  note: string;
}

/** 装配对照结果 */
export interface AssemblyDiff {
  /** 工作台类型；解析不出来为空串 */
  workspace: string;
  /** 十步的组件对照 */
  stepRows: AssemblyRow[];
  /** 面板对照 */
  panelRows: AssemblyRow[];
  /** 已是独立组件的数量 */
  componentCount: number;
  /** 页面内区块的数量 */
  sectionCount: number;
  /** 还没有实现的数量 */
  missingCount: number;
  /** 配置里声明了步骤但没给组件 */
  stepsWithoutComponent: string[];
  /** 配置里有、注册表里没有登记的面板或组件（会如实报出来，不静默略过） */
  unknownNames: string[];
  /** 注册表里登记了、但这份配置没用到的名字 */
  unusedInConfig: string[];
  /** 档案引用的工作台编码与实际取到的不一致时的提示（一致则为空串） */
  schemaCodeWarning: string;
  /** 已就绪 / 总数，形如 `1/15` */
  readyText: string;
  /** 一句话结论（界面 hover 提示与单测都断言它） */
  verdict: string;
}

/**
 * 解析 `layout_json`（容错：不合法就返回 null，调用方据此不渲染对照）。
 *
 * @param json 配置里的 layout_json 字符串
 * @returns 装配定义；解析不出来返回 null
 */
export function parseWorkspaceLayout(json?: string | null): WorkspaceLayout | null {
  if (!json) {
    return null;
  }
  try {
    const parsed = JSON.parse(json) as Partial<WorkspaceLayout>;
    if (!parsed || typeof parsed !== 'object') {
      return null;
    }
    const panels = Array.isArray(parsed.panels) ? parsed.panels.filter((p) => typeof p === 'string') : [];
    const steps = Array.isArray(parsed.steps)
      ? parsed.steps
          .filter((s) => s && typeof s === 'object')
          .map((s) => {
            const row = s as { code?: unknown; component?: unknown };
            return { code: String(row.code ?? ''), component: String(row.component ?? '') };
          })
      : [];
    if (!panels.length && !steps.length) {
      return null;
    }
    return { workspace: String(parsed.workspace ?? ''), panels, steps };
  } catch (e) {
    // 配置写坏了：不猜，交给调用方当作"没有装配定义"
    return null;
  }
}

/**
 * 从工作台装配里取装配定义。
 *
 * <p><b>为什么不是从场景档案取</b>：档案里的 `workspaceSchemaJson` 只是**引用**
 * （内容形如 `{"schemaCode":"WS_LONG_PAGE"}`），装配定义在 `dp_workspace_schema.layout_json`。
 * R17 第一版就取错了源，结果是"对照整块静默不显示"——不报错、只是没了，最难发现的那种。</p>
 *
 * @param workspace 工作台装配（`GET /creative/v2/scenarios/{type}/workspace`）
 * @returns 装配定义；缺失或 layout_json 坏掉都返回 null
 */
export function layoutOfWorkspace(workspace?: ScenarioWorkspace | null): WorkspaceLayout | null {
  return parseWorkspaceLayout(workspace?.layoutJson);
}

/**
 * 取场景档案里**引用**的工作台编码（形如 `WS_LONG_PAGE`）。
 *
 * <p>用它和实际取到的工作台装配对账：档案说引用 A、装配表里发布的是 B，就是配置不一致，
 * 应该被看见而不是被将就。</p>
 *
 * @param profile 场景档案
 * @returns 引用的 schemaCode；解析不出来返回空串
 */
export function referencedSchemaCode(profile?: ScenarioProfile | null): string {
  const json = profile?.workspaceSchemaJson;
  if (!json) {
    return '';
  }
  try {
    const parsed = JSON.parse(json) as { schemaCode?: unknown };
    return parsed && typeof parsed.schemaCode === 'string' ? parsed.schemaCode : '';
  } catch (e) {
    return '';
  }
}

/** 取注册表条目（未登记时给一个"未登记"的占位，而不是丢掉这一行） */
function entryOf(name: string): RegistryEntry {
  const hit = CODE_COMPONENT_REGISTRY[name];
  if (hit) {
    return hit;
  }
  return {
    kind: 'MISSING',
    location: '注册表里没有登记',
    note: '配置里出现的名字在代码侧注册表里查不到——要么代码还没实现，要么注册表漏登记（两者都要有人处理）'
  };
}

/** 造一行对照 */
function rowOf(code: string, component: string, registry: Record<string, RegistryEntry>): AssemblyRow {
  const entry = registry[component] ?? entryOf(component);
  return { code, component, kind: entry.kind, location: entry.location, note: entry.note };
}

/**
 * 逐项对照「配置声明的装配」与「代码里的真实落点」。
 *
 * @param layout        装配定义（`layoutOfWorkspace` 的结果）
 * @param options.registry        代码侧注册表（默认 {@link CODE_COMPONENT_REGISTRY}；测试可注入）
 * @param options.referencedCode  场景档案里引用的工作台编码（用于对账，可空）
 * @returns 对照结果；layout 为空时返回 null（调用方据此不渲染）
 */
export function diffWorkspaceAssembly(
  layout: WorkspaceLayout | null,
  options: {
    registry?: Record<string, RegistryEntry>;
    referencedCode?: string;
    actualCode?: string;
  } = {}
): AssemblyDiff | null {
  if (!layout) {
    return null;
  }
  const registry = options.registry ?? CODE_COMPONENT_REGISTRY;
  const panelRows = layout.panels.map((panel) => rowOf(panel, panel, registry));
  const stepRows = layout.steps.map((step) => rowOf(step.code, step.component, registry));
  const rows = [...panelRows, ...stepRows];

  const componentCount = rows.filter((r) => r.kind === 'COMPONENT').length;
  const sectionCount = rows.filter((r) => r.kind === 'SECTION').length;
  const missingCount = rows.filter((r) => r.kind === 'MISSING').length;

  const stepsWithoutComponent = layout.steps.filter((s) => !s.component).map((s) => s.code);
  const namesInConfig = new Set(rows.map((r) => r.component).filter(Boolean));
  const unknownNames = Array.from(namesInConfig).filter((name) => !registry[name]);
  const unusedInConfig = Object.keys(registry).filter((name) => !namesInConfig.has(name));
  const referenced = (options.referencedCode || '').trim();
  const actual = (options.actualCode || '').trim();
  const schemaCodeWarning =
    referenced && actual && referenced !== actual
      ? `场景档案引用的是 ${referenced}，而发布的工作台是 ${actual}——两者不一致，先对齐再说装配`
      : '';

  return {
    workspace: layout.workspace,
    stepRows,
    panelRows,
    componentCount,
    sectionCount,
    missingCount,
    stepsWithoutComponent,
    unknownNames,
    unusedInConfig,
    schemaCodeWarning,
    readyText: rows.length ? `${componentCount}/${rows.length}` : '',
    verdict: buildAssemblyVerdict(layout, rows.length, componentCount, sectionCount, missingCount,
      unknownNames, stepsWithoutComponent, schemaCodeWarning)
  };
}

/**
 * 组装结论句（**只用算出来的事实拼**：改配置或改注册表，结论自己会变）。
 *
 * @param layout                装配定义
 * @param total                 对照总条数
 * @param componentCount        已是独立组件的条数
 * @param sectionCount          页面内区块的条数
 * @param missingCount          还没实现的条数
 * @param unknownNames          注册表里没登记的名字
 * @param stepsWithoutComponent 声明了步骤但没给组件
 * @param schemaCodeWarning     档案引用与实际工作台不一致的提示
 * @returns 结论文本
 */
function buildAssemblyVerdict(
  layout: WorkspaceLayout,
  total: number,
  componentCount: number,
  sectionCount: number,
  missingCount: number,
  unknownNames: string[],
  stepsWithoutComponent: string[],
  schemaCodeWarning: string
): string {
  const parts: string[] = [
    `工作台 ${layout.workspace || '(未声明)'}：配置声明 ${layout.panels.length} 个面板 + ${layout.steps.length} 个步骤组件`
  ];
  parts.push(`已是独立组件 ${componentCount} 个、页面内区块 ${sectionCount} 个、还没实现 ${missingCount} 个（共对照 ${total} 项）`);
  if (missingCount) {
    parts.push('未实现的要在装配前补齐或在配置里声明并入某步');
  }
  if (unknownNames.length) {
    parts.push(`注册表里没有登记：${unknownNames.join('、')}`);
  }
  if (stepsWithoutComponent.length) {
    parts.push(`配置里没给组件的步骤：${stepsWithoutComponent.join('、')}`);
  }
  if (schemaCodeWarning) {
    parts.push(schemaCodeWarning);
  }
  return parts.join('；');
}

/**
 * 界面上那枚胶囊的文字（形如 `工作台装配 1/15`）。
 *
 * @param diff 对照结果
 * @returns 胶囊文本；没有对照数据返回空串
 */
export function formatAssemblyChip(diff: AssemblyDiff | null): string {
  if (!diff || !diff.readyText) {
    return '';
  }
  return `工作台装配 ${diff.readyText}`;
}

/** 分类的中文标签（界面用） */
export const ASSEMBLY_KIND_LABELS: Record<AssemblyKind, string> = {
  COMPONENT: '已是组件',
  SECTION: '页面内区块',
  MISSING: '未实现'
};

// ---------------------------------------------------------------------------
// R19：装配运行时——把配置里的面板清单翻成"这次要渲染哪些槽位"
// ---------------------------------------------------------------------------

/** 一个槽位要渲染成什么 */
export type AssemblyTarget =
  /** 步骤导航（指引线） */
  | 'GUIDE'
  /** 主舞台：页面自己的内容（通过 main 插槽接进来） */
  | 'MAIN'
  /** 从真实组件注册表里解析组件 */
  | 'COMPONENT'
  /** 这次不渲染（还没拆成组件 / 还没实现 / 没登记），但要如实给出原因 */
  | 'SKIP';

/** 装配计划里的一条槽位 */
export interface AssemblySlot {
  /** 面板编码（与配置里的名字一致） */
  code: string;
  /** 面板/组件分类 */
  kind: AssemblyKind;
  /** 渲染成什么 */
  target: AssemblyTarget;
  /** 为什么这样安排（SKIP 时尤其要说清） */
  reason: string;
}

/**
 * 把配置里的面板清单翻成装配计划（R19）。
 *
 * <p>规则（左侧是配置里的名字，右侧是这次渲染成什么）：</p>
 * <ul>
 *   <li>{@code STEP_NAVIGATOR} → {@code GUIDE}（指引线，五页共用的那个组件）；</li>
 *   <li>{@code MAIN_STAGE} → {@code MAIN}（页面自身内容，由 main 插槽接进来）；</li>
 *   <li>已经是独立组件的（{@code COMPONENT}）→ {@code COMPONENT}（运行时按名字解析）；</li>
 *   <li>页面内区块（{@code SECTION}）与未实现（{@code MISSING}）→ {@code SKIP}，
 *       并给出"还没拆成组件/还没实现"的原因——**不渲染空白，也不假装装配成功**。</li>
 * </ul>
 *
 * <p>配置读不到时给一份**兜底计划**（指引线 + 主舞台）：这就是改造前四个页面的样子，
 * 也就是说配置层挂了只是"少了配置驱动的那几块"，不会把页面弄空。</p>
 *
 * @param panelRows 装配对照里的面板行（`AssemblyDiff.panelRows`）；为空表示配置读不到
 * @param registry  真实组件注册表（用于判断"能不能解析出组件"）
 * @returns 按配置顺序的装配计划
 */
export function buildAssemblyPlan(
  panelRows: AssemblyRow[] | null | undefined,
  registry: Record<string, unknown> = CODE_COMPONENT_REGISTRY
): AssemblySlot[] {
  if (!panelRows || !panelRows.length) {
    return [
      { code: 'STEP_NAVIGATOR', kind: 'COMPONENT', target: 'GUIDE', reason: '配置读不到，按既有布局渲染指引线' },
      { code: 'MAIN_STAGE', kind: 'SECTION', target: 'MAIN', reason: '配置读不到，按既有布局渲染页面内容' }
    ];
  }
  return panelRows.map((row) => {
    if (row.code === 'STEP_NAVIGATOR') {
      return { code: row.code, kind: row.kind, target: 'GUIDE' as AssemblyTarget, reason: '步骤导航：五页共用的指引线组件' };
    }
    if (row.code === 'MAIN_STAGE') {
      return { code: row.code, kind: row.kind, target: 'MAIN' as AssemblyTarget, reason: '主舞台：页面自身内容通过 main 插槽接入' };
    }
    // R31：先把"注册表里根本没登记"与"登记了但还没实现"分开说。
    // 两者的处置完全不同（前者要补登记或补代码，后者是排期问题），笼统写"没实现"会让人白找代码。
    const name = row.component || row.code;
    if (!registry[name]) {
      return {
        code: row.code,
        kind: row.kind,
        target: 'SKIP' as AssemblyTarget,
        reason: '注册表里没有登记（要么代码还没实现，要么注册表漏登记——两者都要有人处理）'
      };
    }
    if (row.kind === 'COMPONENT' && registry[name]) {
      return { code: row.code, kind: row.kind, target: 'COMPONENT' as AssemblyTarget, reason: '已是独立组件，按名字解析' };
    }
    return {
      code: row.code,
      kind: row.kind,
      target: 'SKIP' as AssemblyTarget,
      reason:
        row.kind === 'SECTION'
          ? '还是页面内区块，没拆成组件——拆完才会参与装配'
          : '代码里还没实现（或在配置里声明并入某步）'
    };
  });
}

/**
 * 装配计划里真正会渲染出来的槽位（GUIDE / MAIN / COMPONENT）。
 *
 * @param plan 装配计划
 * @returns 会渲染的槽位
 */
export function assembledSlots(plan: AssemblySlot[]): AssemblySlot[] {
  return plan.filter((slot) => slot.target !== 'SKIP');
}

/**
 * 一句话说明这次装配（给界面与验收用，如 `装配 4 / 5 个槽位：STEP_NAVIGATOR、MAIN_STAGE、INSPECTOR、ASSET_DRAWER`）。
 *
 * @param plan 装配计划
 * @returns 说明文本
 */
export function describeAssembly(plan: AssemblySlot[]): string {
  const live = assembledSlots(plan);
  const skipped = plan.filter((s) => s.target === 'SKIP');
  const head = `装配 ${live.length} / ${plan.length} 个槽位：${live.map((s) => s.code).join('、')}`;
  return skipped.length ? `${head}；未装配：${skipped.map((s) => s.code).join('、')}` : head;
}
