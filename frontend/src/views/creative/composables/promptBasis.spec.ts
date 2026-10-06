import { describe, expect, it } from 'vitest';
import { pickPromptBasis } from './promptBasis';
import type { DpVisualDnaVO } from '@/api/creative/types';

/**
 * 「出图依据是哪一版基因」的单测（R38-3 / P0-3）。
 *
 * 这条的最贵误解是：锁定 v1 → 改字段 → 自动新建 v2 → **页面显示 v2 但出图仍按 v1**，
 * 人以为"改了没生效"。所以这里钉住的正是"v1 是依据、v2 只是草稿"这一判断，
 * 以及"一版都没锁时不许把某一版说成依据"。
 *
 * @author creative
 */
function version(n: number, locked: boolean, id?: number): DpVisualDnaVO {
  return { id: id ?? n * 100, taskId: 1, version: n, locked } as DpVisualDnaVO;
}

describe('pickPromptBasis', () => {
  it('已有锁定版：依据是锁定那一版，即使页面正在显示更高的草稿版', () => {
    const result = pickPromptBasis([version(2, false), version(1, true)], version(2, false));
    expect(result.version).toBe(1);
    expect(result.id).toBe(100);
    expect(result.editingIsBasis).toBe(false);
    expect(result.note).toContain('v2');
    expect(result.note).toContain('锁定它之后');
  });

  it('页面正在显示的就是锁定版：明确说"你编辑的就是这一版"', () => {
    const result = pickPromptBasis([version(1, true), version(2, false)], version(1, true));
    expect(result.version).toBe(1);
    expect(result.editingIsBasis).toBe(true);
    expect(result.note).toContain('正在编辑的就是这一版');
  });

  it('多个锁定版（历史异常数据）：取版本号最大的那个，不靠数组顺序', () => {
    const result = pickPromptBasis([version(3, true), version(5, true), version(1, true)], version(3, true));
    expect(result.version).toBe(5);
  });

  it('一版都没锁：**不许**把最新一版说成出图依据', () => {
    const result = pickPromptBasis([version(1, false), version(2, false)], version(2, false));
    expect(result.version).toBeNull();
    expect(result.id).toBeNull();
    expect(result.editingIsBasis).toBe(false);
    expect(result.note).toContain('还没有锁定任何一版');
  });

  it('一个版本都没有：如实说没有版本，而不是空白', () => {
    const result = pickPromptBasis([], null);
    expect(result.version).toBeNull();
    expect(result.note).toContain('还没有基因版本');
  });

  it('versions 传空值时按"没有版本"处理（页面初次加载）', () => {
    expect(pickPromptBasis(undefined, undefined).version).toBeNull();
    expect(pickPromptBasis(null, null).version).toBeNull();
  });

  it('locked 字段缺失或非 true 都不算锁定（不能把 undefined 当已锁）', () => {
    const noFlag = { id: 7, taskId: 1, version: 4 } as DpVisualDnaVO;
    const result = pickPromptBasis([noFlag], noFlag);
    expect(result.version).toBeNull();
  });
});
