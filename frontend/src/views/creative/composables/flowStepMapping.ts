import { STEP_META, STAGE_ORDER } from './creativeFlowSteps';
import type { FlowStatus } from './creativeFlowSteps';
import type { ProjectStepState, ScenarioStep } from '@/api/creative/scenario';

/**
 * 「配置步骤（`dp_scenario_step`）↔ 代码八步」的**只读对照**（V0.2 D 阶段第二刀，第一步）。
 *
 * <p><b>为什么先做对照、而不是直接把指引线切到配置驱动</b>：配置里是 10 步（INPUT/FACT/DNA/
 * DIRECTION/STORYBOARD/GATE/GENERATION/QA/LAYOUT/FINAL），代码里是 8 步，两者**不是一一对应**：
 * <ul>
 *   <li>代码第 1 步「项目资料与品牌要求」在配置里被拆成 INPUT + FACT（一条配置 → 两步合一）；</li>
 *   <li>代码第 8 步「详情页排版与终审」在配置里被拆成 LAYOUT + FINAL（两条配置 → 一步合一）；</li>
 *   <li>配置里的 QA（质检）在代码八步里**没有独立一步**（代码把质检并进第 7 步「出图」）。</li>
 * </ul>
 * 直接把指引线换成 10 步会立刻改变"当前在第几步、点了跳到哪一页"的判定，而每个步骤的
 * 明细判据（`useCreativeFlow#ensureStep`）目前是按 1~8 的步号写死的。所以这一步只做**对照**：
 * 把差异如实算出来展示给人看，指引线一行不动（点错了不会有事，但看错了会指错路）。</p>
 *
 * <p><b>纯函数</b>：不 import 任何运行时依赖（`@/api/creative/scenario` 只 import type，编译后擦除），
 * 因此能被 vitest 在 node 环境直接断言——与 `scenarioText.ts`、`creativeFlowSteps.ts` 同一做法。</p>
 *
 * @author creative
 */

/**
 * 配置步骤编码 → 代码八步的 key。
 *
 * <p>这张表是**人的判断**，不是推导出来的：改它等于改"配置里的哪一步对应界面上的哪一步"，
 * 必须有意识地改，并且单测会跟着红。刻意不给 QA 映射——代码八步里没有它。</p>
 */
export const STEP_CODE_TO_CODE_KEY: Record<string, string> = {
  INPUT: 'material',
  FACT: 'fact',
  DNA: 'dna',
  DIRECTION: 'direction',
  STORYBOARD: 'storyboard',
  GATE: 'gate',
  GENERATION: 'production',
  LAYOUT: 'layout',
  FINAL: 'layout'
};

/** 对照表里的一行 */
export interface MappingRow {
  stepCode: string;
  stepName: string;
  sortNo: number;
  /** 对映到的代码步号（1~8）；没有对应为 null */
  codeNo: number | null;
  /** 对映到的代码步骤名（没有对应为 null） */
  codeName: string | null;
  /** 该配置步骤覆盖的阶段（原样，便于核对） */
  stageCodes: string;
  /** 覆盖了但代码不认识的阶段码（配置写错时在这里暴露） */
  unknownStages: string[];
}

/** 对照结果 */
export interface FlowStepMapping {
  /** 逐条对照（按 sortNo） */
  rows: MappingRow[];
  /** 配置里有、代码八步里没有对应的（预期：QA） */
  unmapped: MappingRow[];
  /** 代码八步里没有任何配置步骤覆盖的步号 */
  uncoveredCodeNos: number[];
  /** 多条配置步骤覆盖同一个代码步（预期：LAYOUT/FINAL → 第 8 步） */
  fanIn: Array<{ codeNo: number; codeName: string; stepCodes: string[] }>;
  /** 18 个阶段里没有被任何配置步骤覆盖的（预期：V08_READY） */
  uncoveredStages: string[];
  /** 配置里引用了代码不认识的阶段（预期：空） */
  unknownStages: string[];
  /** 已对映条数 / 配置总条数，形如 `9/10` */
  coverageText: string;
  /** 一句话结论（界面上的 hover 提示用它，测试也断言它） */
  verdict: string;
}

/** 取代码八步的步号（按 STEP_META 顺序，key 不存在返回 null） */
function codeNoOfKey(key: string): number | null {
  const meta = STEP_META.find((m) => m.key === key);
  return meta ? meta.no : null;
}

