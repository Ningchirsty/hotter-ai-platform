import { describe, expect, it } from 'vitest';
import type { AigTaskFlowNodeVO } from '@/api/aigov/task/types';
import {
  awaitingNodes,
  confirmValidation,
  flowNodeStatusText,
  flowNodeTagType,
  flowNodeTypeLabel,
  flowSummary
} from './flow';

/**
 * 任务流程节点展示口径测试（设计 §6.5：人工确认的挂起与恢复）。
 *
 * <p>最要命的一条是"等人工确认"被显示成别的：如果界面把挂起的节点显示成"执行中"或"已通过"，
 * 人就不会去批；反过来把已批准的显示成"等确认"，人会以为自己的批准没生效、反复点。</p>
 */
describe('task flow presentation', () => {
  const node = (extra: Partial<AigTaskFlowNodeVO>): AigTaskFlowNodeVO => ({
    nodeIndex: 0,
    nodeType: 'HUMAN_CONFIRM',
    status: 'RUNNING',
    ...extra
  });

  it('类型码 → 文案；没登记身份就如实说，不猜一个类型', () => {
    expect(flowNodeTypeLabel('HUMAN_CONFIRM')).toBe('人工确认');
    expect(flowNodeTypeLabel('CONDITION')).toBe('条件判断');
    expect(flowNodeTypeLabel(undefined)).toBe('未登记身份');
    expect(flowNodeTypeLabel('PARALLEL')).toBe('PARALLEL');
  });

  it('★ 等人工确认：按后端下发的 awaitingConfirmation 判定，不听 status 字面量', () => {
    expect(flowNodeStatusText(node({ awaitingConfirmation: true }))).toBe('等待人工确认');
    // 同一个 RUNNING，awaiting=false 说明只是执行中（不是等人）
    expect(flowNodeStatusText(node({ awaitingConfirmation: false }))).toBe('执行中');
    expect(flowNodeTagType(node({ awaitingConfirmation: true }))).toBe('warning');
  });

  it('★ 人工确认通过要把凭据带出来（不然看不出是谁批的）', () => {
    expect(flowNodeStatusText(node({ status: 'SUCCEEDED', approvalRef: 'appr-9' }))).toBe('已批准（appr-9）');
    expect(flowNodeStatusText(node({ status: 'SUCCEEDED' }))).toBe('已批准（无凭据）');
    expect(flowNodeStatusText({ nodeType: 'STEP', status: 'SUCCEEDED' })).toBe('已通过');
  });

  it('失败/跳过/待执行各自可读；失败带分类', () => {
    expect(flowNodeStatusText({ status: 'FAILED', errorCode: 'TIMEOUT' })).toBe('已失败：TIMEOUT');
    expect(flowNodeStatusText({ status: 'FAILED' })).toBe('已失败');
    expect(flowNodeStatusText({ status: 'SKIPPED' })).toBe('已跳过');
    expect(flowNodeStatusText({ status: 'PLANNED' })).toBe('待执行');
    expect(flowNodeStatusText(undefined)).toBe('—');
    expect(flowNodeTagType({ status: 'FAILED' })).toBe('danger');
  });

  it('★ 摘要先回答"有没有流程、停在哪儿"', () => {
    expect(flowSummary([])).toContain('没有流程节点');
    expect(flowSummary(undefined)).toContain('没有流程节点');
    const summary = flowSummary([
      { status: 'SUCCEEDED' },
      { status: 'SKIPPED' },
      { status: 'PLANNED' },
      { status: 'RUNNING', awaitingConfirmation: true },
      { status: 'FAILED' }
    ]);
    expect(summary).toContain('共 5 个节点');
    expect(summary).toContain('已完成 2');
    expect(summary).toContain('等人工确认 1');
    expect(summary).toContain('失败 1');
  });

  it('确认下拉只列等待确认的节点', () => {
    const list = [
      { nodeIndex: 0, status: 'SUCCEEDED' },
      { nodeIndex: 1, status: 'RUNNING', awaitingConfirmation: true },
      { nodeIndex: 2, status: 'PLANNED' }
    ];
    expect(awaitingNodes(list).map((item) => item.nodeIndex)).toEqual([1]);
    expect(awaitingNodes(undefined)).toEqual([]);
  });

  it('★ 确认前校验：节点必选、必须真的在等待、批准引用必填', () => {
    const list = [
      { nodeIndex: 0, status: 'RUNNING', awaitingConfirmation: true },
      { nodeIndex: 1, status: 'SUCCEEDED', awaitingConfirmation: false }
    ];
    expect(confirmValidation(list, undefined, 'appr-1')).toContain('请选择');
    expect(confirmValidation(list, 0, '')).toContain('批准引用');
    expect(confirmValidation(list, 0, '   ')).toContain('批准引用');
    expect(confirmValidation(list, 1, 'appr-1')).toContain('不在等待人工确认');
    expect(confirmValidation(list, 9, 'appr-1')).toContain('请先刷新');
    expect(confirmValidation(list, 0, 'appr-1')).toBeNull();
  });
});
