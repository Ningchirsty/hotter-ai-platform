import { describe, expect, it } from 'vitest';
import {
  CAPABILITY_LABELS,
  capabilityLabel,
  capabilityOf,
  workflowDisplayName,
  workflowOptionLabel
} from './workflowLabels';
import type { CreativeWorkflowVO } from '@/api/creative/types';

/**
 * 出图能力/工作流中文口径的单测（v1 人工测试反馈：卡片与下拉里全是英文代号）。
 *
 * @author creative
 */
const WORKFLOWS: CreativeWorkflowVO[] = [
  { workflowCode: 'wf-t2i-qwen21', capabilityCode: 'T2I', published: true },
  { workflowCode: 'wf-i2i-qwen21', capabilityCode: 'I2I', published: true },
  { workflowCode: 'wf-edit-qwen21', capabilityCode: 'EDIT', published: true },
  { workflowCode: 'wf-bgremove-qwen21', capabilityCode: 'BGREMOVE', published: false, status: 'RETIRED' }
];

describe('capabilityLabel', () => {
  it('五个能力都有中文名（与 contracts 的 capabilities[].name 对齐）', () => {
    expect(capabilityLabel('T2I')).toBe('文生图');
    expect(capabilityLabel('I2I')).toBe('图生图');
    expect(capabilityLabel('EDIT')).toBe('指令改图');
    expect(capabilityLabel('BGREMOVE')).toBe('商品去背景');
    expect(capabilityLabel('WHITEBG')).toBe('白底图');
    expect(Object.keys(CAPABILITY_LABELS)).toHaveLength(5);
  });

  it('认不出的能力原样返回——难看但可发现，不抹成空白', () => {
    expect(capabilityLabel('NEWCAP')).toBe('NEWCAP');
    expect(capabilityLabel('')).toBe('');
    expect(capabilityLabel(null)).toBe('');
  });
});

describe('capabilityOf', () => {
  it('从工作流清单里查能力，不靠编码前缀猜', () => {
    expect(capabilityOf('wf-i2i-qwen21', WORKFLOWS)).toBe('I2I');
    expect(capabilityOf('wf-bgremove-qwen21', WORKFLOWS)).toBe('BGREMOVE');
  });

  it('清单里没有的工作流返回空串（不猜）', () => {
    expect(capabilityOf('wf-unknown-v9', WORKFLOWS)).toBe('');
    // 前缀像 i2i，但清单里没有这一条 → 不按前缀猜成图生图
    expect(capabilityOf('wf-i2i-qwen99', WORKFLOWS)).toBe('');
  });
});

describe('workflowDisplayName', () => {
  it('分镜卡片显示中文能力名', () => {
    expect(workflowDisplayName('wf-i2i-qwen21', WORKFLOWS)).toBe('图生图');
  });

  it('清单拿不到（接口失败）时回落成编码本身，而不是空白', () => {
    expect(workflowDisplayName('wf-i2i-qwen21', [])).toBe('wf-i2i-qwen21');
    expect(workflowDisplayName('wf-i2i-qwen21', null)).toBe('wf-i2i-qwen21');
  });

  it('没有出图能力时显示「—」', () => {
    expect(workflowDisplayName('', WORKFLOWS)).toBe('—');
    expect(workflowDisplayName(null, WORKFLOWS)).toBe('—');
  });
});

describe('workflowOptionLabel', () => {
  it('下拉项：中文名在前，代号与发布状态在括号里', () => {
    expect(workflowOptionLabel(WORKFLOWS[1])).toBe('图生图（wf-i2i-qwen21 · 已发布）');
    expect(workflowOptionLabel(WORKFLOWS[3])).toBe('商品去背景（wf-bgremove-qwen21 · RETIRED）');
  });

  it('没有能力编码时用工作流编码当名字，不产生空括号', () => {
    expect(workflowOptionLabel({ workflowCode: 'wf-x', published: true }))
      .toBe('wf-x（wf-x · 已发布）');
  });
});
