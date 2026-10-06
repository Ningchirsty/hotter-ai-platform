import type { DpVisualDnaVO } from '@/api/creative/types';

/**
 * 「出图依据是哪一版基因」的唯一口径（R38-3 修复，P0-3）。
 *
 * <p><b>修的是什么误解</b>：出图永远按**已锁定**那一版基因派生提示词
 * （后端 `CreativeDnaServiceImpl#activeEntity`：有锁定版用锁定版，否则用最新一版），
 * 而页面顶部一直显示的是**当前编辑的那一版**。于是出现这条最容易造成大返工的误解：</p>
 * <ol>
 *   <li>锁定 v1 → 改字段 → 自动新建 v2 草稿 → 页面显示 v2；</li>
 *   <li>人以为"现在按 v2 出图"，其实出图仍按 v1；</li>
 *   <li>出图结果与"我改的东西"对不上，看起来像功能坏了。</li>
 * </ol>
 *
 * <p><b>为什么判定要做成纯函数</b>：它决定页面上"出图依据 vN"这个数字对不对，
 * 判错就等于把上面那条误解又还回去。抽出来能直接单测，不必起浏览器。</p>
 *
 * <p><b>怎么判（与后端同一口径，别处不要再判一遍）</b>：
 * 优先取 `locked === true` 的那一版；一版都没锁时**没有出图依据**——
 * 后端这时用的是"最新一版"，但那不是"依据"，如实返回 `null` 并说明"尚未锁定"，
 * 而不是把某一版说成依据。</p>
 */

/** 出图依据的判定结果 */
export interface PromptBasis {
  /** 出图依据的版本号；尚未锁定时为 null */
  version: number | null;
  /** 出图依据那一版的 id；尚未锁定时为 null */
  id: string | number | null;
  /** 页面正在编辑的这一版就是出图依据（不需要额外提示"改了没生效"） */
  editingIsBasis: boolean;
  /** 给页面直接用的一行说明（不含"出图依据"四个字，由页面配标签） */
  note: string;
}

/**
 * 判「出图依据」是哪一版。
 *
 * @param versions   全部基因版本（顺序无关，判定只看 locked/version）
 * @param displayed  页面当前正在显示/编辑的那一版（可空）
 * @returns 判定结果；没有版本时 version 与 id 都是 null
 */
export function pickPromptBasis(
  versions?: DpVisualDnaVO[] | null,
  displayed?: DpVisualDnaVO | null
): PromptBasis {
  const all = versions || [];
  const locked = all.filter((item) => item?.locked === true);
  if (!locked.length) {
    return {
      version: null,
      id: null,
      editingIsBasis: false,
      note: all.length
        ? '还没有锁定任何一版——出图会在你锁定某一版之后，按那一版派生提示词。'
        : '这个项目还没有基因版本。'
    };
  }
  const basis = locked.reduce((best, current) =>
    Number(current.version ?? 0) > Number(best.version ?? 0) ? current : best
  );
  const shownVersion = displayed?.version ?? null;
  const editingIsBasis = shownVersion != null && Number(shownVersion) === Number(basis.version);
  return {
    version: basis.version ?? null,
    id: basis.id ?? null,
    editingIsBasis,
    note: editingIsBasis
      ? '你正在编辑的就是这一版；锁定之后它就不会再被改动。'
      : `你正在编辑的是 v${shownVersion ?? '?'}（草稿）——锁定它之后，出图才会改用这一版。`
  };
}
