import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

/**
 * 「谁在引用这个能力」（影响面；Q6 已拍板：改策略/停用/删除前强制先看）。
 *
 * <p>返回三类引用（Agent 版本声明 / 历史任务 / 训练台草稿），**一次请求拿全** ——
 * 确认框要一次说清"改了会影响谁"，分三个接口会让确认框等三次。</p>
 */

/** 引用类型 */
export type AigCapabilityRefType = 'AGENT_VERSION' | 'TASK' | 'DRAFT';

/** 一条引用（对齐后端 AigCapabilityReferenceVo） */
export interface AigCapabilityReferenceVO {
  refType?: AigCapabilityRefType | string;
  /** 人类可读的一行说明（确认框与表格直接用） */
  refLabel?: string;
  agentId?: string | number;
  agentCode?: string;
  agentName?: string;
  agentVersionId?: string | number;
  agentVersion?: string;
  /** 发布状态（AGENT_VERSION 才有；在跑的版本风险最高） */
  releaseStatus?: string;
  /** 计数（TASK / DRAFT） */
  count?: number;
  /** 补充说明：声明来源（provider_capability / config_json）等 */
  detail?: string;
}

/** 查询引用某个能力的 Agent 版本 / 任务 / 草稿 */
export function listCapabilityReferences(
  capabilityCode: string
): AxiosPromise<AigCapabilityReferenceVO[]> {
  return request({
    url: '/aigov/capability/references',
    method: 'get',
    params: { capabilityCode }
  });
}
