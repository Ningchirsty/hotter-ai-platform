import { describe, expect, it } from 'vitest';
import {
  DIRTY_CHOICES,
  adoptUnsavedMessage,
  beforeUnloadShouldWarn,
  needsGuard,
  unsavedMessage,
} from './dirtyGuard';

/**
 * 未保存保护的三条纪律（设计 D06）。
 *
 * <p>最要命的一类回归是"某条路径静默丢弃本地改动"——它不报错，人只是发现刚写的东西没了。
 * 因此这里钉住：选择恒为三项，且没有会直接继续的选项。</p>
 */
describe('dirtyGuard', () => {
  it('只有三个选择，且覆盖 保存/放弃/返回', () => {
    expect(DIRTY_CHOICES.map(c => c.key)).toEqual(['SAVE', 'DISCARD', 'CANCEL']);
    expect(DIRTY_CHOICES).toHaveLength(3);
  });

  it('每个选择都有可见文案，不能出现"静默继续"的选项', () => {
    for (const choice of DIRTY_CHOICES) {
      expect(choice.label.length).toBeGreaterThan(0);
    }
    const labels = DIRTY_CHOICES.map(c => c.label).join('|');
    expect(labels).toContain('保存');
    expect(labels).toContain('放弃');
    expect(labels).toContain('返回');
  });

  it('未保存才拦截；已保存不打扰', () => {
    expect(needsGuard(true)).toBe(true);
    expect(needsGuard(false)).toBe(false);
  });

  it('提示写清动作、基准修订与三个选择', () => {
    const message = unsavedMessage('关闭编辑器');
    expect(message).toContain('关闭编辑器');
    expect(message).toContain('已保存的修订');
    expect(message).toContain('保存并继续');
    expect(message).toContain('放弃本地改动并继续');
    expect(message).toContain('返回继续编辑');
    expect(adoptUnsavedMessage()).toContain('采纳提案');
  });

  it('浏览器关闭/刷新只在有未保存改动时拦截', () => {
    expect(beforeUnloadShouldWarn(true)).toBe(true);
    expect(beforeUnloadShouldWarn(false)).toBe(false);
  });
});
