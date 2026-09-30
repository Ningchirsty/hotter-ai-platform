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
export type AssemblyKind =
  /** 已是独立组件，工作台能按名字自己解析出来（{@link WORKSPACE_COMPONENTS}） */
  | 'COMPONENT'
  /** 已是独立组件，但它要宿主页面的状态，由页面以**同名插槽**提供（R37，如项目页的六个区块） */
  | 'SLOT'
  /** 还是页面里的一段（没拆成组件） */
  | 'SECTION'
  /** 代码里还没有对应实现 */
  | 'MISSING';

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
  // R37：项目页的三个步骤（资料 / 事实 / 出图）在 R32/R33 真拆出来了，配置也改成它们的**真名**。
  // 它们标 SLOT 而不是 COMPONENT：这些区块要页面状态（附件、事实、候选…），
  // 由项目页以**同名插槽**提供内容，工作台只决定"当前该显示哪一步的哪几块"。
  ProjectAssetsBlock: {
    kind: 'SLOT',
    location: 'project/components/ProjectAssetsBlock.vue（R32 拆出；项目页以同名插槽提供）',
    note: 'INPUT 步：参考图 / 产品图（上传、绑定产品图）；入参是项目页的附件状态'
  },
  ProjectBriefBlock: {
    kind: 'SLOT',
    location: 'project/components/ProjectBriefBlock.vue（R32 拆出；项目页以同名插槽提供）',
    note: 'INPUT 步：品牌要求（内容协同录入并确认，本页只读 + 申请修改）'
  },
  ProjectFactsBlock: {
    kind: 'SLOT',
    location: 'project/components/ProjectFactsBlock.vue（R32 拆出；项目页以同名插槽提供）',
    note: 'FACT 步：事实确认（筛选/排序口径留在页面一处）'
  },
  ProjectCopyBlock: {
    kind: 'SLOT',
    location: 'project/components/ProjectCopyBlock.vue（R32 拆出；项目页以同名插槽提供）',
    note: 'FACT 步：文案与要点（详情页的「字」）'
  },
  ProjectHeroBlock: {
    kind: 'SLOT',
    location: 'project/components/ProjectHeroBlock.vue（R33 拆出；项目页以同名插槽提供）',
    note: 'GENERATION 步：发起出图（工作流 / 提示词 / DNA 预填）'
  },
  ProjectGenerationsBlock: {
    kind: 'SLOT',
    location: 'project/components/ProjectGenerationsBlock.vue（R33 拆出；项目页以同名插槽提供）',
    note: 'GENERATION 步（项目页视图）：候选列表（预览 / 重试）'
  },
  GenerationBoard: {
    kind: 'SLOT',
    location: 'production/components/GenerationBoard.vue（R41 拆出；生产页以同名插槽提供）',
    note: 'GENERATION 步（生产页视图）：逐屏候选管理（质检/产品基准/规则体检、选定、重出、对比产品图）'
  },
  VisualDnaPanel: {
    kind: 'SLOT',
    location: 'dna/components/VisualDnaPanel.vue（R38 拆出；基因页以同名插槽提供）',
    note: 'DNA 步：概览 / 规范编辑 / 派生提示词 / 证据链 / 版本历史；编辑态表单与"按参考图推荐"在组件里，落库仍由页面负责'
  },
  DirectionBoard: {
    kind: 'SLOT',
    location: 'storyboard/components/DirectionBoard.vue（R39 拆出；分镜页以同名插槽提供）',
    note: 'DIRECTION 步：A/B/C 三套方向（选定、编辑文案入口）；编辑弹窗留在页面'
  },
  StoryboardBoard: {
    kind: 'SLOT',
    location: 'storyboard/components/StoryboardBoard.vue（R39 拆出；分镜页以同名插槽提供）',
    note: 'STORYBOARD 步：逐屏规格卡片（屏号/类型/文案/画面独白/视觉规格）+ 生成与锁定；编辑单屏弹窗留在页面'
  },
  GatePanel: {
    kind: 'SLOT',
    location: 'review/components/GatePanel.vue（R40 拆出；评审页以同名插槽提供）',
    note: 'GATE 步：门禁状态 / 准入项 / 人工确认（审核意见是这一步自己的输入，随事件交回页面）'
  },
  QaPanel: {
    kind: 'COMPONENT',
    location: 'components/CreativeQaPanel.vue（R31 真做）+ composables/qaVerdicts.ts（口径，纯函数有单测）',
    note: '质检与交付：四条证据线并排（参考图基准 / 产品基准 / 规则体检 / 交付产物），只读；口径集中在 qaVerdicts'
  },
  LongPageCanvas: {
    kind: 'SLOT',
    location: 'review/components/LongPageCanvas.vue（R40 拆出；评审页以同名插槽提供）',
    note: 'LAYOUT 步：渲染机排版 + 版本表（预览 / 逐版本通过打回）；长图预览弹窗留在页面（blob URL 生命周期）'
  },
  FinalReviewPanel: {
    kind: 'SLOT',
    location: 'review/components/FinalReviewPanel.vue（R40 拆出；评审页以同名插槽提供）',
    note: 'FINAL 步：交付最终版（V1.0）上传 + 交付产物（Renderer Hub：渲染器能力 / 历史交付包 / 下载）'
  }
};

