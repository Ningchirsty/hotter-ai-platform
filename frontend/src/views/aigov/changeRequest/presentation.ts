import type { AigChangeRequestVO, AigChangeStatus } from '@/api/aigov/changeRequest/types';

/**
 * 变更单展示口径（Q1C 完整版）。
 *
 * <p><b>必须区分 CONFLICTED 与 REJECTED</b>：前者是"系统拒绝执行"（现场被改过），
 * 后者是"审批人不同意"。混在一起会让人以为是审批人的判断，处置方向完全不同。</p>
 */

/** 标签元数据 */
export interface TagMeta {
  label: string;
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'gray';
}

/**
 * 状态 → 标签。
 *
 * @param status 状态码（可空）
 * @returns 标签
 */
export function statusMeta(status?: string): TagMeta {
  switch (status as AigChangeStatus) {
    case 'PENDING':
      return { label: '待批准', type: 'warning' };
    case 'APPLIED':
      return { label: '已生效', type: 'success' };
    case 'REJECTED':
      return { label: '已驳回', type: 'danger' };
    case 'CANCELLED':
      return { label: '已撤回', type: 'gray' };
    case 'CONFLICTED':
      return { label: '冲突未生效', type: 'info' };
    default:
      return { label: status || '未知状态', type: 'gray' };
  }
}

/**
 * 变更类型 → 标签。
 *
 * @param changeType 类型码（可空）
 * @returns 标签
 */
export function typeMeta(changeType?: string): TagMeta {
  switch (changeType) {
    case 'POLICY_CREATE':
      return { label: '新增策略', type: 'primary' };
    case 'POLICY_UPDATE':
      return { label: '修改策略', type: 'warning' };
    case 'POLICY_DELETE':
      return { label: '删除策略', type: 'danger' };
    default:
      return { label: changeType || '未知类型', type: 'gray' };
  }
}

/**
 * 把 JSON 快照渲染成"人能读的一行"（审批人要看清批的是什么值）。
 *
 * <p>不认识的键原样回显：宁可显示原始键名，也不要把不认识的改动藏起来。</p>
 *
 * @param json    快照 JSON（可空）
 * @param changed 只显示与它不同的键（可空=全显示）
 * @returns 形如 `allowExternal: N → Y` 的条目文本
 */
export function snapshotLines(json?: string, changed?: string): string[] {
  const mine = parse(json);
  const other = changed ? parse(changed) : undefined;
  const lines: string[] = [];
  for (const [key, value] of Object.entries(mine)) {
    if (other && String(other[key]) === String(value)) {
      continue;
    }
    lines.push(other ? `${key}: ${other[key] ?? '（无）'} → ${value ?? '（无）'}` : `${key}: ${value ?? '（无）'}`);
  }
  return lines;
}

/**
 * 解析快照 JSON（解析失败返回空对象，由调用方显示"原始文本"）。
 *
 * @param json JSON 文本（可空）
 * @returns 键值对象
 */
export function parse(json?: string): Record<string, unknown> {
  if (!json) {
    return {};
  }
  try {
    const parsed = JSON.parse(json);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

/**
 * 一行结论（列表里直接显示"发生了什么"）。
 *
 * @param row 变更单
 * @returns 结论文本
 */
export function outcomeText(row?: AigChangeRequestVO): string {
  if (!row) {
    return '';
  }
  if (row.status === 'APPLIED') {
    return `已于 ${row.appliedAt || '—'} 生效（批准人 ${row.approverName || row.approverId || '—'}）`;
  }
  if (row.status === 'CONFLICTED') {
    return `未生效：${row.resultRemark || '与现场冲突'}`;
  }
  if (row.status === 'REJECTED') {
    return `已驳回：${row.decisionRemark || '（未写理由）'}`;
  }
  if (row.status === 'CANCELLED') {
    return '申请人已撤回';
  }
  return row.status === 'PENDING' ? '等待另一位持有「能力高风险变更」权限的同事批准' : '';
}
