/**
 * 招聘期限标准 类型（对齐后端 RecruitStandardVo / RecruitStandardBo）。
 *
 * 业务键：岗位名称 + 公司 + 生效日期。公司留空表示「集团通用」。
 */

/** 列表/详情视图对象 */
export interface RecruitStandardVO {
  standardId?: string | number;
  jobName?: string;
  /** 公司（平台部门）ID；为空表示集团通用 */
  companyDeptId?: string | number | null;
  companyName?: string | null;
  /** 是否集团通用（后端回填） */
  groupWide?: boolean;
  standardDays?: number;
  effectiveDate?: string | null;
  expiryDate?: string | null;
  /** active 生效 / inactive 停用 */
  status?: string;
  statusLabel?: string;
  remark?: string;
  createBy?: string | number;
  createByName?: string;
  createTime?: string;
  updateTime?: string;
}

/** 新增/编辑表单 */
export interface RecruitStandardForm {
  standardId?: string | number;
  jobName?: string;
  companyDeptId?: string | number | null;
  companyName?: string | null;
  standardDays?: number;
  effectiveDate?: string | null;
  expiryDate?: string | null;
  status?: string;
  remark?: string;
}

/** 查询条件 */
export interface RecruitStandardQuery {
  pageNum?: number;
  pageSize?: number;
  jobName?: string;
  companyDeptId?: string | number | null;
  status?: string;
  /** 只看集团通用条目 */
  groupOnly?: boolean;
}
