/**
 * 流程指引线的**纯逻辑**（阶段 → 步骤映射、当前步解析、八步骨架）。
 *
 * <p>为什么单独一个文件：这些判定是「当前环节对不对」的唯一判据，必须能被单测直接钉住。
 * 放在 {@code useCreativeFlow.ts} 里会被 API/请求层（{@code @/utils/push} 在模块加载时就用
 * {@code window}）带进 node 测试环境而直接报错——那不是逻辑有问题，却让逻辑测不了。
 * 所以这里只依赖 `@/api/creative/types`（纯类型与常量，无副作用）。</p>
 *
 * @author creative
 */
import { CREATIVE_STAGE_LABELS } from '@/api/creative/types';

/** 步骤状态：已完成 / 进行中 / 未开始 / 被阻塞 */
export type FlowStatus = 'done' | 'doing' | 'todo' | 'blocked';

/** 指引线上的一个步骤 */
export interface FlowStep {
  no: number;
  key: string;
  name: string;
  /** 该步归属的页面路径（点击跳转用；带 taskId 由组件补） */
  page: string;
  status: FlowStatus;
  statusLabel: string;
  /** 一行摘要（如「3 个方向 · 已选定 A」） */
  summary: string;
  /** 缺什么（懒加载后填充；未加载时为空数组） */
  missing: string[];
  /** 未加载时给一句「点开看缺什么」，加载后给具体原因 */
  reason: string;
  /** 该步明细是否已加载 */
  detailLoaded: boolean;
}

export const FLOW_STATUS_LABELS: Record<FlowStatus, string> = {
  done: '已完成',
  doing: '进行中',
  todo: '未开始',
  blocked: '被阻塞'
};

/**
 * 阶段顺序：直接取标签表的键序（与后端 DpVisualStageEnum 的声明顺序一致）。
 * 刻意不另抄一份列表——两份顺序迟早会不一致。
 */
export const STAGE_ORDER = Object.keys(CREATIVE_STAGE_LABELS);

/** 后端 DpVisualStageEnum#isRunning 的口径镜像（进行中的阶段页面应显示进度而不是静止） */
export const RUNNING_STAGES = [
  'DNA_GENERATING',
  'DIRECTION_GENERATING',
  'STORYBOARD_GENERATING',
  'PRODUCING',
  'QA_PROCESSING',
  'LAYOUT_PROCESSING'
];

/** 八步骨架：步骤号、键、名称、归属页面 */
export const STEP_META: Array<{ no: number; key: string; name: string; page: string }> = [
  { no: 1, key: 'material', name: '项目与资料', page: '/creative/project' },
  { no: 2, key: 'fact', name: '事实确认', page: '/creative/project' },
  { no: 3, key: 'dna', name: '视觉基因', page: '/creative/dna' },
  { no: 4, key: 'direction', name: '视觉方向', page: '/creative/storyboard' },
  { no: 5, key: 'storyboard', name: '分镜', page: '/creative/storyboard' },
  { no: 6, key: 'gate', name: '视觉门', page: '/creative/review' },
  { no: 7, key: 'production', name: '出图', page: '/creative/production' },
  { no: 8, key: 'layout', name: '详情页排版与终审', page: '/creative/review' }
];

/** 阶段在顺序表里的下标（未知返回 -1） */
export function stageIndexOf(stage?: string | null): number {
  if (!stage) return -1;
  return STAGE_ORDER.indexOf(stage);
}

/** 阶段下标是否已达某阶段 */
export function stageReached(stage: string | null | undefined, target: string): boolean {
  const current = stageIndexOf(stage);
  const goal = STAGE_ORDER.indexOf(target);
  if (current < 0 || goal < 0) return false;
  return current >= goal;
}

/** 阶段落在第几步（3~8；未知按「基因」起步，不猜成已完成） */
export function stageStepOf(stage?: string | null): number {
  const i = stageIndexOf(stage);
  if (i <= stageIndexOf('DNA_LOCKED')) return 3;
  if (i <= stageIndexOf('DIRECTION_LOCKED')) return 4;
  if (i <= stageIndexOf('STORYBOARD_LOCKED')) return 5;
  if (i <= stageIndexOf('VISUAL_LOCKED')) return 6;
  if (i <= stageIndexOf('QA_PROCESSING')) return 7;
  return 8;
}

/**
 * 当前步解析（纯函数，导出供单测钉住——这是「当前环节对不对」的唯一判据）。
 *
 * <p>规则：**永远是「还没做完的第一步」**，且不会比阶段暗示的位置更靠前。
 * 为什么取两者较大：阶段单调推进，而人会跳步与返工。例：视觉门通过后阶段是
 * {@code VISUAL_LOCKED}，它落在第 6 步区间，但第 6 步此时已完成——真正该做的是第 7 步出图；
 * 反过来若把阶段退回 {@code DNA_REVIEW} 重做基因，第 3 步未完成，当前步应回到第 3 步。</p>
 *
 * @param doneFlags 八步完成标记（下标 0 对应第 1 步）
 * @param stage     当前视觉阶段
 * @returns 当前步号（1~8）；八步全部完成返回 0（线上不该有任何一步在脉冲）
 */
export function resolveActiveNo(doneFlags: boolean[], stage?: string | null): number {
  if (!doneFlags[0]) return 1;
  if (!doneFlags[1]) return 2;
  const firstNotDone = doneFlags.findIndex((flag) => !flag);
  if (firstNotDone < 0) return 0;
  return Math.max(firstNotDone + 1, Math.max(3, stageStepOf(stage)));
}
