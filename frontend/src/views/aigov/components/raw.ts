/**
 * 原始字段（JSON / 长文本）的展示口径（走读 §5.5「长 ID 与原始 JSON 默认折叠」）。
 *
 * <p><b>为什么必须默认折叠</b>：审计与配置页里常有一段原始 JSON 或一长串 ID。
 * 直接摊在列表里会把人的注意力从"结论"拽到"字段"，而且大多数人根本不读它——
 * 但真出问题时又必须有处可查。所以默认收成一行，点开才展开。</p>
 */

/**
 * 文本是否像 JSON（用于决定"格式化展开"还是"原样显示"）。
 *
 * @param text 文本（可空）
 * @returns 像 JSON 对象/数组返回 true
 */
export function looksLikeJson(text?: string): boolean {
  const trimmed = (text || '').trim();
  if (!trimmed) {
    return false;
  }
  if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) {
    return false;
  }
  try {
    JSON.parse(trimmed);
    return true;
  } catch {
    return false;
  }
}

/**
 * 展开用的文本：JSON 就缩进美化，否则原样返回。
 *
 * <p><b>非法 JSON 不能被丢掉</b>：它恰恰是最需要看的那种值——美化失败时把原文交出来，
 * 而不是显示一句"格式错误"让人无处可查。</p>
 *
 * @param text 原始文本（可空）
 * @returns 可直接展示的文本
 */
export function prettyOrRaw(text?: string): string {
  const trimmed = (text || '').trim();
  if (!trimmed) {
    return '（空）';
  }
  if (!looksLikeJson(trimmed)) {
    return trimmed;
  }
  try {
    return JSON.stringify(JSON.parse(trimmed), null, 2);
  } catch {
    return trimmed;
  }
}

/**
 * 收起时那一行的预览（单行、限长，保证列表不被撑开）。
 *
 * @param text   原始文本（可空）
 * @param maxLen 最大字符数（默认 60）
 * @returns 预览文本
 */
export function collapsedPreview(text?: string, maxLen = 60): string {
  const oneLine = (text || '').replace(/\s+/g, ' ').trim();
  if (!oneLine) {
    return '（空）';
  }
  if (oneLine.length <= maxLen) {
    return oneLine;
  }
  return `${oneLine.slice(0, maxLen)}…（共 ${oneLine.length} 字符）`;
}
