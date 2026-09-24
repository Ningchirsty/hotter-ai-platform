/**
 * 视觉工厂「流程指引线」状态（唯一真相处）。
 *
 * <p><b>为什么要有它</b>：R4 曾用一个独立向导页承载八步流程，结果是「做完一步要退回菜单再进下一页」，
 * 而且在 6 个页面之间来回跳。R5 改成「每个环节页面顶部一条指引线」，于是同一套判定必须被 5 个页面共用——
 * 一旦逐页复制，就会出现「两个口径」（本工程一直在避免的漂移）。</p>
 *
 * <p><b>两段式取数（关键设计）</b>：</p>
 * <ul>
 *   <li><b>主线（便宜、始终加载）</b>：项目 {@code visualStage} + 附件 + 事实。
 *       八步的「已完成/当前/未开始/被阻塞」由 {@code cp_task.visual_stage} 的<b>顺序</b>推导——
 *       它是后端唯一写入的阶段列（{@code CreativeProjectService.moveStage}），
 *       天然支持跳步与返工，不需要前端再猜一套状态。</li>
 *   <li><b>细化（按需、懒加载）</b>：用户悬停/点开某一步时才去取该步的明细，
 *       算出「为什么还不能进入下一步」。这样指引线不会因为一次要打 10 个接口而拖慢页面。</li>
 * </ul>
 *
 * <p><b>权威在后端</b>：阶段描述的权威是 {@code DpVisualStageEnum}，本文件只做兜底展示与顺序映射；
 * 判「基因是否就绪」等具体条件仍以接口返回为准（懒加载那一段），不在前端另立标准。</p>
 *
 * @author creative
 */
import { computed, reactive, ref, type Ref } from 'vue';
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

// 纯逻辑（阶段映射 / 当前步 / 八步骨架）全部在 creativeFlowSteps.ts，那里可被 node 环境单测直接钉住
export {
  FLOW_STATUS_LABELS,
  STEP_META,
  resolveActiveNo,
  stageReached,
  stageStepOf,
  STAGE_ORDER
} from './creativeFlowSteps';
export type { FlowStatus, FlowStep } from './creativeFlowSteps';

interface StepDetail {
  summary: string;
  missing: string[];
  /**
   * 懒加载得出的「已完成」修正（只可能把 stage 推不出来的完成态补上，不会反向改）：
   * 例如阶段已经进入排版，但「7 屏都选定了候选」这件事只有查生成记录才知道。
   */
  done?: boolean;
}

/**
 * 流程指引线的状态与数据。
 *
 * @param taskId 当前项目ID（响应式；为空时指引线显示「未选择项目」）
 */
