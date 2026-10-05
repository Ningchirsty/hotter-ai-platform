import { computed, inject, provide, type ComputedRef } from 'vue';

/**
 * 「本页步骤」的编号（v1 人工测试反馈：出图显示成「7.」而不是「3.」、事实确认(4) 排在文案与要点(3) 前面）。
 *
 * <p><b>根因</b>：编号有两个来源，而且都取自**全局流程**——
 * ① 工作台步骤条用流程里的 `no`（第 7 步＝出图）；
 * ② 每个区块标题里写死了号（`<h4>4. 事实确认</h4>`）。
 * 于是同一个页面上会出现"4 在 3 前面""出图是第 7 步"这种读不通的顺序。</p>
 *
 * <p><b>正确口径</b>：编号应当回答"**在这个页面上**我该按什么顺序看"——
 * 也就是页面实际托管的步骤顺序（分镜页：视觉方向 1 / 分镜 2 / 出图 3）。
 * 整条流程的顺序由「流程指引线」负责展示，两者分工不同，不该互相冒充。</p>
 *
 * <p><b>为什么用 provide/inject 而不是插槽属性</b>：工作台是唯一知道"本页有哪几步、
 * 这一步排第几"的地方；用插槽属性就得让 5 个页面、十几个插槽逐个转发，
 * 少转一个就静默退化成没有编号。provide/inject 让区块自己取，页面不必知道这件事。</p>
 *
 * <p><b>没有上下文时不显示编号</b>（区块被单独用在别处时）：宁可没有号，
 * 也不要编一个可能错的号——编号错了比没有号更难发现。</p>
 *
 * @author creative
 */

/** 注入键（Symbol 避免与其它注入撞名） */
const STEP_NUMBERING_KEY = Symbol('creative-step-numbering');

/** 本页步骤的编号上下文 */
export interface StepNumbering {
  /** 本页步骤序号（1 起） */
  no: number;
  /** 这一步里要渲染的组件数（>1 时区块标题用 2.1 / 2.2 这种子号，避免两块共用同一个号） */
  size: number;
  /** 取某组件在这一步里的次序（1 起；不在这一步里时返回 0） */
  orderOf: (component: string) => number;
}

/**
 * 由工作台调用：把"本页步骤编号"提供给子树里的区块。
 *
 * @param ctx 编号上下文（响应式：切步骤时要跟着变）
 */
export function provideStepNumbering(ctx: ComputedRef<StepNumbering>): void {
  provide(STEP_NUMBERING_KEY, ctx);
}

/**
 * 由区块调用：取自己的标题编号前缀。
 *
 * @param component 组件名（与工作台装配配置里写的名字一致，例如 `ProjectFactsBlock`）
 * @returns 形如 `"2.1 "` / `"2. "`；没有上下文或不在当前步时返回空串
 */
export function useStepHeading(component: string): ComputedRef<string> {
  const ctx = inject<ComputedRef<StepNumbering> | null>(STEP_NUMBERING_KEY, null);
  return computed(() =>
    formatStepHeading(ctx?.value?.no || 0, ctx?.value?.size || 0, ctx?.value?.orderOf(component) || 0)
  );
}

/**
 * 编号前缀的纯函数（抽出来是为了能单测：provide/inject 在无渲染环境里测不动）。
 *
 * @param no    本页步骤号（0 = 没有上下文 / 没有可见步）
 * @param size  这一步要渲染的区块数
 * @param order 本区块在这一步里的次序（1 起；0 = 不在这一步里）
 * @returns 形如 `"2.1 "` / `"2. "`；没有步骤号时返回空串
 */
export function formatStepHeading(no: number, size: number, order: number): string {
  if (!no || no < 1) {
    return '';
  }
  if (size > 1 && order > 0) {
    return `${no}.${order} `;
  }
  return `${no}. `;
}
