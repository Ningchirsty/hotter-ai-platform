/**
 * 结果反馈与运营指标类型（对应后端 `/aigov/feedback`、`/aigov/operations`）。
 *
 * @author ai-gov
 */

/** 一条反馈 */
export interface AigFeedbackVO {
  feedbackId?: string | number;
  taskId?: string | number;
  resultId?: string | number;
  agentVersionId?: string | number;
  knowledgeVersionId?: string | number;
  /** 反馈原因（USEFUL/FACT_ERROR/CITATION_MISMATCH/MISSING_SOURCE/FORMAT_MISMATCH/FLOW_INTERRUPTED） */
  reasonCode?: string;
  /** 处理结论（PENDING/CONFIRMED/REJECTED） */
  verdict?: string;
  /** 处理动作（ADD_KNOWLEDGE/CHANGE_RULE/ADD_CASE/CLOSE） */
  actionCode?: string;
  detailRef?: string;
  reporterId?: string | number;
  handledBy?: string | number;
  handledAt?: string;
  createTime?: string;
  remark?: string;
}

/** 提交反馈入参 */
export interface AigFeedbackSubmitForm {
  taskId?: string | number;
  resultId?: string | number;
  agentVersionId?: string | number;
  knowledgeVersionId?: string | number;
  reasonCode?: string;
  detailRef?: string;
}

/** 处理反馈入参 */
export interface AigFeedbackHandleForm {
  /** CONFIRMED / REJECTED */
  verdict?: string;
  /** 确认问题时必填 */
  actionCode?: string;
  remark?: string;
}

/** 运营指标 */
export interface AigOperationsMetricsVO {
  completedTasks?: number;
  confirmedResults?: number;
  adoptionRate?: number;
  firstPassRate?: number;
  reworkRate?: number;
  citationValidRate?: number;
  policyBlocked?: number;
  modelCost?: number | string;
}
