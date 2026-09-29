/**
 * 视觉工厂「流程指引线」状态（唯一真相处）。
 *
 * <p><b>为什么要有它</b>：R4 曾用一个独立向导页承载八步流程，结果是「做完一步要退回菜单再进下一页」，
 * 而且在 6 个页面之间来回跳。R5 改成「每个环节页面顶部一条指引线」，于是同一套判定必须被 5 个页面共用——
 * 一旦逐页复制，就会出现「两个口径」（本工程一直在避免的漂移）。</p>
 *
 * <p><b>R16：步骤与状态改由配置驱动</b>（V0.2 D3）。三条口径（已确认）：</p>
 * <ul>
 *   <li><b>步骤来自配置</b>：`dp_scenario_step` 的十步各占一格（`INPUT/FACT/DNA/DIRECTION/STORYBOARD/
 *       GATE/GENERATION/QA/LAYOUT/FINAL`），名称与顺序都取配置；QA 不新增页面（复用出图页）。</li>
 *   <li><b>状态来自后端</b>：`GET /creative/v2/projects/{taskId}/steps`（R14/D2 的派生投影：
 *       `DONE/ACTIVE/PENDING`）。前端不再按阶段顺序自己算"做到第几步"——那是第二个真相源。</li>
 *   <li><b>前置来自配置</b>：`entry_condition_json` 里的 `requireXxx` 决定某步是否「被阻塞」。</li>
 * </ul>
 *
 * <p><b>明细判据按 `step_code` 注册</b>（{@link STEP_DETAIL_CHECKERS}）：原先是一段 `switch (no)` 的
 * 1~8 分支，步骤变成配置里的十步后分支号已无意义。现在是一张"步骤编码 → 判据函数"的表，
 * 未注册的编码会**如实说明"还没有注册判据"**，而不是悄悄给一个空结论。</p>
 *
 * <p><b>两段式取数</b>：主线（项目+附件+事实+配置步骤+步骤状态，始终加载）+
 * 细化（悬停/点开某一步才取该步明细，回答「为什么还不能进入下一步」）。</p>
 *
 * <p><b>权威在后端</b>：阶段描述的权威是 {@code DpVisualStageEnum}，步骤状态的权威是
 * `dp_project_step_state`（由 `moveStage` 写）。本文件只做展示与映射，不在前端另立标准。</p>
 *
 * @author creative
 */
import { computed, reactive, ref, watch, type Ref } from 'vue';
import {
  getCreativeProject,
  getDetailPage,
  getDna,
  getStoryboard,
  getVisualGate,
  listCreativeFiles,
  listDirections,
  listGenerations
} from '@/api/creative';
import { listProjectSteps } from '@/api/creative/scenario';
import type { ProjectStepState } from '@/api/creative/scenario';
import { CREATIVE_STAGE_LABELS } from '@/api/creative/types';
import type {
  CreativeProjectVO,
  DpDetailPageVO,
  DpGenerationVO,
  DpStoryboardVO,
  DpVisualDirectionVO,
  DpVisualDnaVO,
  GateEvaluationVO
} from '@/api/creative/types';
import { factFieldOptions, listFact } from '@/api/content/fact';
import type { CpFactFieldOptionVO, CpFactSnapshotVO } from '@/api/content/fact/types';
import type { CpTaskFileVO } from '@/api/content/task/types';
import {
  FLOW_STATUS_LABELS,
  RUNNING_STAGES,
  STEP_META,
  resolveActiveNo,
  stageReached,
  type FlowStatus,
  type FlowStep
} from './creativeFlowSteps';
import {
  buildFallbackSteps,
  buildGuideSteps,
  CONFIG_STATUS_LABELS,
  type GuideStepPlan
} from './flowStepMapping';
import { useScenarioConfig } from './useScenarioConfig';

// 纯逻辑（阶段映射 / 当前步 / 步序对照 / 配置驱动的计划）全部在纯函数模块里，可被 node 环境单测直接钉住
export {
  FLOW_STATUS_LABELS,
  STEP_META,
  resolveActiveNo,
  stageReached,
  stageStepOf,
  STAGE_ORDER
} from './creativeFlowSteps';
export type { FlowStatus, FlowStep } from './creativeFlowSteps';

