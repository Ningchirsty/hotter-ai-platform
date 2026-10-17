/**
 * 发布状态的人话（走读 §5.5：「状态胶囊 5 类统一，不再直出裸枚举」）。
 *
 * <p>取值与后端 {@code AigReleaseStatusEnum} 一一对应。**认不出的原样回显**——
 * 宁可让人看到原始码，也不要把不认识的取值塞进「草稿」或「已发布」（那会误导审批与发布判断）。</p>
 *
 * <p>抽成共享函数的原因：Agent 版本、Skill/Package 版本、知识引用的版本状态都是同一套词表，
 * 各页各写一份迟早出现"同一状态在两个页面颜色/文案不同"。</p>
 */

/** 标签色调（Element Plus tag type） */
export type ReleaseTone = 'success' | 'warning' | 'info' | 'danger' | 'primary';

/** 发布状态的展示口径 */
export interface ReleaseStatusMeta {
  /** 人话 */
  label: string;
  /** 色调 */
  tone: ReleaseTone;
}

const META: Record<string, ReleaseStatusMeta> = {
  DRAFT: { label: '草稿', tone: 'info' },
  VALIDATED: { label: '已校验', tone: 'info' },
  SANDBOX_TESTED: { label: '沙箱已跑通', tone: 'primary' },
  CANDIDATE: { label: '候选（灰度）', tone: 'warning' },
  STABLE: { label: '稳定（已发布）', tone: 'success' },
  DISABLED: { label: '已停用', tone: 'danger' },
  ARCHIVED: { label: '已归档', tone: 'info' }
};

/**
 * 发布状态 → 文案与色调。
 *
 * @param status 状态码（可空）
 * @returns 展示元数据
 */
export function releaseStatusMeta(status?: string | null): ReleaseStatusMeta {
  const code = (status ?? '').trim().toUpperCase();
  if (!code) {
    return { label: '未设置', tone: 'info' };
  }
  return META[code] ?? { label: code, tone: 'info' };
}

/**
 * 发布状态 → 人话（只要文案时用这个）。
 *
 * @param status 状态码（可空）
 * @returns 文案
 */
export function releaseStatusLabel(status?: string | null): string {
  return releaseStatusMeta(status).label;
}
