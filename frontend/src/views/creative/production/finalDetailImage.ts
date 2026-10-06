import type { DpDetailPageVersionVO } from '@/api/creative/types';

/**
 * 「最终产品详情图」到底是哪一版（第 36 轮，v1 反馈 AI生产中心 1.2）。
 *
 * <p><b>原文</b>：「预览中的是最终的出图还是上传的产品图还是参考图并不明确，全都放到一起，
 * <b>已出图的方向应该是最终的产品详情图</b>」——也就是在「已出图」这里，人要看的是**最终那张详情长图**，
 * 不是模型候选、也不是产品图/参考图。第 26 轮已把"这是什么图"标清楚（预览弹窗那栏），
 * 这一轮补上"最终长图本身在哪儿看得到"。</p>
 *
 * <p><b>为什么判定要做成纯函数</b>：它是"显示哪一张"的唯一依据，判错的表现是**指着一张方图说这是长图**
 * （或者反过来把真长图藏起来）。抽出来就能直接单测，不必起浏览器。</p>
 *
 * <p><b>判定口径</b>（写死在这里，别处不要再判一遍）：</p>
 * <ol>
 *   <li>只认**能预览**的版本（后端已经给了 `previewable`，拿不到预览图的版本显示了也只是个破图）；</li>
 *   <li>只认**长图**：高 / 宽 ≥ {@link LONG_PAGE_MIN_RATIO}（详情长图是"一屏一屏往下排"的产物，
 *       750×4551 这种比例；而终版精修、海报、方图都不满足）；</li>
 *   <li>多版长图时取**版本号最大**的那一版（最新）。</li>
 * </ol>
 *
 * <p><b>不猜</b>：一条都不满足就返回 `null`——页面如实说"还没有长图排版产物"，并把人指到
 * 「详情页与审核」，而不是随便挑一张图顶上。</p>
 *
 * @author creative
 */

/** 长图的最低高宽比（低于这个值不算"详情长图"） */
export const LONG_PAGE_MIN_RATIO = 3;

/**
 * 从详情页版本里挑出"最终产品详情图"那一版。
 *
 * @param versions 详情页版本（后端倒序或正序都行，判定只看版本号）
 * @returns 该版本；没有长图时返回 null
 */
export function pickFinalDetailVersion(
  versions?: DpDetailPageVersionVO[] | null
): DpDetailPageVersionVO | null {
  const longPages = (versions || []).filter((version) => {
    if (version?.previewable === false) {
      return false;
    }
    const width = Number(version?.pageWidth ?? 0);
    const height = Number(version?.pageHeight ?? 0);
    if (!width || !height) {
      return false;
    }
    return height / width >= LONG_PAGE_MIN_RATIO;
  });
  if (!longPages.length) {
    return null;
  }
  return longPages.reduce((best, current) =>
    Number(current.version ?? 0) > Number(best.version ?? 0) ? current : best
  );
}

/**
 * 这一版的"是什么"一行字：版本 + 形态 + 尺寸。
 *
 * @param version 详情页版本
 * @returns 形如「v1 · 机排版 V0.8 · 750×4551」
 */
export function finalDetailCaption(version?: DpDetailPageVersionVO | null): string {
  if (!version) {
    return '';
  }
  const parts: string[] = [];
  if (version.version != null) {
    parts.push('v' + version.version);
  }
  if (version.kindDesc) {
    parts.push(version.kindDesc);
  } else if (version.kind) {
    parts.push(version.kind);
  }
  if (version.pageWidth && version.pageHeight) {
    parts.push(`${version.pageWidth}×${version.pageHeight}`);
  }
  if (version.statusDesc) {
    parts.push(version.statusDesc);
  }
  return parts.join(' · ');
}