/**
 * 单步明细（懒加载结果）。
 */
interface StepDetail {
  summary: string;
  missing: string[];
  /**
   * 明细层面的"已完成"修正。
   *
   * <p>R16 起**只有回落模式**（配置参数读不到、走代码八步）才用它——
   * 配置驱动时状态一律以后端 `dp_project_step_state` 为准，避免两个真相源。</p>
   */
  done?: boolean;
}

/** 判据函数拿到的上下文（省得每个判据各取一遍同样的数据） */
interface StepDetailContext {
  taskId: string | number;
  project: CreativeProjectVO | null;
  files: CpTaskFileVO[];
  imageFiles: CpTaskFileVO[];
  facts: CpFactSnapshotVO[];
  confirmedFacts: CpFactSnapshotVO[];
  fieldOptionsLoaded: boolean;
  requiredOptions: CpFactFieldOptionVO[];
  unsatisfiedOptions: CpFactFieldOptionVO[];
  /** 某步骤是否已完成（配置驱动看后端状态，回落模式看本地判据） */
  stepDone: (stepCode: string) => boolean;
  /** 分镜 + 候选（出图与质检共用一次请求） */
  storyboardAndGenerations: () => Promise<{
    storyboard: DpStoryboardVO | null;
    generations: DpGenerationVO[];
  }>;
  /** 未加载明细时的一行摘要（按步骤编码） */
  fallbackSummary: (key: string) => string;
}

/**
 * 「为什么还不能进入下一步」的判据表：**按 step_code 注册**（R16 重构）。
 *
 * <p>键是配置步骤编码（`INPUT/FACT/DNA/DIRECTION/STORYBOARD/GATE/GENERATION/QA/LAYOUT/FINAL`），
 * 与 `dp_scenario_step.step_code` 一一对应；配置里新增一步时，这里要显式补一个判据，
 * 否则界面上会明确显示"还没有注册明细判据"——**不猜、不装作通过**。</p>
 */
