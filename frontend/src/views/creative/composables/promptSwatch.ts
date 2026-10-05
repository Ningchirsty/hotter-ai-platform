/**
 * 提示词里的色号 → 可展示的"色块 + 色值"（v1 人工测试反馈 视觉基因 1.2）。
 *
 * <p><b>原文</b>：「具体的编号对应的是相应的颜色。不要展示编号最好。」——提示词正文里现在直接写
 * `主色 #C8443C、辅色 #F2E8E6、点缀色 #8C6239、背景 #F5F5F3`：对人不友好（看不出是什么颜色），
 * 而色号本身**不能删**——这段文本是出图时真正下发的内容，也是"这版基因派生出什么"的证据。</p>
 *
 * <p><b>所以只改"怎么显示"，不动文本</b>：这一层把文本切成片段，遇到 `#RRGGBB` 就切出一个色块片段
 * （色值放在 title 里、悬停可见），其余原样是文字。落库与下发的文本一个字都不变，
 * 页面上另有「查看原文」把原始文本照原样显示（可复制）。</p>
 *
 * <p>纯函数：切分口径必须能被单测钉住，且不依赖运行时状态。</p>
 *
 * @author creative
 */

/** 提示词片段：普通文字，或一个色号 */
export interface PromptSegment {
  /** 片段类型：text=原样文字；color=色号 */
  kind: 'text' | 'color';
  /** 要显示的内容（color 片段里是规范化后的 `#RRGGBB`） */
  text: string;
}

/**
 * 色号形态：`#` + 3/4/6/8 位十六进制（`#abc` / `#abcd` / `#aabbcc` / `#aabbccdd`）。
 *
 * <p>用全局正则配合 `exec` 而不是 `split`：需要知道每处匹配的**位置**，才能把前后文字也切成片段。</p>
 */
const HEX = /#[0-9a-fA-F]{3,8}\b/g;

/** 合法长度（3/4/6/8）——`#12345` 这种不是色号，按普通文字处理 */
const VALID_LENGTHS = new Set([4, 5, 7, 9]); // 含 '#' 号

/**
 * 把提示词切成片段。
 *
 * @param text 提示词原文（可空）
 * @returns 片段数组；空输入返回空数组
 */
export function splitPromptColors(text?: string | null): PromptSegment[] {
  const raw = text || '';
  if (!raw) {
    return [];
  }
  const out: PromptSegment[] = [];
  let last = 0;
  HEX.lastIndex = 0;
  let match = HEX.exec(raw);
  while (match) {
    const token = match[0];
    // 长度不合法就当普通文字，别把 "#12345" 渲染成一个乱猜的颜色
    if (!VALID_LENGTHS.has(token.length)) {
      match = HEX.exec(raw);
      continue;
    }
    if (match.index > last) {
      out.push({ kind: 'text', text: raw.slice(last, match.index) });
    }
    out.push({ kind: 'color', text: token });
    last = match.index + token.length;
    match = HEX.exec(raw);
  }
  if (last < raw.length) {
    out.push({ kind: 'text', text: raw.slice(last) });
  }
  return out;
}

/**
 * 这一段提示词里用到几个色号（去重，按首次出现顺序）。
 *
 * @param text 提示词原文
 * @returns 色号列表（形如 `['#C8443C', '#F2E8E6']`）
 */
export function promptColorTokens(text?: string | null): string[] {
  const seen: string[] = [];
  for (const seg of splitPromptColors(text)) {
    if (seg.kind === 'color' && !seen.includes(seg.text)) {
      seen.push(seg.text);
    }
  }
  return seen;
}
