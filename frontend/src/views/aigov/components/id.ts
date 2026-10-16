/**
 * 长 ID 折叠与复制（走读 §5.5「长 ID 与原始 JSON 默认折叠」）。
 *
 * <p><b>为什么必须折叠</b>：审计 / 绑定 / 任务列表里直出 19 位雪花 ID，
 * 一屏下来全是数字，人眼既读不出"这是哪一行"，也复制不准（少一位就查不到数据）。
 * 折叠成"头 6 位…尾 6 位"后，人靠头尾就能对上；完整值仍可通过悬停/点击复制拿到。</p>
 */

/**
 * 折叠 ID：保留头尾，中间用省略号。
 *
 * @param id   原始 ID（字符串或数字；数字请传字符串——19 位雪花 ID 超出 2^53 会被截断）
 * @param keep 头尾各保留几位（默认 6）
 * @returns 折叠后的文本；短到不需要折叠时原样返回
 */
export function foldId(id?: string | number | null, keep = 6): string {
  if (id === null || id === undefined) {
    return '';
  }
  const text = String(id).trim();
  if (!text) {
    return '';
  }
  if (text.length <= keep * 2 + 1) {
    return text;
  }
  return `${text.slice(0, keep)}…${text.slice(-keep)}`;
}

/**
 * 是否需要折叠（用于决定要不要显示复制按钮/悬停标题）。
 *
 * @param id   原始 ID
 * @param keep 头尾各保留几位
 * @returns 需要折叠返回 true
 */
export function needsFold(id?: string | number | null, keep = 6): boolean {
  if (id === null || id === undefined) {
    return false;
  }
  return String(id).trim().length > keep * 2 + 1;
}
