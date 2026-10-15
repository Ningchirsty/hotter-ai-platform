import { describe, expect, it } from 'vitest';
import { AUX_TABS, STUDIO_STEPS, revisionText, saveStateLabel, saveStateTagType } from './layout';

/**
 * 训练台布局口径测试（设计 §6.3、§5.4 图一）。
 */
describe('studio layout', () => {
  it('左侧七个步骤与设计一致且顺序固定', () => {
    expect(STUDIO_STEPS.map(item => item.label)).toEqual([
      '岗位与目标',
      '输入与成果',
      '知识',
      '工具与流程',
      '行为规则',
      '测试',
      '发布'
    ]);
  });

  it('右侧辅助区三个可切换页签', () => {
    expect(AUX_TABS.map(item => item.label)).toEqual(['培训助手', '测试预览', '修订差异']);
  });

  it('保存状态文案：保存中 > 未保存 > 已保存（保存中优先，避免闪回"已保存"）', () => {
    expect(saveStateLabel(true, true)).toBe('保存中');
    expect(saveStateLabel(true, false)).toBe('有未保存改动');
    expect(saveStateLabel(false, false)).toBe('已保存');
  });

  it('保存状态标签色：保存中=灰、未保存=橙、已保存=绿', () => {
    expect(saveStateTagType(true, true)).toBe('info');
    expect(saveStateTagType(true, false)).toBe('warning');
    expect(saveStateTagType(false, false)).toBe('success');
  });

  it('修订号：没有修订号显示 —，不显示 0', () => {
    expect(revisionText(3)).toBe('#3');
    expect(revisionText(0)).toBe('#0');
    expect(revisionText(undefined)).toBe('—');
  });
});
