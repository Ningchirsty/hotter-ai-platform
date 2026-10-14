/**
 * 训练台布局口径（设计 §6.3、§5.4 图一）。
 *
 * <p>训练台按「左侧步骤 / 中间编辑 / 右侧辅助区」组织：左侧七个步骤对应设计给出的
 * 「岗位与目标、输入与成果、知识、工具与流程、行为规则、测试、发布」；右侧辅助区可在
 * 「培训助手 / 测试预览 / 修订差异」之间切换。这里把步骤与辅助区的**唯一口径**放在一处，
 * 避免界面各处又各写一份清单。</p>
 *
 * @author ai-gov
 */

export interface StepItem {
  key: string;
  label: string;
}

export interface TabItem {
  key: string;
  label: string;
}

/** 左侧七个步骤（顺序即展示顺序） */
export const STUDIO_STEPS: readonly StepItem[] = [
  { key: 'role', label: '岗位与目标' },
  { key: 'io', label: '输入与成果' },
  { key: 'knowledge', label: '知识' },
  { key: 'tools', label: '工具与流程' },
  { key: 'rules', label: '行为规则' },
  { key: 'test', label: '测试' },
  { key: 'release', label: '发布' }
] as const;

/** 右侧辅助区（可切换） */
export const AUX_TABS: readonly TabItem[] = [
  { key: 'copilot', label: '培训助手' },
  { key: 'test', label: '测试预览' },
  { key: 'diff', label: '修订差异' }
] as const;

/**
 * 保存状态文案（设计 §6.3：至少区分已保存、未保存、保存中、保存失败、版本冲突）。
 *
 * <p>这里只覆盖前端能直接判定的三种；保存失败与版本冲突由服务端响应驱动，
 * 走消息提示而不是常驻状态，避免"上一次失败"长期挂在页面上造成误判。</p>
 */
export function saveStateLabel(dirty: boolean, saving: boolean): string {
  if (saving) {
    return '保存中';
  }
  return dirty ? '有未保存改动' : '已保存';
}

/** 保存状态标签色 */
export function saveStateTagType(dirty: boolean, saving: boolean): 'info' | 'warning' | 'success' {
  if (saving) {
    return 'info';
  }
  return dirty ? 'warning' : 'success';
}

/** 修订号文案（没有修订号时如实显示 —，不显示 0） */
export function revisionText(revision?: number): string {
  return revision === undefined || revision === null ? '—' : '#' + revision;
}
