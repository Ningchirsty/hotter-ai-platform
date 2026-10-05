/**
 * 把后端「视觉基因不能锁定的原因」对到表单里的具体字段（v1 人工测试反馈）。
 *
 * <p><b>为什么要这一步</b>：后端 `VisualDnaSchema.validate` 的措辞其实已经把字段说清了
 * （「主色未设置」「产品占比区间颠倒：下限 80 大于上限 60」），但它是一串**字符串**——
 * 页面上只能读，不能点。用户看完仍要自己在下面 15 个表单项里找「主色」在哪。
 * 这一步把"哪句话对应哪个输入框"变成可点击的动作。</p>
 *
 * <p><b>为什么按关键字而不是按顺序</b>：`issues` 是后端决定顺序与措辞的，
 * 前端按位置猜（第 1 条=主色）会在后端加一条校验时立刻错位。关键字对上就给动作，
 * 对不上就**不给动作**（只显示原文）——宁可不给按钮，也不要把人送到无关的输入框。</p>
 *
 * <p><b>纯函数</b>：映射口径要被单测钉住，且不依赖任何运行时状态。</p>
 *
 * @author creative
 */

/** 基因表单里能被 issue 指向的字段 */
export interface DnaIssueTarget {
  /** 表单字段名（与 `CreativeDnaForm` 一致，用于定位与聚焦） */
  field: string;
  /** 字段的中文名（按钮文案用） */
  label: string;
  /**
   * 同一句 issue 关联的第二个字段。
   *
   * 「产品占比区间颠倒」同时关乎下限与上限，只高亮一个会让人以为改一个就行。
   */
  also?: string;
}

/**
 * 关键字 → 字段。顺序重要：先匹配更具体的词
 * （「背景色」要在「主色」之前判，否则「背景色未设置」会被主色规则吃掉——
 * 两者字面上都含"色"）。
 */
const RULES: Array<{ keyword: string; target: DnaIssueTarget }> = [
  { keyword: '背景色', target: { field: 'colorBg', label: '背景色' } },
  { keyword: '辅色', target: { field: 'colorSecondary', label: '辅色' } },
  { keyword: '点缀色', target: { field: 'colorAccent', label: '点缀色' } },
  { keyword: '主色', target: { field: 'colorPrimary', label: '主色' } },
  { keyword: '风格关键词', target: { field: 'styleKeywords', label: '风格关键词' } },
  { keyword: '饱和度', target: { field: 'saturation', label: '饱和度' } },
  { keyword: '对比度', target: { field: 'contrastLevel', label: '对比度' } },
  { keyword: '留白', target: { field: 'whitespaceLevel', label: '留白' } },
  { keyword: '光线类型', target: { field: 'lightingType', label: '光线类型' } },
  { keyword: '光位', target: { field: 'lightingDir', label: '光位' } },
  {
    keyword: '产品占比',
    target: { field: 'productRatioMin', label: '产品占比', also: 'productRatioMax' }
  }
];

/**
 * 取一句 issue 对应的表单字段。
 *
 * @param issue 后端给的 issue 原文
 * @returns 对应字段；认不出返回 null（页面只显示原文，不给按钮）
 */
export function dnaIssueTarget(issue?: string | null): DnaIssueTarget | null {
  const text = (issue || '').trim();
  if (!text) {
    return null;
  }
  for (const rule of RULES) {
    if (text.includes(rule.keyword)) {
      return rule.target;
    }
  }
  // 「缺少 schema 标识」这类是结构问题，没有对应输入框——如实不给动作
  return null;
}

/**
 * 这一组 issue 里有哪些是「按参考图实测」能给出值的。
 *
 * <p>用途：把"推荐"按钮的承诺说准。参考图能实测配色/饱和度/对比度/留白/产品占比/光线/场景，
 * 但**测不出**风格关键词、禁忌词、字体风格（这三项是审美与品牌语义，后端 `skipped` 里也这么说）。
 * 如果 issues 全是测不出来的项，就不该把这个按钮摆在最显眼的位置骗人点。</p>
 *
 * @param issues issue 原文列表
 * @returns 是否存在"可实测"的项
 */
export function hasMeasurableIssue(issues?: string[] | null): boolean {
  return (issues || []).some((issue) => {
    const target = dnaIssueTarget(issue);
    if (!target) {
      return false;
    }
    return target.field !== 'styleKeywords';
  });
}