/** 取代码八步的名字 */
function codeNameOfKey(key: string): string | null {
  const meta = STEP_META.find((m) => m.key === key);
  return meta ? meta.name : null;
}

/** 拆 stage_codes（逗号分隔）并去掉空项 */
function splitStages(stageCodes?: string | null): string[] {
  return (stageCodes || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

/**
 * 把配置步骤与代码八步做逐条对照。
 *
 * @param steps 配置步骤（`GET /creative/v2/scenarios/{type}/steps` 的返回）
 * @returns 对照结果；入参为空时所有数组为空、coverageText 为空串（调用方据此不显示）
 */
export function mapFlowSteps(steps?: ScenarioStep[] | null): FlowStepMapping {
  const rows: MappingRow[] = (steps || [])
    .filter(Boolean)
    .toSorted((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
    .map((step) => {
      const stepCode = step.stepCode || '';
      const stages = splitStages(step.stageCodes);
      const unknown = stages.filter((s) => !STAGE_ORDER.includes(s));
      const key = STEP_CODE_TO_CODE_KEY[stepCode];
      const codeNo = key ? codeNoOfKey(key) : null;
      return {
        stepCode,
        stepName: step.stepName || stepCode,
        sortNo: step.sortNo ?? 0,
        codeNo,
        codeName: key ? codeNameOfKey(key) : null,
        stageCodes: step.stageCodes || '',
        unknownStages: unknown
      };
    });

  const unmapped = rows.filter((r) => r.codeNo == null);

  // 代码八步里没有任何配置步骤覆盖的
  const coveredNos = new Set(rows.map((r) => r.codeNo).filter((n): n is number => n != null));
  const uncoveredCodeNos = STEP_META.map((m) => m.no).filter((no) => !coveredNos.has(no));

  // 多条配置 → 同一个代码步
  const fanIn: FlowStepMapping['fanIn'] = [];
  for (const meta of STEP_META) {
    const hit = rows.filter((r) => r.codeNo === meta.no);
    if (hit.length > 1) {
      fanIn.push({ codeNo: meta.no, codeName: meta.name, stepCodes: hit.map((r) => r.stepCode) });
    }
  }

  // 阶段覆盖：哪些阶段没有被任何配置步骤覆盖
  const coveredStages = new Set(rows.flatMap((r) => splitStages(r.stageCodes)));
  const uncoveredStages = STAGE_ORDER.filter((s) => !coveredStages.has(s));
  const unknownStages = Array.from(new Set(rows.flatMap((r) => r.unknownStages)));

  const covered = rows.length - unmapped.length;
  return {
    rows,
    unmapped,
    uncoveredCodeNos,
    fanIn,
    uncoveredStages,
    unknownStages,
    coverageText: rows.length ? `${covered}/${rows.length}` : '',
    verdict: buildVerdict(rows, unmapped, uncoveredCodeNos, fanIn, uncoveredStages, unknownStages)
  };
}

/**
 * 组装一句话结论（**只用算出来的事实拼**，不写死"应该是几比几"——配置改了结论自己会变）。
 *
 * @param rows               逐条对照
 * @param unmapped           无对应的配置步骤
 * @param uncoveredCodeNos   无配置对应的代码步号
 * @param fanIn              多条配置覆盖同一步
 * @param uncoveredStages    无配置覆盖的阶段
 * @param unknownStages      代码不认识的阶段码
 * @returns 结论文本；没有配置步骤时返回空串
 */
function buildVerdict(
  rows: MappingRow[],
  unmapped: MappingRow[],
  uncoveredCodeNos: number[],
  fanIn: FlowStepMapping['fanIn'],
  uncoveredStages: string[],
  unknownStages: string[]
): string {
  if (rows.length === 0) {
    return '';
  }
  const parts: string[] = [`配置 ${rows.length} 步 ↔ 代码 ${STEP_META.length} 步`];
  parts.push(
    unmapped.length
      ? `${rows.length - unmapped.length} 条已对映，${unmapped.length} 条代码八步里没有对应（${unmapped
          .map((r) => r.stepCode || '未命名')
          .join('、')}）`
      : '全部有对应'
  );
  for (const item of fanIn) {
    parts.push(`代码第 ${item.codeNo} 步由 ${item.stepCodes.join('、')} 两步覆盖`);
  }
  if (uncoveredCodeNos.length) {
    parts.push(`代码第 ${uncoveredCodeNos.join('、')} 步没有配置步骤对应`);
  }
  if (uncoveredStages.length) {
    parts.push(`${uncoveredStages.length} 个阶段未被任何配置步骤覆盖（${uncoveredStages.join('、')}）`);
  }
  if (unknownStages.length) {
    parts.push(`配置里引用了代码不认识的阶段：${unknownStages.join('、')}`);
  }
  return parts.join('；');
}

/**
 * 配置驱动模式下的状态文案。
 *
 * <p>只改一处：`blocked` 从「被阻塞」改成「前置未完成」。回落模式（代码八步）里的"被阻塞"意思是
 * <b>后端会直接拒绝该动作</b>（例如视觉门未过就出图）；而配置驱动时阻塞来自 `entry_condition_json`
 * 的声明（例如 DNA 声明 `requireFact`，但后端其实允许在零事实下先出基因）——
 * 说"被阻塞"会让人以为点了会被拒，说"前置未完成"才是准确的。</p>
 */
export const CONFIG_STATUS_LABELS: Record<FlowStatus, string> = {
  done: '已完成',
  doing: '进行中',
  todo: '未开始',
  blocked: '前置未完成'
};

/**
 * 界面上那枚小胶囊的文字（形如 `步序对照 9/10`）。
 *
 * @param mapping 对照结果
 * @returns 胶囊文本；没有对照数据返回空串
 */
export function formatMappingChip(mapping: FlowStepMapping): string {
  if (!mapping.rows.length || !mapping.coverageText) {
    return '';
  }
  return `步序对照 ${mapping.coverageText}`;
}

// ---------------------------------------------------------------------------
// R16：把指引线切成配置驱动 —— 步骤 → 页面、进入条件 → 前置步骤、组装计划
// ---------------------------------------------------------------------------

/**
 * 配置步骤 → 归属页面（点击跳转用）。
 *
 * <p>这是**人的判断**：配置里只说"这一步做什么"，页面归属是前端装配的事。
 * 三条口径（R16 已确认）：① 十步各占一格；② QA 不新增页面（质检在出图页按候选执行）；
 * ③ 排版与终审都在评审页。</p>
 */
export const STEP_CODE_TO_PAGE: Record<string, string> = {
  INPUT: '/creative/project',
  FACT: '/creative/project',
  DNA: '/creative/dna',
  DIRECTION: '/creative/storyboard',
  STORYBOARD: '/creative/storyboard',
  GATE: '/creative/review',
  GENERATION: '/creative/production',
  // QA 不新增页面：质检是出图页里对候选逐张执行的动作（自动质检只筛除、不放行）
  QA: '/creative/production',
  LAYOUT: '/creative/review',
  FINAL: '/creative/review'
};

/**
 * 配置里的进入条件键 → 它要求的**前置配置步骤**编码。
 *
 * <p>前端不再自己写"第 4 步要求第 3 步"这类规则，而是把配置 `entry_condition_json` 里的
 * `requireXxx` 翻成步骤编码。配置改了条件，界面上的"被阻塞"跟着变——
 * 这就是"配置驱动"该有的样子；反过来，出现不认识的键会**如实报出来**（见 `unknownConditions`）。</p>
 */
export const REQUIRE_TO_STEP: Record<string, string> = {
  requireInput: 'INPUT',
  requireFact: 'FACT',
  requireDna: 'DNA',
  requireDirection: 'DIRECTION',
  requireStoryboard: 'STORYBOARD',
  requireGate: 'GATE',
  requireGeneration: 'GENERATION',
  requireLayout: 'LAYOUT'
};

/** 指引线上的一步（配置驱动；`source=CODE` 表示这是配置读不到时的回落） */
export interface GuideStepPlan {
  /** 显示序号（1 起；R16 起就是配置顺序） */
  no: number;
  /** 步骤编码（配置驱动时是 stepCode；回落时是代码八步的 key） */
  key: string;
  name: string;
  /** 归属页面 */
  page: string;
  status: FlowStatus;
  /** 后端给的步骤状态（DONE/ACTIVE/PENDING）；回落模式为 null */
  backendStatus: string | null;
  /** 配置里 required='1' */
  required: boolean;
  /** 未满足的前置步骤名（用于解释"被阻塞"） */
  waiting: string[];
  /** 配置里出现但前端不认识的进入条件键（如实暴露，不静默忽略） */
  unknownConditions: string[];
  source: 'CONFIG' | 'CODE';
}

/** 解析 entry_condition_json：取值为 true 的键 */
function parseConditionKeys(json?: string | null): string[] {
  if (!json) {
    return [];
  }
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    if (!parsed || typeof parsed !== 'object') {
      return [];
    }
    return Object.entries(parsed)
      .filter(([, value]) => value === true)
      .map(([key]) => key);
  } catch (e) {
    // 配置里写了不合法的 JSON：不猜，交给 unknownConditions 之外的"缺失原因"去解释
    return [];
  }
}

/**
 * 按配置与后端步骤状态组装指引线（不读任何运行时依赖，可被 vitest 直接断言）。
 *
 * <p>状态口径（R16 已确认：当前步/完成态以后端为准，前端不再按阶段顺序自算）：</p>
 * <ul>
 *   <li>后端 `DONE` → 已完成；后端 `ACTIVE` → 进行中；</li>
 *   <li>后端 `PENDING`（或没有该步的状态行）→ 前置满足则待办，不满足则**被阻塞**；</li>
 *   <li>进行中的步骤不会被判成"被阻塞"（它已经开始了，配置里的前置显然满足）；</li>
 *   <li>命中"进行中"的第一步记为 `current`，供界面只给它一个脉冲（避免两步一起闪）。</li>
 * </ul>
 *
 * @param steps  配置步骤（`GET /creative/v2/scenarios/{type}/steps`）
 * @param states 项目步骤状态（`GET /creative/v2/projects/{taskId}/steps`）
 * @returns 指引线计划；配置为空时返回空数组（调用方据此回落到代码八步）
 */
export function buildGuideSteps(
  steps?: ScenarioStep[] | null,
  states?: ProjectStepState[] | null
): GuideStepPlan[] {
  const rows = (steps || []).filter(Boolean).toSorted((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0));
  if (!rows.length) {
    return [];
  }
  const stateByCode = new Map<string, ProjectStepState>();
  for (const state of states || []) {
    if (state?.stepCode) {
      stateByCode.set(state.stepCode, state);
    }
  }
  const doneCodes = new Set(
    (states || []).filter((s) => (s?.status || '').toUpperCase() === 'DONE').map((s) => s?.stepCode || '')
  );
  const nameByCode = new Map(rows.map((r) => [r.stepCode || '', r.stepName || r.stepCode || '']));

  return rows.map((row, index) => {
    const stepCode = row.stepCode || '';
    const backendStatus = (stateByCode.get(stepCode)?.status || '').toUpperCase() || null;
    const conditionKeys = parseConditionKeys(row.entryConditionJson);
    const unknownConditions = conditionKeys.filter(
      (key) => key !== 'requireProject' && !REQUIRE_TO_STEP[key]
    );
    // requireProject 的意思是"得先有项目"——进到这里必然成立（没有项目就没有指引线）
    const waiting = conditionKeys
      .filter((key) => key !== 'requireProject')
      .map((key) => REQUIRE_TO_STEP[key])
      // 自依赖（配置里写 require 自己）不算阻塞，否则永远红着
      .filter((code): code is string => Boolean(code) && code !== stepCode)
      .filter((code) => !doneCodes.has(code))
      .map((code) => nameByCode.get(code) || code);

    let status: FlowStatus;
    if (backendStatus === 'DONE') {
      status = 'done';
    } else if (backendStatus === 'ACTIVE') {
      status = 'doing';
    } else {
      status = waiting.length ? 'blocked' : 'todo';
    }

    return {
      no: index + 1,
      key: stepCode,
      name: row.stepName || stepCode,
      page: STEP_CODE_TO_PAGE[stepCode] || '/creative/project',
      status,
      backendStatus,
      required: (row.required || '1') !== '0',
      waiting,
      unknownConditions,
      source: 'CONFIG' as const
    };
  });
}

/**
 * 配置读不到时的回落计划：代码八步（顺序、名称、页面都取自 `STEP_META`）。
 *
 * <p>状态留给调用方按既有口径（阶段顺序 + 本地完成判据）填——回落路径要与 R13 之前**完全一致**，
 * 这样"配置层挂了"就只是少了配置信息，而不是把指引线一起弄坏。</p>
 *
 * @returns 八步计划（`source=CODE`，状态一律 todo，由调用方覆写）
 */
export function buildFallbackSteps(): GuideStepPlan[] {
  return STEP_META.map((meta) => ({
    no: meta.no,
    key: meta.key,
    name: meta.name,
    page: meta.page,
    status: 'todo' as FlowStatus,
    backendStatus: null,
    required: true,
    waiting: [],
    unknownConditions: [],
    source: 'CODE' as const
  }));
}
