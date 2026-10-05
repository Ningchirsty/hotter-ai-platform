/**
 * 视觉方向卡片上「取舍维度」的中文口径（v1 人工测试反馈：方向卡片全是英文/编号）。
 *
 * <p><b>为什么要有这张表</b>：方向卡片的策略明细直接按 `strategy` 的键渲染，
 * 键就是后端存的字段名——页面上于是出现 `background / scene / lighting / composition /
 * mood / productRatio / referenceSceneType / dnaBasis` 这一串英文标识符。
 * 那是**给代码看的名字**，设计同事读的是「场景、光线、背景、构图、调性」。</p>
 *
 * <p><b>为什么单独一个纯函数</b>：这张表是展示口径，会被单测钉住；
 * 而且"认不出的键怎么办"必须只有一个答案（原样显示，见下）。</p>
 *
 * <p><b>顺序也是口径</b>：原先按 JSON 里的书写顺序渲染，等于让存储顺序决定阅读顺序。
 * 这里按"看一张画面时人真正关心的次序"排：先场景与光线，再背景与构图，最后调性、
 * 占比与依据。表里没有的键**排在最后并原样显示**——将来后端加维度时它会露出来，
 * 而不是被静默丢掉。</p>
 *
 * @author creative
 */

/** 已知取舍维度的中文名 */
export const DIRECTION_STRATEGY_LABELS: Record<string, string> = {
  scene: '场景',
  lighting: '光线',
  background: '背景色',
  composition: '构图',
  mood: '情绪/调性',
  productRatio: '产品占比',
  referenceSceneType: '参考场景类型',
  dnaBasis: '基因依据'
};

/** 展示顺序（只列已知维度；未知维度按原顺序追加在后面） */
export const DIRECTION_STRATEGY_ORDER = [
  'scene',
  'lighting',
  'background',
  'composition',
  'mood',
  'productRatio',
  'referenceSceneType',
  'dnaBasis'
];

/** 不当作"取舍"展示的元字段（结构标识与差异清单本身另有展示位置） */
const META_KEYS = ['schema', 'differences'];

/**
 * 取一个方向要展示的取舍维度键（中文顺序优先，未知键追加在后）。
 *
 * @param strategy 方向的策略明细（可为空）
 * @returns 维度键（已去掉 schema / differences）
 */
export function directionStrategyKeys(strategy?: Record<string, unknown> | null): string[] {
  const keys = Object.keys(strategy || {}).filter((key) => !META_KEYS.includes(key));
  const known = DIRECTION_STRATEGY_ORDER.filter((key) => keys.includes(key));
  const unknown = keys.filter((key) => !DIRECTION_STRATEGY_ORDER.includes(key));
  return [...known, ...unknown];
}

/**
 * 一个取舍维度的中文标签。
 *
 * @param key 维度键（如 `lighting`）
 * @returns 中文标签；认不出时原样返回键（显示得难看是可发现的，静默丢掉不是）
 */
export function directionStrategyLabel(key: string): string {
  return DIRECTION_STRATEGY_LABELS[key] || key;
}

/**
 * 取一个维度的展示值（策略里的原始值统一成字符串；缺值给空串而不是 "undefined"）。
 *
 * @param strategy 策略明细
 * @param key      维度键
 * @returns 展示值
 */
export function directionStrategyValue(
  strategy: Record<string, unknown> | null | undefined,
  key: string
): string {
  const value = strategy ? strategy[key] : undefined;
  if (value == null) {
    return '';
  }
  return typeof value === 'string' ? value : JSON.stringify(value);
}
