/**
 * 调用授权审批类型（对齐后端 AigCallApprovalVo / AigCallApprovalSweepVo）。
 *
 * 一张单子有两个时间，别混：
 * - expireTime：审批时限——PENDING 超过它就不能再批准（会被置为已超时）；
 * - validUntil：授权有效期止——批准时才算出来，`validUntil > now` 才是「授权在用」。
 */

/** 审批单 / 授权行 */
export interface AigCallApprovalVO extends BaseEntity {
  approvalId?: string | number;
  requesterId?: string | number;
  requesterName?: string;
  /** 授权粒度之一：能力 */
  capabilityCode?: string;
  /** 授权粒度之一：数据等级 */
  dataLevel?: string;
  /** 申请理由 */
  reason?: string;
  /** PENDING 待审批 / APPROVED 已批准 / REJECTED 已驳回 / EXPIRED 已超时 / CANCELLED 已撤回 */
  status?: string;
  /** 审批时限（超过它不得再批准） */
  expireTime?: string;
  approverId?: string | number;
  approverName?: string;
  decidedAt?: string;
  /** 审批意见（驳回必填） */
  decisionRemark?: string;
  /** 授权有效期止（为空表示这张单从未获批） */
  validUntil?: string;
}

/** 查询条件 */
export interface AigCallApprovalQuery extends PageQuery {
  capabilityCode?: string;
  dataLevel?: string;
  requesterId?: string | number;
  approverId?: string | number;
  status?: string;
}

/** 提交申请 */
export interface AigCallApprovalForm {
  capabilityCode?: string;
  dataLevel?: string;
  reason?: string;
}

/** 批准/驳回 */
export interface AigCallApprovalDecideForm {
  /** true 批准 / false 驳回 */
  approved?: boolean;
  /** 审批意见（驳回必填） */
  remark?: string;
}

/** 超时扫描结果 */
export interface AigCallApprovalSweepVO {
  expired?: number;
  scannedAt?: string;
}