const STEP_DETAIL_CHECKERS: Record<string, (ctx: StepDetailContext) => Promise<StepDetail>> = {
  /** 产品资料与参考图 */
  INPUT: async (ctx) => {
    const missing: string[] = [];
    if (!ctx.imageFiles.length) {
      missing.push('项目里还没有图片附件（产品照片 / 参考图）');
    }
    if (ctx.project && ctx.project.productId == null) {
      missing.push('项目没有关联产品，照片无法登记为产品图（产品基准质检会没有基准图）');
    }
    return { missing, summary: ctx.fallbackSummary('INPUT') };
  },

  /** 事实确认 */
  FACT: async (ctx) => {
    const missing: string[] = [];
    if (!ctx.fieldOptionsLoaded) {
      missing.push('字段选项接口没取到，无法判断闸门必填项是否齐备');
    } else {
      if (ctx.unsatisfiedOptions.length) {
        missing.push(
          `必填事实还差 ${ctx.unsatisfiedOptions.length} 项：` +
            ctx.unsatisfiedOptions.map((o) => o.fieldName || o.fieldCode).join('、')
        );
      }
      if (!ctx.requiredOptions.length && !ctx.confirmedFacts.length) {
        missing.push('该交付类型没有声明必填事实项，同时也没有任何已确认事实');
      }
    }
    return { missing, summary: ctx.fallbackSummary('FACT') };
  },

  /** 视觉基因 */
  DNA: async (ctx) => {
    const res = await getDna(ctx.taskId);
    const dna: DpVisualDnaVO | null = res?.data ?? null;
    const missing: string[] = [];
    if (!ctx.imageFiles.length) missing.push('没有参考图，「按参考图推荐」没有实测依据');
    if (!ctx.confirmedFacts.length) missing.push('没有已确认事实，基因与文案只能靠默认规范推导');
    if (dna && (dna.issues || []).length) {
      missing.push(`当前版本自洽校验未过：${(dna.issues || []).join('；')}`);
    }
    if (dna && !dna.locked && !(dna.issues || []).length) missing.push('当前版本还没有锁定');
    if (!dna) missing.push('还没有生成视觉基因');
    return {
      missing,
      summary: dna ? `${dna.dnaNo} v${dna.version} · ${dna.statusDesc}` : '还没有基因版本'
    };
  },

  /** 视觉方向 */
  DIRECTION: async (ctx) => {
    const res = await listDirections(ctx.taskId);
    const rows: DpVisualDirectionVO[] = res?.data || [];
    const selected = rows.find((d) => d.status === 'SELECTED') || null;
    const missing: string[] = [];
    const dnaDone = ctx.stepDone('DNA');
    if (!dnaDone) missing.push('视觉基因还没有锁定版本（后端会拒绝生成方向）');
    if (rows.length && !selected) missing.push('还没有在 A/B/C 中选定方向');
    if (!rows.length && dnaDone) missing.push('还没有生成方向');
    return {
      missing,
      summary: `${rows.length} 个方向${selected ? ' · 已选定 ' + selected.directionCode : ''}`
    };
  },

  /** 分镜 */
  STORYBOARD: async (ctx) => {
    const res = await getStoryboard(ctx.taskId);
    const sb: DpStoryboardVO | null = res?.data ?? null;
    const missing: string[] = [];
    const dnaDone = ctx.stepDone('DNA');
    if (!dnaDone) missing.push('视觉基因还没有锁定版本（后端会拒绝生成分镜）');
    if (!sb && dnaDone) missing.push('还没有生成分镜');
    if (sb && sb.status !== 'LOCKED') missing.push('当前分镜还是草稿，没有锁定');
    if (!ctx.stepDone('DIRECTION')) missing.push('方向未选定（不影响生成，但分镜不会带上方向的取舍）');
    const noSolo = (sb?.screens || []).filter((s) => !s.pictureSoloStatement);
    if (noSolo.length) {
      missing.push(`${noSolo.length} 屏缺少「画面独白」：${noSolo.map((s) => s.screenNo).join('、')}`);
    }
    return {
      missing,
      summary: sb
        ? `${sb.storyboardNo} v${sb.version} · ${sb.statusDesc} · ${sb.screenCount ?? 0} 屏`
        : '还没有分镜'
    };
  },

  /** 视觉门 */
  GATE: async (ctx) => {
    const res = await getVisualGate(ctx.taskId);
    const gate: GateEvaluationVO | null = res?.data ?? null;
    const missing: string[] = [];
    if (gate) {
      missing.push(...(gate.blocked || []));
      if (gate.submittable && !gate.passed) {
        if (gate.cardStatus === 'PENDING') missing.push('已提交，等待人工确认（本页可直接确认/打回）');
        else if (gate.cardStatus === 'BLOCKED') missing.push('已被打回，请修改方案后重新提交');
        else missing.push('硬性项已满足，但还没有提交审核');
      }
    } else {
      missing.push('视觉门评估还没取到');
    }
    return {
      missing,
      summary: gate ? (gate.passed ? '已通过' : `未通过（${gate.cardStatus || '未提交'}）`) : '评估未取到'
    };
  },

  /** 出图 */
  GENERATION: async (ctx) => {
    const { storyboard: sb, generations: gens } = await ctx.storyboardAndGenerations();
    const screens = sb?.screens || [];
    const selectedCount = screens.filter((s) =>
      gens.some((g) => g.screenId === s.id && g.status === 'APPROVED')
    ).length;
    const unselected = screens.filter(
      (s) => !gens.some((g) => g.screenId === s.id && g.status === 'APPROVED')
    );
    const missing: string[] = [];
    if (!sb) missing.push('还没有分镜，无法出图');
    else if (sb.status !== 'LOCKED') missing.push('分镜还没锁定（批量出图以最新分镜为准）');
    if (!ctx.stepDone('GATE')) missing.push('视觉门还没通过（后端会拒绝出图请求）');
    if (sb && !screens.length) missing.push('分镜里没有屏');
    if (unselected.length) {
      missing.push(`${unselected.length} 屏还没有选定候选：${unselected.map((s) => s.screenNo).join('、')}`);
    }
    return {
      missing,
      summary: `${gens.length} 个候选 / ${screens.length} 屏，已选定 ${selectedCount} 屏`,
      // 每屏都选定了候选就是真的做完了（阶段可能还没推）——仅回落模式会用到这个修正
      done: screens.length > 0 && unselected.length === 0
    };
  },

  /**
   * 质检（配置里 `required='0'`：可选步骤；**不新增页面**，质检在出图页按候选逐张执行）。
   *
   * <p>口径：自动质检只筛除、不放行——`INCONSISTENT` 的候选会被后端置为 `REJECTED`，
   * `UNCERTAIN`（质检跑失败）按未通过处理，这里都如实说出来，不把它当"通过"。</p>
   */
  QA: async (ctx) => {
    const { generations: gens } = await ctx.storyboardAndGenerations();
    const checked = gens.filter((g) => g.qaVerdict);
    const inconsistent = checked.filter((g) => (g.qaVerdict || '').toUpperCase() === 'INCONSISTENT');
    const uncertain = checked.filter((g) => (g.qaVerdict || '').toUpperCase() === 'UNCERTAIN');
    const pending = gens.filter((g) => !g.qaVerdict);
    const missing: string[] = [];
    if (!gens.length) {
      missing.push('还没有候选图，质检没有对象（先在出图页生成候选）');
    } else {
      if (pending.length) missing.push(`${pending.length} 张候选还没有质检结论`);
      if (inconsistent.length) {
        missing.push(`${inconsistent.length} 张候选质检不一致，已被自动筛除（需重出或人工取舍）`);
      }
      if (uncertain.length) missing.push(`${uncertain.length} 张候选未能取得结论（按未通过处理）`);
    }
    return {
      missing,
      summary: gens.length
        ? `已质检 ${checked.length} / ${gens.length} 张；不一致 ${inconsistent.length} 张`
        : '还没有候选可质检'
    };
  },

  /** 长图排版 */
  LAYOUT: async (ctx) => {
    const res = await getDetailPage(ctx.taskId);
    const page: DpDetailPageVO | null = res?.data ?? null;
    const versions = page?.versions || [];
    const missing: string[] = [];
    if (!ctx.stepDone('GATE')) missing.push('视觉门还没通过（后端会拒绝渲染）');
    if (!versions.length) missing.push('还没有渲染过机排版版本');
    return {
      missing,
      summary: page
        ? `v${page.currentVersion ?? 0} · ${page.statusDesc || '未排版'} · ${versions.length} 个版本`
        : '详情页未取到'
    };
  },

  /** 终审交付 */
  FINAL: async (ctx) => {
    const res = await getDetailPage(ctx.taskId);
    const page: DpDetailPageVO | null = res?.data ?? null;
    const versions = page?.versions || [];
    const approvedVision = versions.find((v) => v.status === 'APPROVED' && v.kind !== 'V10_FINAL');
    const finalVersion = versions.find((v) => v.kind === 'V10_FINAL');
    const missing: string[] = [];
    if (!versions.length) missing.push('还没有渲染过机排版版本');
    if (versions.length && !finalVersion && !approvedVision) {
      missing.push('已有版本但还没有终审通过，也没有上传精修最终版');
    }
    if (approvedVision && !finalVersion) {
      missing.push('机排版已终审通过，还差上传精修最终版（V1.0）');
    }
    return {
      missing,
      summary: page
        ? `v${page.currentVersion ?? 0} · ${page.statusDesc || '未排版'} · ${versions.length} 个版本`
        : '详情页未取到'
    };
  }
};

