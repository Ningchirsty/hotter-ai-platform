import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

/**
 * 调用（唯一入口）与**试调（dryRun）**。
 *
 * <p><b>试调是"只看不花钱"的入口</b>：`POST /aigov/invoke/dryRun` 只做路由决策，
 * 不发起模型调用、不写审计、不扣配额。右侧「能力试调助手」就是用它。</p>
 *
 * <p><b>Q3 已补两个只读字段</b>：dryRun 现在会预判「真实调用是否需要审批」（`wouldRequireApproval`）
 * 与「调用人自己的额度状态」（`quotaStatus`）。但**预判 ≠ 校验**：dryRun 仍不去查
 * 「你手上有没有有效授权」，也不消耗额度。界面必须把这一点写清，否则"试调通过"会被当成"能调"。</p>
 */

/** 调用入参（试调只用前四个；prompt/payload 试调不需要） */
export interface AigInvokeForm {
  /** 能力编码 */
  capabilityCode?: string;
  /** 本次数据等级：PUBLIC / INTERNAL / RESTRICTED / STRICT */
  dataLevel?: string;
  /** 本次是否允许外发：不传=沿用策略；false=硬禁止外发 */
  allowExternal?: boolean;
  /** 场景编码（可空；用于场景指定收窄） */
  scenarioCode?: string;
  /** 单次预算（USD，可空） */
  maxCost?: number;
  prompt?: string;
  payload?: Record<string, any>;
  agentVersionId?: string | number;
  taskId?: string | number;
}

/** 调用/试调结果（对齐后端 AigInvokeVo） */
export interface AigInvokeVO {
  traceId?: string;
  /** 路由结论：MODEL / MANUAL / DENIED */
  decision?: string;
  modelId?: string | number;
  modelKey?: string;
  deploymentType?: string;
  invoker?: string;
  externalCall?: boolean;
  output?: string;
  pendingConfirm?: string[];
  reason?: string;
  errorCode?: string;
  reasonCode?: string;
  /** 路由逐条判定，含「哪些候选因何被排除」 */
  policyHits?: string[];
  latencyMs?: number;
  /**
   * 仅 dryRun 填充：真实调用时是否会被「调用审批」拦下。
   * 未返回（undefined）表示后端没给这个字段 —— 界面应说"未知"，不能说成"不需要审批"。
   */
  wouldRequireApproval?: boolean;
  /** 仅 dryRun 填充：调用人自己的额度状态（不消耗、不写账） */
  quotaStatus?: AigQuotaStatusVO;
}

/** 调用人的额度快照（对齐后端 AigQuotaStatusVo；只读、只回本人的） */
export interface AigQuotaStatusVO {
  /** 是否有生效中的限制（配了配额行且至少一条上限非空） */
  limited?: boolean;
  /** 当前是否已超限（任一有上限的周期已用 ≥ 上限） */
  exceeded?: boolean;
  /** 最紧的那个周期的剩余次数（下限 0）；不限时为 null */
  remaining?: number | null;
  dailyLimit?: number | null;
  dailyUsed?: number;
  monthlyLimit?: number | null;
  monthlyUsed?: number;
}

/** 只做路由决策预览：不真调用、不写审计、不扣配额 */
export function dryRun(data: AigInvokeForm): AxiosPromise<AigInvokeVO> {
  return request({
    url: '/aigov/invoke/dryRun',
    method: 'post',
    data
  });
}