export function useCreativeFlow(taskId: Ref<string | number | undefined>) {
  const loading = ref(false);
  const error = ref<string | null>(null);

  const project = ref<CreativeProjectVO | null>(null);
  const files = ref<CpTaskFileVO[]>([]);
  const facts = ref<CpFactSnapshotVO[]>([]);
  const fieldOptions = ref<CpFactFieldOptionVO[]>([]);
  const fieldOptionsLoaded = ref(false);

  /** 各步的明细（懒加载后写入） */
  const details = reactive<Record<number, StepDetail>>({});

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

  const step1Done = computed(() => Boolean(taskId.value) && imageFiles.value.length > 0);
  const step2Done = computed(() => {
    if (!fieldOptionsLoaded.value) return false;
    return requiredOptions.value.length
      ? unsatisfiedOptions.value.length === 0
      : confirmedFacts.value.length > 0;
  });

  /** 八步的完成判据（3~8 由阶段顺序推导；懒加载的精确结论优先） */
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
    return base.map((flag, index) => details[index + 1]?.done ?? flag);
  });

  /** 当前步（规则见 {@link resolveActiveNo}） */
  const activeNo = computed(() => {
    if (!taskId.value) return 0;
    return resolveActiveNo(doneFlags.value, stage.value);
  });

  /** 前置是否满足（不满足＝后端会直接拒绝该动作，属「被阻塞」而不是「未开始」） */
  function prerequisiteMet(no: number): boolean {
    if (no === 4 || no === 5) return doneFlags.value[2];
    if (no === 6) return doneFlags.value[4] && step1Done.value;
    if (no === 7 || no === 8) return doneFlags.value[5];
    return true;
  }

  const steps = computed<FlowStep[]>(() =>
    STEP_META.map((meta) => {
      const idx = meta.no - 1;
      const done = doneFlags.value[idx];
      const detail = details[meta.no];
      let status: FlowStatus;
      if (done) status = 'done';
      else if (!prerequisiteMet(meta.no)) status = 'blocked';
      // 「当前步」按定义不会是已完成的一步（activeNo 已保证只指向未完成步）
      else if (meta.no === activeNo.value) status = 'doing';
      else status = 'todo';
      return {
        ...meta,
        status,
        statusLabel: FLOW_STATUS_LABELS[status],
        summary: detail?.summary || defaultSummary(meta.no),
        missing: detail?.missing || [],
        reason: detail
          ? detail.missing.length
            ? detail.missing.join('；') + '。'
            : '这一步的明细数据已齐，页面上直接完成即可。'
          : '点开看这一步还缺什么（明细按需加载，不拖慢页面）。',
        detailLoaded: Boolean(detail)
      };
    })
  );

  const doneCount = computed(() => steps.value.filter((s) => s.status === 'done').length);

  /** 未加载明细时的一行摘要 */
  function defaultSummary(no: number): string {
    switch (no) {
      case 1:
        return `${files.value.length} 个附件 / ${imageFiles.value.length} 张图片`;
      case 2:
        return `已确认 ${confirmedFacts.value.length} 条 / 共 ${facts.value.length} 行`;
      default:
        return stage.value ? `阶段：${stageLabel.value}` : '待加载';
    }
  }

  /** 加载主线数据（项目 + 附件 + 事实）。任何一步刷新后都调用一次。 */
  async function reload() {
    detailsClear();
    if (!taskId.value) {
      project.value = null;
      files.value = [];
      facts.value = [];
      fieldOptions.value = [];
      fieldOptionsLoaded.value = false;
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
    } finally {
      loading.value = false;
    }
  }

  /**
   * 懒加载某一步的明细，用于回答「为什么还不能进入下一步」。
   *
   * @param no 步骤号（1~8）
   */
  async function ensureStep(no: number) {
    if (!taskId.value || details[no]) return;
    const id = taskId.value;
    try {
      switch (no) {
        case 1: {
          const missing: string[] = [];
          if (!imageFiles.value.length) missing.push('项目里还没有图片附件（产品照片 / 参考图）');
          if (project.value && project.value.productId == null) {
            missing.push('项目没有关联产品，照片无法登记为产品图（产品基准质检会没有基准图）');
          }
          details[1] = { missing, summary: defaultSummary(1) };
          break;
        }
        case 2: {
          const missing: string[] = [];
          if (!fieldOptionsLoaded.value) {
            missing.push('字段选项接口没取到，无法判断闸门必填项是否齐备');
          } else {
            if (unsatisfiedOptions.value.length) {
              missing.push(
                `必填事实还差 ${unsatisfiedOptions.value.length} 项：` +
                  unsatisfiedOptions.value.map((o) => o.fieldName || o.fieldCode).join('、')
              );
            }
            if (!requiredOptions.value.length && !confirmedFacts.value.length) {
              missing.push('该交付类型没有声明必填事实项，同时也没有任何已确认事实');
            }
          }
          details[2] = { missing, summary: defaultSummary(2) };
          break;
        }
        case 3: {
          const res = await getDna(id);
          const dna: DpVisualDnaVO | null = res?.data ?? null;
          const missing: string[] = [];
          if (!imageFiles.value.length) missing.push('没有参考图，「按参考图推荐」没有实测依据');
          if (!confirmedFacts.value.length) missing.push('没有已确认事实，基因与文案只能靠默认规范推导');
          if (dna && (dna.issues || []).length) {
            missing.push(`当前版本自洽校验未过：${(dna.issues || []).join('；')}`);
          }
          if (dna && !dna.locked && !(dna.issues || []).length) missing.push('当前版本还没有锁定');
          if (!dna) missing.push('还没有生成视觉基因');
          details[3] = {
            missing,
            summary: dna ? `${dna.dnaNo} v${dna.version} · ${dna.statusDesc}` : '还没有基因版本'
          };
          break;
        }
        case 4: {
          const res = await listDirections(id);
          const rows: DpVisualDirectionVO[] = res?.data || [];
          const selected = rows.find((d) => d.status === 'SELECTED') || null;
          const missing: string[] = [];
          if (!doneFlags.value[2]) missing.push('视觉基因还没有锁定版本（后端会拒绝生成方向）');
          if (rows.length && !selected) missing.push('还没有在 A/B/C 中选定方向');
          if (!rows.length && doneFlags.value[2]) missing.push('还没有生成方向');
          details[4] = {
            missing,
            summary: `${rows.length} 个方向${selected ? ' · 已选定 ' + selected.directionCode : ''}`
          };
          break;
        }
        case 5: {
          const res = await getStoryboard(id);
          const sb: DpStoryboardVO | null = res?.data ?? null;
          const missing: string[] = [];
          if (!doneFlags.value[2]) missing.push('视觉基因还没有锁定版本（后端会拒绝生成分镜）');
          if (!sb && doneFlags.value[2]) missing.push('还没有生成分镜');
          if (sb && sb.status !== 'LOCKED') missing.push('当前分镜还是草稿，没有锁定');
          if (!doneFlags.value[3]) missing.push('方向未选定（不影响生成，但分镜不会带上方向的取舍）');
          const noSolo = (sb?.screens || []).filter((s) => !s.pictureSoloStatement);
          if (noSolo.length) {
            missing.push(`${noSolo.length} 屏缺少「画面独白」：${noSolo.map((s) => s.screenNo).join('、')}`);
          }
          details[5] = {
            missing,
            summary: sb
              ? `${sb.storyboardNo} v${sb.version} · ${sb.statusDesc} · ${sb.screenCount ?? 0} 屏`
              : '还没有分镜'
          };
          break;
        }
        case 6: {
          const res = await getVisualGate(id);
          const gate: GateEvaluationVO | null = res?.data ?? null;
          const missing: string[] = gate ? [...(gate.blocked || [])] : ['视觉门评估还没取到'];
          if (gate && gate.submittable && !gate.passed) {
            if (gate.cardStatus === 'PENDING') missing.push('已提交，等待人工确认（本页可直接确认/打回）');
            else if (gate.cardStatus === 'BLOCKED') missing.push('已被打回，请修改方案后重新提交');
            else missing.push('硬性项已满足，但还没有提交审核');
          }
          details[6] = {
            missing,
            summary: gate ? (gate.passed ? '已通过' : `未通过（${gate.cardStatus || '未提交'}）`) : '评估未取到'
          };
          break;
        }
        case 7: {
          const [sbRes, genRes] = await Promise.all([getStoryboard(id), listGenerations(id)]);
          const sb: DpStoryboardVO | null = sbRes?.data ?? null;
          const gens: DpGenerationVO[] = genRes?.data || [];
          const screens = sb?.screens || [];
          const selectedCount = screens.filter((s) => gens.some((g) => g.screenId === s.id && g.status === 'APPROVED')).length;
          const unselected = screens.filter((s) => !gens.some((g) => g.screenId === s.id && g.status === 'APPROVED'));
          const missing: string[] = [];
          if (!sb) missing.push('还没有分镜，无法出图');
          else if (sb.status !== 'LOCKED') missing.push('分镜还没锁定（批量出图以最新分镜为准）');
          if (!doneFlags.value[5]) missing.push('视觉门还没通过（后端会拒绝出图请求）');
          if (sb && !screens.length) missing.push('分镜里没有屏');
          if (unselected.length) {
            missing.push(`${unselected.length} 屏还没有选定候选：${unselected.map((s) => s.screenNo).join('、')}`);
          }
          details[7] = {
            missing,
            summary: `${gens.length} 个候选 / ${screens.length} 屏，已选定 ${selectedCount} 屏`,
            // 每屏都选定了候选就是真的做完了（阶段可能还没推）
            done: screens.length > 0 && unselected.length === 0
          };
          break;
        }
        case 8: {
          const [res, genRes] = await Promise.all([getDetailPage(id), listGenerations(id)]);
          const page: DpDetailPageVO | null = res?.data ?? null;
          const versions = page?.versions || [];
          const approvedVision = versions.find((v) => v.status === 'APPROVED' && v.kind !== 'V10_FINAL');
          const finalVersion = versions.find((v) => v.kind === 'V10_FINAL');
          const missing: string[] = [];
          if (!doneFlags.value[5]) missing.push('视觉门还没通过（后端会拒绝渲染）');
          if (!versions.length) missing.push('还没有渲染过机排版版本');
          if (versions.length && !finalVersion && !approvedVision) {
            missing.push('已有版本但还没有终审通过，也没有上传精修最终版');
          }
          if (approvedVision && !finalVersion) {
            missing.push('机排版已终审通过，还差上传精修最终版（V1.0）');
          }
          details[8] = {
            missing,
            summary: page
              ? `v${page.currentVersion ?? 0} · ${page.statusDesc || '未排版'} · ${versions.length} 个版本`
              : '详情页未取到',
            done: Boolean(finalVersion)
          };
          break;
        }
        default:
          break;
      }
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : '明细读取失败';
      details[no] = { missing: [`明细读取失败：${message}`], summary: defaultSummary(no) };
    }
  }

  /** 清空明细缓存（切项目或刷新后调用） */
  function detailsClear() {
    Object.keys(details).forEach((key) => delete details[Number(key)]);
  }

  /** 供页面在完成某个动作后调用：清明细并重算 */
  async function refresh() {
    await reload();
  }

  /** 该步对应的页面地址（带 taskId） */
  function stepHref(no: number): string {
    const meta = STEP_META.find((m) => m.no === no);
    const base = meta?.page || '/creative/project';
    return taskId.value ? `${base}?taskId=${taskId.value}` : base;
  }

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
    ensureStep,
    refresh,
    reload,
    detailsClear,
    stepHref
  };
}
