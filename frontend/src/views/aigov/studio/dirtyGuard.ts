/**
 * 未保存改动的保护（设计 D06、§6.3、验收 §11.2-2）。
 *
 * <p>三条纪律集中在这里，供「关闭编辑器 / 离开页面 / 采纳提案」共用：</p>
 * <ol>
 *   <li><b>只有三个选择</b>：保存并继续、放弃本地改动并继续、返回继续编辑。
 *       不提供"直接继续"这种会把未保存内容静默丢掉的动作——静默丢失是这里唯一不可接受的结果；</li>
 *   <li><b>提示里写清"会发生什么"</b>：说明 X 会以「已保存的修订」为基准，本地改动将丢失；</li>
 *   <li><b>浏览器关闭/刷新只能给通用提示</b>：自定义按钮不可用，因此这里只决定是否拦截
 *       （{@link beforeUnloadShouldWarn}）。</li>
 * </ol>
 *
 * @author ai-gov
 */

/** 三个选择 */
export type DirtyChoice = 'SAVE' | 'DISCARD' | 'CANCEL';

export interface DirtyChoiceOption {
  key: DirtyChoice;
  label: string;
  /** Element Plus 按钮类型 */
  type: 'primary' | 'danger' | 'info';
}

/**
 * 三个选择。<b>刻意固定为三项</b>：少于三项就意味着某条路径会让内容静默丢失。
 */
export const DIRTY_CHOICES: readonly DirtyChoiceOption[] = [
  { key: 'SAVE', label: '保存并继续', type: 'primary' },
  { key: 'DISCARD', label: '放弃本地改动并继续', type: 'danger' },
  { key: 'CANCEL', label: '返回继续编辑', type: 'info' },
] as const;

/** 是否需要在动作前弹出三选一 */
export function needsGuard(dirty: boolean): boolean {
  return dirty;
}

/**
 * 生成提示文案。
 *
 * @param actionLabel 即将发生的动作（如"关闭编辑器""采纳提案""离开页面"）
 */
export function unsavedMessage(actionLabel: string): string {
  return (
    `有未保存的改动。「${actionLabel}」会以「已保存的修订」为基准，本地改动将丢失。` +
    `请选择：保存并继续、放弃本地改动并继续、或返回继续编辑。`
  );
}

/** 采纳提案场景的提示（文案与关闭/离开不同的原因：采纳会改基准修订并重载编辑器） */
export function adoptUnsavedMessage(): string {
  return unsavedMessage('采纳提案');
}

/** 浏览器关闭/刷新时是否拦截（自定义三选一在 beforeunload 里不可用） */
export function beforeUnloadShouldWarn(dirty: boolean): boolean {
  return dirty;
}
