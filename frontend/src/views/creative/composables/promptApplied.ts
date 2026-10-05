import { computed, type ComputedRef } from 'vue';

/**
 * 「这版提示词用到了哪些维度」的中文口径（v1 人工测试反馈）。
 *
 * <p><b>为什么要改</b>：DNA 页与项目页原先直接把后端派生结果里的字段名原样拼出来
 * （`styleKeywords、colors、lighting、whitespaceLevel、brief.mustShow(2)`）。
 * 那是**给代码看的名字**：一半是英文，另一半还带括号计数，读起来像编号。
 * 用户要的是"这版提示词用了基因的哪几样、用了品牌要求的哪几条"，
 * 而不是一份字段清单。</p>
 *
 * <p><b>为什么是纯函数</b>：两处（基因页的派生预览、项目页出图框的预填说明）都要用同一套口径，
 * 抄两份迟早漂移；而且"哪个码翻成哪句话"是要被钉住的展示口径，得有单测。</p>
 *
 * <p><b>认不出的码不吞掉</b>：后端将来加了新维度而前端没跟上时，原样显示那个码——
 * 显示得难看是可发现的，静默丢掉会让"这版提示词明明用到了它"变得查不出来。</p>
 *
 * @author creative
 */

/** 固定维度的中文名（键与 `DnaPromptBuilder.Prompt#applied()` 里的码一致） */
const FIXED_LABELS: Record<string, string> = {
  'screenText(屏文案)': '本屏屏文案',
  'module.visualRules': '该屏的模块视觉表达',
  styleKeywords: '基因·风格关键词',
  colors: '基因·配色',
  lighting: '基因·光线',
  whitespaceLevel: '基因·留白',
  productRatio: '基因·产品占比',
  sceneType: '基因·场景',
  'saturation/contrastLevel': '基因·饱和度与对比度',
  avoidKeywords: '基因·禁忌词',
  'avoidKeywords(默认)': '基因·禁忌词（默认值）'
};

/** 带条数的维度：`brief.mustShow(2)` → 「品牌必显信息 2 条」 */
const COUNTED_LABELS: Record<string, string> = {
  'brief.mustShow': '品牌必显信息',
  'brief.mainPush': '品牌主推卖点',
  'brief.forbiddenWords': '品牌禁用词'
};

/**
 * 把一个 applied 码翻成中文短语。
 *
 * @param code 后端给的码（如 `colors`、`brief.mustShow(2)`）
 * @returns 中文短语；认不出时原样返回
 */
export function appliedLabel(code: string): string {
  const text = (code || '').trim();
  if (!text) {
    return '';
  }
  const fixed = FIXED_LABELS[text];
  if (fixed) {
    return fixed;
  }
  const counted = /^([a-zA-Z.]+)\((\d+)\)$/.exec(text);
  if (counted) {
    const name = COUNTED_LABELS[counted[1]];
    if (name) {
      return `${name} ${counted[2]} 条`;
    }
  }
  // 认不出：原样显示（宁可难看，也不要让"用到了某维度"这件事消失）
  return text;
}

/**
 * 把 applied 码列表拼成一句话用的中文串。
 *
 * @param codes 后端给的码列表（可为空）
 * @returns 形如 `基因·配色、品牌必显信息 2 条`；空列表返回空串
 */
export function appliedText(codes?: string[] | null): string {
  const labels = (codes || []).map(appliedLabel).filter(Boolean);
  // 去重但保持顺序：同一个维度不该在一个句子里出现两次
  return Array.from(new Set(labels)).join('、');
}

/**
 * 响应式版本的 {@link appliedText}（模板里直接 `{{ applied }}` 用）。
 *
 * @param codes 码列表的响应式引用
 * @returns 中文串
 */
export function useAppliedText(codes: ComputedRef<string[] | undefined | null>): ComputedRef<string> {
  return computed(() => appliedText(codes.value));
}