/**
 * 一个步骤组件在配置里的声明。
 *
 * <p>R41 起支持**页面限定**：同一个步骤在不同页面上可能是**不同的视图**——
 * 例如「出图」步在项目页是"发起出图 + 候选"两块，在生产页是"逐屏候选管理"。
 * 不写 `pages` 表示"任何提供该插槽的页面都算"（绝大多数组件都是这样）。</p>
 */
export interface StepComponentRef {
  /** 组件名（= 页面提供的插槽名） */
  name: string;
  /** 只在哪些页面（路由 path）上生效；空数组表示不限页面 */
  pages: string[];
}

/** 对照结果的一行 */
export interface AssemblyRow {
  /** 步骤编码或面板编码 */
  code: string;
  /** 配置里声明的组件名（一行的那个）；步骤没给组件时为空串 */
  component: string;
  /** 配置里声明的全部组件名（一步多组件时不止一个） */
  components: string[];
  kind: AssemblyKind;
  location: string;
  note: string;
}

/** 工作台装配定义（解析 `layout_json` 的结果） */
export interface WorkspaceLayout {
  /** 工作台类型（如 LONG_PAGE） */
  workspace: string;
  /** 面板编码 */
  panels: string[];
  /**
   * 步骤 → 组件（R37 起允许**一步多个组件**：项目页「资料」步就是"附件 + 品牌要求"两块）。
   *
   * <p>旧写法 `{"code":"X","component":"Y"}` 仍然解析（归一成 `components:["Y"]`），
   * 这样配置改一半、或者别的工作台还没改过来时，装配不会突然空掉。</p>
   */
  steps: Array<{ code: string; components: StepComponentRef[] }>;
}

