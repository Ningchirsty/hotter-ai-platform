/**
 * 任务流程节点展示口径（纯函数，可单测）。
 *
 * @author ai-gov
 */

import type { AigTaskFlowNodeVO } from '@/api/aigov/task/types';

/** 节点类型（与后端 AigFlowNodeTypeEnum 一致） */
export const FLOW_NODE_TYPE_LABELS: Record<string, string> = {
  STEP: '顺序步骤',
  CONDITION: '条件判断',
  HUMAN_CONFIRM: '人工确认',
  OUTPUT: '输出节点'
};

/** 类型码 → 文案；认不出的码原样展示（不假装知道） */
export function flowNodeTypeLabel(type?: string): string {
  if (!type) {
    // 存量步骤没有登记节点身份：如实说明，不猜一个类型
    return '未登记身份';
  }
  return FLOW_NODE_TYPE_LABELS[type] ?? type;
}

/**
 * 节点状态文案。
 *
 * 「等待人工确认」用后端下发的 `awaitingConfirmation` 判定，而不是前端自己拼
 * 「RUNNING 且没有批准引用」——两处判据迟早不一致，而方向恰好是「界面说已批准、服务端说没有」。
 */
export function flowNodeStatusText(node?: AigTaskFlowNodeVO): string {
  if (!node) {
    return '—';
  }
  if (node.awaitingConfirmation) {
    return '等待人工确认';
  }
  switch (node.status) {
    case 'PLANNED':
      return '待执行';
    case 'RUNNING':
      return '执行中';
    case 'SUCCEEDED':
      // 人工确认通过时把凭据带出来：不然「已通过」看不出是谁批的
      if (node.nodeType === 'HUMAN_CONFIRM') {
        return node.approvalRef ? `已批准（${node.approvalRef}）` : '已批准（无凭据）';
      }
      return '已通过';
    case 'SKIPPED':
      return '已跳过';
    case 'FAILED':
      return node.errorCode ? `已失败：${node.errorCode}` : '已失败';
    default:
      return node.status || '—';
  }
}

/** 状态标签色：等确认最需要被看见，失败次之 */
export function flowNodeTagType(node?: AigTaskFlowNodeVO): 'warning' | 'danger' | 'success' | 'info' {
  if (!node) {
    return 'info';
  }
  if (node.awaitingConfirmation) {
    return 'warning';
  }
  switch (node.status) {
    case 'FAILED':
      return 'danger';
    case 'SUCCEEDED':
      return 'success';
    default:
      return 'info';
  }
}

/** 一句话摘要：先让人知道「这个任务有没有流程、停在哪」 */
export function flowSummary(nodes?: AigTaskFlowNodeVO[]): string {
  const list = nodes ?? [];
  if (!list.length) {
    return '这个任务没有流程节点（没有声明流程，或步骤还没登记）。';
  }
  const awaiting = list.filter((item) => item.awaitingConfirmation).length;
  const failed = list.filter((item) => item.status === 'FAILED').length;
  const pending = list.filter((item) => item.status === 'PLANNED' || item.status === 'RUNNING').length;
  const done = list.filter((item) => item.status === 'SUCCEEDED' || item.status === 'SKIPPED').length;
  return `共 ${list.length} 个节点：已完成 ${done}、待执行 ${pending}、等人工确认 ${awaiting}、失败 ${failed}。`;
}

/** 等待人工确认的节点（确认下拉的可选项） */
export function awaitingNodes(nodes?: AigTaskFlowNodeVO[]): AigTaskFlowNodeVO[] {
  return (nodes ?? []).filter((item) => item.awaitingConfirmation);
}

/**
 * 记录人工确认前的校验（与后端同口径，先把话说清楚再发请求）。
 *
 * @returns 通过返回 null；否则返回可显示的拒绝原因
 */
export function confirmValidation(
  nodes: AigTaskFlowNodeVO[] | undefined,
  nodeIndex: number | undefined,
  approvalRef?: string
): string | null {
  if (nodeIndex === undefined || nodeIndex === null) {
    return '请选择要确认的节点';
  }
  const node = (nodes ?? []).find((item) => item.nodeIndex === nodeIndex);
  if (!node) {
    return '该节点已不在列表里，请先刷新流程节点';
  }
  if (!node.awaitingConfirmation) {
    return '这个节点不在等待人工确认（已批准过，或它本身不是人工确认节点）';
  }
  if (!approvalRef || !approvalRef.trim()) {
    return '请填写批准引用（审批单号/工单号）：没有凭据的确认事后无法追溯';
  }
  return null;
}
