import { describe, expect, it } from 'vitest';
import type { DpDetailPageVersionVO } from '@/api/creative/types';
import { LONG_PAGE_MIN_RATIO, finalDetailCaption, pickFinalDetailVersion } from './finalDetailImage';

/**
 * 「最终产品详情图是哪一版」的判定（第 36 轮，v1 反馈 AI生产中心 1.2）。
 *
 * <p>口径在 {@link pickFinalDetailVersion} 的注释里；这里钉住四件事：
 * 长图才认、多版取最新、不可预览的不要、没有就返回 null（页面如实说"还没有"）。</p>
 */

/** 造一版（默认是一张能预览的长图） */
function version(over: Partial<DpDetailPageVersionVO>): DpDetailPageVersionVO {
  return { id: '1', version: 1, kind: 'V08', kindDesc: '机排版 V0.8',
    pageWidth: 750, pageHeight: 4551, previewable: true, ...over } as DpDetailPageVersionVO;
}

describe('最终产品详情图的判定（第 36 轮）', () => {
  it('只看长图：方图/海报/精修小图都不算', () => {
    const picked = pickFinalDetailVersion([
      version({ id: 'a', version: 4, kind: 'V10_FINAL', pageWidth: 750, pageHeight: 120 }),
      version({ id: 'b', version: 2, kind: 'V10_FINAL', pageWidth: 512, pageHeight: 512 }),
      version({ id: 'c', version: 1, kind: 'V08', pageWidth: 750, pageHeight: 4551 })
    ]);
    expect(picked?.id).toBe('c');
  });

  it('多版长图取版本号最大的那一版（不看数组顺序）', () => {
    const older = version({ id: 'old', version: 1 });
    const newer = version({ id: 'new', version: 5 });
    expect(pickFinalDetailVersion([newer, older])?.id).toBe('new');
    expect(pickFinalDetailVersion([older, newer])?.id).toBe('new');
  });

  it('不能预览的版本不选（显示了也只是破图）', () => {
    expect(pickFinalDetailVersion([
      version({ id: 'broken', version: 9, previewable: false }),
      version({ id: 'ok', version: 2 })
    ])?.id).toBe('ok');
    expect(pickFinalDetailVersion([version({ id: 'broken', version: 9, previewable: false })])).toBeNull();
  });

  it('没有长图 / 空列表 / 尺寸缺失 → null（不猜一张顶上）', () => {
    expect(pickFinalDetailVersion([])).toBeNull();
    expect(pickFinalDetailVersion(null)).toBeNull();
    expect(pickFinalDetailVersion([version({ pageWidth: undefined, pageHeight: undefined })])).toBeNull();
    expect(pickFinalDetailVersion([version({ pageWidth: 0, pageHeight: 0 })])).toBeNull();
    // 比例刚好等于阈值算长图（>= 而不是 >）
    const ratio = LONG_PAGE_MIN_RATIO;
    expect(pickFinalDetailVersion([version({ pageWidth: 100, pageHeight: 100 * ratio })])).not.toBeNull();
    expect(pickFinalDetailVersion([version({ pageWidth: 100, pageHeight: 100 * ratio - 1 })])).toBeNull();
  });

  it('展示文案：版本 + 形态 + 尺寸 + 状态', () => {
    expect(finalDetailCaption(version({ version: 1, kindDesc: '机排版 V0.8', statusDesc: '已通过' })))
      .toBe('v1 · 机排版 V0.8 · 750×4551 · 已通过');
    expect(finalDetailCaption(null)).toBe('');
  });
});