/** 把一个组件声明归一成 {@link StepComponentRef}（字符串 / 对象两种写法都认） */
function normalizeComponentRef(raw: unknown): StepComponentRef | null {
  if (typeof raw === 'string') {
    const name = raw.trim();
    return name ? { name, pages: [] } : null;
  }
  if (raw && typeof raw === 'object') {
    const row = raw as { component?: unknown; pages?: unknown };
    const name = typeof row.component === 'string' ? row.component.trim() : '';
    if (!name) {
      return null;
    }
    const pages = Array.isArray(row.pages)
      ? row.pages.filter((p): p is string => typeof p === 'string' && p.trim() !== '').map((p) => p.trim())
      : [];
    return { name, pages };
  }
  return null;
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
            const row = s as { code?: unknown; component?: unknown; components?: unknown };
            // 新写法优先；没有就退回旧的单个 component（归一成数组），三种写法都认
            const rawList = Array.isArray(row.components)
              ? row.components
              : typeof row.component === 'string' && row.component.trim() !== ''
                ? [row.component]
                : [];
            const components = rawList
              .map(normalizeComponentRef)
              .filter((c): c is StepComponentRef => c !== null);
            return { code: String(row.code ?? ''), components };
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

/** 装配对照结果 */
export interface AssemblyDiff {
  /** 工作台类型；解析不出来为空串 */
  workspace: string;
  /** 十步的组件对照 */
  stepRows: AssemblyRow[];
  /** 面板对照 */
  panelRows: AssemblyRow[];
  /** 已是独立组件、工作台能自己解析的数量 */
  componentCount: number;
  /** 已是独立组件、由宿主页面以同名插槽提供的数量（R37） */
  slotCount: number;
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
  /**
   * 已就绪 / 总数，形如 `11/18`。
   *
   * <p>口径（R37 起）：**就绪 = 已是独立组件**（COMPONENT + SLOT——两者都有真组件，
   * 区别只在"谁来装配它"）；分母是**组件行数**（一步多组件就多算几行），
   * 因为要装配的是组件，不是步骤。</p>
   */
  readyText: string;
  /** 一句话结论（界面 hover 提示与单测都断言它） */
  verdict: string;
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
  return {
    code,
    component,
    components: component ? [component] : [],
    kind: entry.kind,
    location: entry.location,
    note: entry.note
  };
}

/**
 * 把「步骤 → 组件数组」摊平成对照行（一步多组件就多几行，共享同一个步骤编码）。
 *
 * @param steps    配置里的步骤
 * @param registry 代码侧注册表
 * @returns 对照行；没给组件的步骤给一行空组件（这样"声明了但没给组件"是可见的）
 */
function stepRowsOf(
  steps: WorkspaceLayout['steps'],
  registry: Record<string, RegistryEntry>
): AssemblyRow[] {
  const out: AssemblyRow[] = [];
  for (const step of steps) {
    if (!step.components.length) {
      out.push(rowOf(step.code, '', registry));
      continue;
    }
    for (const component of step.components) {
      out.push(rowOf(step.code, component.name, registry));
    }
  }
  return out;
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
  const stepRows = stepRowsOf(layout.steps, registry);
  const rows = [...panelRows, ...stepRows];

  const componentCount = rows.filter((r) => r.kind === 'COMPONENT').length;
  const slotCount = rows.filter((r) => r.kind === 'SLOT').length;
  const sectionCount = rows.filter((r) => r.kind === 'SECTION').length;
  const missingCount = rows.filter((r) => r.kind === 'MISSING').length;

  const stepsWithoutComponent = layout.steps.filter((s) => !s.components.length).map((s) => s.code);
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
    slotCount,
    sectionCount,
    missingCount,
    stepsWithoutComponent,
    unknownNames,
    unusedInConfig,
    schemaCodeWarning,
    readyText: rows.length ? `${componentCount + slotCount}/${rows.length}` : '',
    verdict: buildAssemblyVerdict(layout, rows.length, componentCount, slotCount, sectionCount,
      missingCount, unknownNames, stepsWithoutComponent, schemaCodeWarning)
  };
}

/**
 * 组装结论句（**只用算出来的事实拼**：改配置或改注册表，结论自己会变）。
 *
 * @param layout                装配定义
 * @param total                 对照总条数（组件行数）
 * @param componentCount        工作台能自行解析的组件条数
 * @param slotCount             由宿主页面以同名插槽提供的组件条数
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
  slotCount: number,
  sectionCount: number,
  missingCount: number,
  unknownNames: string[],
  stepsWithoutComponent: string[],
  schemaCodeWarning: string
): string {
  const stepCount = layout.steps.length;
  const componentTotal = layout.steps.reduce((sum, s) => sum + Math.max(1, s.components.length), 0);
  const parts: string[] = [
    `工作台 ${layout.workspace || '(未声明)'}：配置声明 ${layout.panels.length} 个面板 + ${stepCount} 个步骤`
      + `（共 ${componentTotal} 个步骤组件）`
  ];
  parts.push(`已是独立组件 ${componentCount + slotCount} 个`
    + `（工作台自行解析 ${componentCount} 个、宿主页面插槽提供 ${slotCount} 个）、`
    + `页面内区块 ${sectionCount} 个、还没实现 ${missingCount} 个（共对照 ${total} 项）`);
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
  SLOT: '宿主插槽组件',
  SECTION: '页面内区块',
  MISSING: '未实现'
};

// ---------------------------------------------------------------------------
// R37：按步骤装配——"这一步该显示哪几块"
// ---------------------------------------------------------------------------

/**
 * 这一步在**某个页面**上应当有哪些组件（R41：支持页面限定）。
 *
 * <p>为什么要页面限定：同一个步骤在不同页面上的呈现不同——「出图」步在项目页是
 * "发起出图 + 候选"两块，在生产页是"逐屏候选管理"；这是产品事实，配置必须能表达，
 * 否则要么页面显示不出自己那一块，要么每页都收到"配置与插槽对不上"的假警报。</p>
 *
 * @param step 配置里的步骤
 * @param path 当前页面路由（如 `/creative/production`；空串表示不筛）
 * @returns 该页面上应当出现的组件（不限页面的组件在所有页面都算）
 */
