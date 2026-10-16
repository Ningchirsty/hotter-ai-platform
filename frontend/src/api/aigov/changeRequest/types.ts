/**
 * 高风险变更单（Q1C 完整版）类型，对齐后端 AigChangeRequestVo / AigChangeRequestBo。
 *
 * <p><b>批准即生效</b>：批准接口返回的是**处理后的单子**——status=APPLIED 表示已生效，
 * CONFLICTED 表示**未生效**（与现场冲突或执行失败，原因在 resultRemark）。页面必须如实区分，
 * 不能把 CONFLICTED 显示成"成功"。</p>
 */

/** 变更类型 */
export type AigChangeType = 'POLICY_CREATE' | 'POLICY_UPDATE' | 'POLICY_DELETE';

/** 变更单状态 */
export type AigChangeStatus = 'PENDING' | 'APPLIED' | 'REJECTED' | 'CANCELLED' | 'CONFLICTED';

/** 变更单视图 */
export interface AigChangeRequestVO extends BaseEntity {
  changeRequestId?: string | number;
  capabilityCode?: string;
  changeType?: AigChangeType | string;
  changeTypeLabel?: string;
  targetRef?: string | number;
  /** 变更前快照（JSON 文本） */
  beforeJson?: string;
  /** 变更后目标值（JSON 文本） */
  afterJson?: string;
  reason?: string;
  status?: AigChangeStatus | string;
  statusLabel?: string;
  requesterId?: string | number;
  requesterName?: string;
  approverId?: string | number;
  approverName?: string;
  decidedAt?: string;
  decisionRemark?: string;
  appliedAt?: string;
  /** 执行结果说明（冲突/失败原因） */
  resultRemark?: string;
}

/** 申请入参（目标值用显式字段，便于审批人逐项对比） */
export interface AigChangeRequestForm {
  capabilityCode?: string;
  changeType?: AigChangeType | string;
  targetRef?: string | number;
  dataLevel?: string;
  allowExternal?: string;
  preferredDeployment?: string;
  requireApproval?: string;
  fallbackToManual?: string;
  status?: string;
  remark?: string;
  reason?: string;
}

/** 查询条件 */
export interface AigChangeRequestQuery extends PageQuery {
  capabilityCode?: string;
  /** 状态过滤（后端字段名是 filterStatus） */
  filterStatus?: string;
}
