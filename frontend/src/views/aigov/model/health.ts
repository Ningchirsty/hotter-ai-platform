/**
 * 模型连通性（健康）状态的展示口径。
 *
 * <h3>为什么单独抽出来（M-003 ①）</h3>
 * <p>原实现在列表页里写的是
 * <code>healthStatus === 'HEALTHY' ? '连通' : '不通'</code>，
 * 也就是**把除 HEALTHY 之外的一切都显示成「不通」**。这在生产上是错的：库里真实存在</p>
 * <ul>
 *   <li><code>UNHEALTHY</code>——确实不通；</li>
 *   <li><code>UNKNOWN</code>——<b>语义是"不知道"</b>，而路由**仍会放行**它
 *       （见 AigRouteServiceImpl：只排除 DOWN/UNHEALTHY）；</li>
 *   <li><code>NULL</code>——从未测过，路由同样放行（"没测过 ≠ 不可用"是刻意的取舍）。</li>
 * </ul>
 * <p>把 UNKNOWN 显示成「不通」，会让运维以为该模型已被排除，
 * 而实际上它仍在参与路由——<b>展示与真实行为相反，比不显示更坏</b>。</p>
 *
 * <h3>另一个坑：词汇有过三套</h3>
 * <p>写入端 `ModelConnectionTester` 落的是 `HEALTHY`/`UNHEALTHY`，
 * 而接口注释与前端类型曾写 `UP/DOWN/DEGRADED`。因此这里**同时容忍两套词汇**，
 * 避免历史数据或将来改动让展示悄悄变错。</p>
 */

/** 健康状态的展示口径。 */
export type HealthTone = 'success' | 'danger' | 'info';

export interface HealthDisplay {
  /** 标签文案 */
  label: string;
  /** Element Plus 标签色调 */
  tone: HealthTone;
}

/** 判为"确实不通"的字面量（与后端路由的排除口径一致：DOWN / UNHEALTHY）。 */
const UNHEALTHY_VALUES = ['DOWN', 'UNHEALTHY'];

/** 判为"身体健康"的字面量（兼容旧词汇 UP）。 */
const HEALTHY_VALUES = ['HEALTHY', 'UP'];

/**
 * 把后端 `health_status` 映射为展示文案与色调。
 *
 * <p>口径与后端**路由判定**保持一致，这是本函数存在的意义：</p>
 * <ul>
 *   <li>HEALTHY/UP → 「连通」（success）；</li>
 *   <li>DOWN/UNHEALTHY → 「不通」（danger）——这些确实会被路由排除；</li>
 *   <li>UNKNOWN → 「未测」（info）——**不是「不通」**，路由仍放行；</li>
 *   <li>空/未提供 → 「未测试」（info）——从未测过，路由仍放行；</li>
 *   <li>其它未识别值 → 原样展示（info），不猜成「不通」，便于发现新词汇。</li>
 * </ul>
 *
 * @param raw 后端返回的 health_status（可为空）
 * @returns 展示文案与色调
 */
export function describeHealth(raw?: string | null): HealthDisplay {
  const value = (raw ?? '').trim().toUpperCase();
  if (!value) {
    return { label: '未测试', tone: 'info' };
  }
  if (HEALTHY_VALUES.includes(value)) {
    return { label: '连通', tone: 'success' };
  }
  if (UNHEALTHY_VALUES.includes(value)) {
    return { label: '不通', tone: 'danger' };
  }
  if (value === 'UNKNOWN') {
    // 「不知道」不是「不通」：路由对 UNKNOWN 是放行的
    return { label: '未测', tone: 'info' };
  }
  // 未识别的词汇原样露出，而不是塞进「不通」——否则新增状态会被静默误报为故障
  return { label: raw ?? value, tone: 'info' };
}

/**
 * 该健康状态是否会导致模型被路由排除。
 *
 * <p>用于表格上给出"会不会被排除"的准确提示，避免运维凭标签颜色猜。</p>
 *
 * @param raw 后端返回的 health_status（可为空）
 * @returns true = 会被路由排除
 */
export function isExcludedByHealth(raw?: string | null): boolean {
  const value = (raw ?? '').trim().toUpperCase();
  return UNHEALTHY_VALUES.includes(value);
}
