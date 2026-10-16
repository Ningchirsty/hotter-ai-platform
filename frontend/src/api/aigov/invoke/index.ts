import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

/**
 * 调用（唯一入口）与**试调（dryRun）**。
 *
 * <p><b>试调是"只看不花钱"的入口</b>：`POST /aigov/invoke/dryRun` 只做路由决策，
 * 不发起模型调用、不写审计、不扣配额。右侧「能力试调助手」就是用它。</p>
 *
 * <p><b>一个必须写进界面的边界</b>：dryRun **不校验调用授权（审批）与人均配额** ——
 * 那两道只在真实调用时拦（见 `AigInvokeServiceImpl.invoke` 的第 6、7 步）。
 * 所以"试调通过"不等于"能调"；助手回答里必须带这句，否则会误导使用者。
 * （P1 会给 dryRun 补 `wouldRequireApproval` / `quotaStatus` 两个只读字段。）</p>
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
}

/** 只做路由决策预览：不真调用、不写审计、不扣配额 */
export function dryRun(data: AigInvokeForm): AxiosPromise<AigInvokeVO> {
  return request({
    url: '/aigov/invoke/dryRun',
    method: 'post',
    data
  });
}