/** 配置步骤编码 → 代码八步的 key（**仅回落模式**用于把编码翻回步号） */
const FALLBACK_KEY_OF_STEP: Record<string, string> = {
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

/**
 * 流程指引线的状态与数据。
 *
 * @param taskId              当前项目ID（响应式；为空时指引线显示「未选择项目」）
 * @param deliverableTypeHint 交付类型的外部提示（页面 prop；没有就用项目数据里的）
 */
export function useCreativeFlow(
  taskId: Ref<string | number | undefined>,
  deliverableTypeHint?: Ref<string | undefined>
) {
  const loading = ref(false);
  const error = ref<string | null>(null);

  const project = ref<CreativeProjectVO | null>(null);
  const files = ref<CpTaskFileVO[]>([]);
  const facts = ref<CpFactSnapshotVO[]>([]);
  const fieldOptions = ref<CpFactFieldOptionVO[]>([]);
  const fieldOptionsLoaded = ref(false);
  /** 项目步骤状态（后端派生投影；R16 起是"做到第几步"的权威） */
  const projectSteps = ref<ProjectStepState[]>([]);
  /** 步骤状态是否取到（没取到就走代码八步回落） */
  const stepsLoaded = ref(false);

  /** 各步的明细（懒加载后按 stepCode 写入） */
  const details = reactive<Record<string, StepDetail>>({});

  const imageFiles = computed(() => files.value.filter((f) => (f.fileKind || '').toUpperCase() === 'IMAGE'));
  const stage = computed(() => project.value?.visualStage || null);
  const stageLabel = computed(() => {
    const s = stage.value;
    if (!s) return '未选择项目';
    return CREATIVE_STAGE_LABELS[s] || s;
  });
  const stageRunning = computed(() => Boolean(stage.value && RUNNING_STAGES.includes(stage.value)));
  const confirmedFacts = computed(() => facts.value.filter((f) => f.confirmStatus === 'CONFIRMED'));
  const requiredOptions = computed(() => fieldOptions.value.filter((o) => o.requiredByGate));
  const unsatisfiedOptions = computed(() => requiredOptions.value.filter((o) => !o.satisfied));

  /** 交付类型：页面 prop 优先，其次项目数据（5 个页面都靠它拿到同一份配置） */
  const deliveryType = computed(
    () => (deliverableTypeHint?.value || project.value?.deliverableType || '').trim()
  );

  /** 场景配置（R13 的展示行 + 步序对照 + R16 的配置步骤）——与指引线共用同一份缓存 */
  const scenario = useScenarioConfig(deliveryType);

  const step1Done = computed(() => Boolean(taskId.value) && imageFiles.value.length > 0);
  const step2Done = computed(() => {
    if (!fieldOptionsLoaded.value) return false;
    return requiredOptions.value.length
      ? unsatisfiedOptions.value.length === 0
      : confirmedFacts.value.length > 0;
  });

  /** 八步的完成判据（**仅回落模式使用**：3~8 由阶段顺序推导；懒加载的精确结论优先） */
  const doneFlags = computed<boolean[]>(() => {
    const s = stage.value;
    const base = [
      step1Done.value,
      step2Done.value,
      stageReached(s, 'DNA_LOCKED'),
      stageReached(s, 'DIRECTION_LOCKED'),
      stageReached(s, 'STORYBOARD_LOCKED'),
      stageReached(s, 'VISUAL_LOCKED'),
      stageReached(s, 'LAYOUT_PROCESSING'),
      s === 'COMPLETED'
    ];
    return base.map((flag, index) => details[STEP_META[index].key]?.done ?? flag);
  });

  /** 回落模式的当前步（配置驱动时不用它） */
  const fallbackActiveNo = computed(() => {
    if (!taskId.value) return 0;
    return resolveActiveNo(doneFlags.value, stage.value);
  });

  /** 配置驱动的计划；配置/步骤状态读不到时为空（此时走 {@link buildFallbackSteps}） */
  const plan = computed<GuideStepPlan[]>(() =>
    taskId.value ? buildGuideSteps(scenario.scenarioSteps.value, projectSteps.value) : []
  );

  /** 是否配置驱动（读不到就回落，保证指引线永远能用） */
  const configDriven = computed(() => plan.value.length > 0);

  /**
   * 配置是否还在读取中（已发请求、还没结果）。
   *
   * <p>有它才能既不闪、也不假：读配置期间**不渲染代码八步回落**，否则页面上会先出现 8 步、
   * 配置到了再跳成 10 步（实测在基因页/分镜页能看到这一下，项目页因为传了 prop 通常快到看不见）。
   * 回落只留给"确实拿不到配置"（交付类型为空或请求失败），那时 `loading` 已经归 false。</p>
   */
  const configPending = computed(
    () => scenario.loading.value && scenario.scenarioSteps.value.length === 0
  );

  /** 计划（配置或回落）——状态在下面按模式填 */
  const plans = computed<GuideStepPlan[]>(() => {
    if (configPending.value) {
      return [];
    }
    return configDriven.value ? plan.value : buildFallbackSteps();
  });

  /** 当前步（计划里的序号）：配置驱动时取"第一个进行中"，回落时按阶段+本地判据 */
  const activeNo = computed(() => {
    if (!taskId.value) return 0;
    if (configDriven.value) {
      const doing = plans.value.find((p) => p.status === 'doing');
      return doing ? doing.no : 0;
    }
    return fallbackActiveNo.value;
  });

  /** 回落模式下某一步的状态（与 R13 之前逐字一致） */
  function fallbackStatus(no: number): FlowStatus {
    if (doneFlags.value[no - 1]) return 'done';
    if (!fallbackPrerequisiteMet(no)) return 'blocked';
    return no === fallbackActiveNo.value ? 'doing' : 'todo';
  }

  /** 回落模式的前置判据（代码八步的老口径，一字未改） */
  function fallbackPrerequisiteMet(no: number): boolean {
    if (no === 4 || no === 5) return doneFlags.value[2];
    if (no === 6) return doneFlags.value[4] && step1Done.value;
    if (no === 7 || no === 8) return doneFlags.value[5];
    return true;
  }

  const steps = computed<FlowStep[]>(() =>
    plans.value.map((item) => {
      const status: FlowStatus = configDriven.value ? item.status : fallbackStatus(item.no);
      const detail = details[item.key];
      const missing = detail?.missing || [];
      // 被阻塞时把"在等哪一步"说在最前面：否则点开只看到"没有图片附件"这类明细，
      // 反而看不出真正卡住的是前一步没完成。
      const blockedHint =
        configDriven.value && status === 'blocked' && item.waiting.length
          ? `前置未完成：先完成「${item.waiting.join('、')}」。`
          : '';
      const tail = detail
        ? missing.length
          ? missing.join('；') + '。'
          : '这一步的明细数据已齐，页面上直接完成即可。'
        : '点开看这一步还缺什么（明细按需加载，不拖慢页面）。';
      return {
        no: item.no,
        key: item.key,
        name: item.name,
        page: item.page,
        status,
        statusLabel: configDriven.value ? CONFIG_STATUS_LABELS[status] : FLOW_STATUS_LABELS[status],
        summary: detail?.summary || defaultSummary(item.key),
        missing,
        reason: blockedHint + tail,
        detailLoaded: Boolean(detail)
      };
    })
  );

  const doneCount = computed(() => steps.value.filter((s) => s.status === 'done').length);
  /** 进度分母：配置驱动时是配置步骤数（十步），回落时是八步 */
  const progressTotal = computed(() => steps.value.length || STEP_META.length);

  /**
   * 未加载明细时的一行摘要（按 step_code 给，不再按步号）。
   *
   * @param key 步骤编码
   * @returns 摘要文本
   */
  function defaultSummary(key: string): string {
    if (key === 'INPUT') {
      return `${files.value.length} 个附件 / ${imageFiles.value.length} 张图片`;
    }
    if (key === 'FACT') {
      return `已确认 ${confirmedFacts.value.length} 条 / 共 ${facts.value.length} 行`;
    }
    return stage.value ? `阶段：${stageLabel.value}` : '待加载';
  }

  /** 某个步骤是否已完成（配置驱动看后端状态；回落模式把编码翻回八步步号） */
  function stepDone(stepCode: string): boolean {
    if (configDriven.value) {
      return projectSteps.value.some(
        (s) => s.stepCode === stepCode && (s.status || '').toUpperCase() === 'DONE'
      );
    }
    const key = FALLBACK_KEY_OF_STEP[stepCode];
    const meta = STEP_META.find((m) => m.key === key);
    return meta ? doneFlags.value[meta.no - 1] ?? false : false;
  }

  /** 分镜 + 候选（出图与质检两步共用，避免重复请求） */
  let ctxCache: { storyboard: DpStoryboardVO | null; generations: DpGenerationVO[] } | null = null;
  async function storyboardAndGenerations() {
    if (!ctxCache) {
      const [sbRes, genRes] = await Promise.all([getStoryboard(taskId.value), listGenerations(taskId.value)]);
      ctxCache = { storyboard: sbRes?.data ?? null, generations: genRes?.data || [] };
    }
    return ctxCache;
  }

  /** 组装判据上下文 */
  function detailContext(id: string | number): StepDetailContext {
    return {
      taskId: id,
      project: project.value,
      files: files.value,
      imageFiles: imageFiles.value,
      facts: facts.value,
      confirmedFacts: confirmedFacts.value,
      fieldOptionsLoaded: fieldOptionsLoaded.value,
      requiredOptions: requiredOptions.value,
      unsatisfiedOptions: unsatisfiedOptions.value,
      stepDone,
      storyboardAndGenerations,
      fallbackSummary: defaultSummary
    };
  }

  /** 加载项目步骤状态（R16：状态的权威来源） */
  async function loadProjectSteps(id: string | number) {
    try {
      const res = await listProjectSteps(id);
      projectSteps.value = res?.data || [];
      stepsLoaded.value = true;
    } catch (e) {
      // 静默降级：步骤状态读不到就回落八步（指引线不能因为配置层挂了而消失）
      projectSteps.value = [];
      stepsLoaded.value = false;
    }
  }

  /** 加载主线数据（项目 + 附件 + 事实 + 步骤状态）。任何一步刷新后都调用一次。 */
  async function reload() {
    detailsClear();
    if (!taskId.value) {
      project.value = null;
      files.value = [];
      facts.value = [];
      fieldOptions.value = [];
      fieldOptionsLoaded.value = false;
      projectSteps.value = [];
      stepsLoaded.value = false;
      return;
    }
    loading.value = true;
    error.value = null;
    try {
      const id = taskId.value;
      const [projectRes, fileRes, factRes, optionRes] = await Promise.all([
        getCreativeProject(id).catch((e) => {
          error.value = e?.message || '项目读取失败';
          return null;
        }),
        listCreativeFiles(id).catch(() => null),
        listFact(id).catch(() => null),
        factFieldOptions(id).catch(() => null)
      ]);
      project.value = projectRes?.data ?? null;
      files.value = fileRes?.data || [];
      facts.value = factRes?.data || [];
      fieldOptions.value = optionRes?.data || [];
      fieldOptionsLoaded.value = optionRes != null;
      // 步骤状态一起等完再收 loading：否则页面会先渲八步、再跳成十步
      await loadProjectSteps(id);
    } finally {
      loading.value = false;
    }
  }

  /**
   * 懒加载某一步的明细，回答「为什么还不能进入下一步」。
   *
   * @param key 步骤编码（`INPUT/FACT/…`，与配置 `step_code` 一致）
   */
  async function ensureStep(key: string) {
    if (!taskId.value || details[key]) {
      return;
    }
    const checker = STEP_DETAIL_CHECKERS[key];
    if (!checker) {
      // 不猜：配置里新增了这一步、前端还没注册判据时，如实说明
      details[key] = {
        missing: [`这一步（${key}）还没有注册明细判据，暂时无法说明还缺什么`],
        summary: defaultSummary(key)
      };
      return;
    }
    try {
      details[key] = await checker(detailContext(taskId.value));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : '明细读取失败';
      details[key] = { missing: [`明细读取失败：${message}`], summary: defaultSummary(key) };
    }
  }

  /** 清空明细缓存（切项目或刷新后调用） */
  function detailsClear() {
    Object.keys(details).forEach((k) => delete details[k]);
    ctxCache = null;
  }

  /** 供页面在完成某个动作后调用：清明细并重算 */
  async function refresh() {
    await reload();
  }

  /** 该步对应的页面地址（带 taskId） */
  function stepHref(no: number): string {
    const meta = plans.value.find((m) => m.no === no);
    const base = meta?.page || '/creative/project';
    return taskId.value ? `${base}?taskId=${taskId.value}` : base;
  }

  // 项目切换：清明细并重新取步骤状态（交付类型变化时场景配置自己会重载）
  watch(
    () => taskId.value,
    (id) => {
      ctxCache = null;
      if (id) {
        void loadProjectSteps(id);
      }
    }
  );

  return {
    loading,
    error,
    project,
    files,
    imageFiles,
    facts,
    confirmedFacts,
    fieldOptions,
    fieldOptionsLoaded,
    stage,
    stageLabel,
    stageRunning,
    steps,
    activeNo,
    doneCount,
    progressTotal,
    configDriven,
    configPending,
    stepsLoaded,
    projectSteps,
    deliveryType,
    scenarioSteps: scenario.scenarioSteps,
    scenarioLine: scenario.line,
    mapping: scenario.mapping,
    mappingChip: scenario.mappingChip,
    ensureStep,
    refresh,
    reload,
    detailsClear,
    stepHref
  };
}