export function stepComponentsForPage(
  step: { code: string; components: StepComponentRef[] },
  path?: string | null
): StepComponentRef[] {
  const here = (path || '').trim();
  if (!here) {
    return step.components;
  }
  return step.components.filter((c) => !c.pages.length || c.pages.includes(here));
}

/**
 * 一个步骤在本页面上**能显示出来的**组件（配置声明 + 宿主页面提供了同名插槽）。
 *
 * @param steps      配置里的步骤（按配置顺序）
 * @param components 宿主页面实际提供了插槽的组件名
 * @param path       当前页面路由（用于页面限定；可空 = 不筛页面）
 * @returns 能显示的步骤（含它能显示的组件；一步多组件时按配置顺序，缺的那个如实少一个）
 */
export function hostedSteps(
  steps: WorkspaceLayout['steps'] | null | undefined,
  components: string[],
  path?: string | null
): Array<{ code: string; components: string[] }> {
  const provided = new Set(components.filter(Boolean));
  return (steps || [])
    .map((step) => ({
      code: step.code,
      components: stepComponentsForPage(step, path)
        .map((c) => c.name)
        .filter((name) => provided.has(name))
    }))
    .filter((step) => step.components.length > 0);
}

/**
 * 这一步显示哪个步骤（R37 的唯一判据，纯函数）。
 *
 * <p>规则（与指引线同一套口径，只是范围收窄到"本页面托管的步骤"）：</p>
 * <ol>
 *   <li>全局当前步如果在托管列表里 → 就用它（用户点指引线跳过来的那一步）；</li>
 *   <li>否则取第一个「进行中」的；</li>
 *   <li>否则取第一个**还没了结**的（未开始 / 前置未完成）；</li>
 *   <li>否则取最后一个（这一步之后本页没有别的活了——显示最后一步比显示第一步更接近"我做到哪了"）。</li>
 * </ol>
 *
 * <p>为什么不取"第一个"：<b>本页的早期步骤做完之后，第一个往往是已经做完的那一步</b>
 * （例如资料与事实都完成了，本页当前该看的是事实步的结果，而不是又回到资料步）。
 * 全页都了结时才回到最后一步，是为了让"改完最后一件事"仍能看见自己刚做的事。</p>
 *
 * @param hosted     本页面托管的步骤（含状态；按配置顺序）
 * @param activeCode 全局当前步编码（指引线给的；可空）
 * @returns 该显示的步骤编码；托管列表为空时返回 null
 */
export function pickVisibleStep(
  hosted: Array<{ code: string; status?: string | null }>,
  activeCode?: string | null
): string | null {
  if (!hosted.length) {
    return null;
  }
  const active = (activeCode || '').trim();
  if (active) {
    const hit = hosted.find((s) => s.code === active);
    if (hit) {
      return hit.code;
    }
  }
  const normalized = hosted.map((s) => ({ code: s.code, status: (s.status || '').toLowerCase() }));
  const doing = normalized.find((s) => s.status === 'doing');
  if (doing) {
    return doing.code;
  }
  const open = normalized.find((s) => s.status !== 'done' && s.status !== 'skipped');
  if (open) {
    return open.code;
  }
  return normalized[normalized.length - 1].code;
}

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
    // 兜底计划**必须包含项目头部**（R31）：
    // 头部里放着刷新/操作日志/清理素材这些页面动作，配置读不到（或还没读到）时把它一起省掉，
    // 页面就变成"只剩内容、连刷新都没有"——这正是"配置层挂了页面也不该变空"要防的事。
    // 首载竞态在 R31 真机验收里出现过一次（同一项目第二次打开就正常），所以这条兜底不是理论问题。
    return [
      { code: 'PROJECT_HEADER', kind: 'COMPONENT', target: 'COMPONENT', reason: '配置读不到，仍渲染项目头部（页面动作都在这里）' },
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
    // R38：宿主插槽组件（SLOT）**不能**作为面板由工作台自己解析——它要宿主页面的状态，
    // 只能由页面以同名插槽提供；配到面板清单里是配置写错了，得说清楚而不是含糊成"没实现"。
    if (row.kind === 'SLOT') {
      return {
        code: row.code,
        kind: row.kind,
        target: 'SKIP' as AssemblyTarget,
        reason: '宿主插槽组件：要页面状态，只能由页面以同名插槽提供，不能作为面板由工作台解析'
      };
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
