/**
 * 调用人均配额类型（对齐后端 AigUserQuotaVo / AigUserQuotaUsageVo）。
 *
 * 单位是「调用次数」，不是钱：调用审计里的 cost 常为「未知而非免费」，
 * 用经常未知的数字做配额会算出一本对不上的账。
 */

/** 配额行 */
export interface AigUserQuotaVO extends BaseEntity {
  quotaId?: string | number;
  userId?: string | number;
  /** 调用人账号（冗余存的一份；以 userId 为准） */
  userName?: string;
  /** 每自然日调用次数上限；null = 不限 */
  dailyLimit?: number | null;
  /** 每自然月调用次数上限；null = 不限 */
  monthlyLimit?: number | null;
  /** 0正常 1停用（停用=不参与判定，等同于不限） */
  status?: string;
  remark?: string;
}

/** 配额查询 */
export interface AigUserQuotaQuery extends PageQuery {
  userId?: string | number;
  userName?: string;
  status?: string;
  params?: Record<string, any>;
}

/** 配额保存入参（一人一行：按 userId upsert；上限传 null = 不限） */
export interface AigUserQuotaForm {
  quotaId?: string | number;
  userId?: string | number;
  userName?: string;
  dailyLimit?: number | null;
  monthlyLimit?: number | null;
  status?: string;
  remark?: string;
}

/** 用量（已用 / 上限 / 周期起点） */
export interface AigUserQuotaUsageVO {
  userId?: string | number;
  userName?: string;
  dailyLimit?: number | null;
  dailyUsed?: number;
  dailyFrom?: string;
  monthlyLimit?: number | null;
  monthlyUsed?: number;
  monthlyFrom?: string;
  /** 是否有生效中的限制 */
  limited?: boolean;
  /** 当前是否已超限 */
  exceeded?: boolean;
}
