/**
 * 岗位执行项类型定义
 * 对齐 script/sql/hr_recruit.sql 的 hr_recruit_job。接口契约见 SPEC-P2 §3.4。
 *
 * 说明（已与后端 RecruitJobVo / RecruitJobAssignBo 核对）：
 * - hr_recruit_job 已补 assistant_ids / first_interviewer_id / second_interviewer_id 列。
 * - **收发对称**：assistantIds 读回与提交均为 Long[] 数组（后端另有只读的 assistantIdText
 *   仅用于翻译，@JsonIgnore 不外发）；同时返回 assistantNames（逗号分隔昵称串）、
 *   firstInterviewerName、secondInterviewerName、ownerName（@Translation 回填）。
 * - 岗位状态 draft/open/paused/closed 暂无字典类型（无 recruit_job_status），页面本地常量渲染。
 */

/** 岗位执行项（hr_recruit_job） */
export interface HrJobVO extends BaseEntity {
  /** 岗位执行项ID（主键） */
  jobId?: string | number;
  /** 岗位编号（业务编号，唯一） */
  jobNo?: string;
  /** 来源招聘需求ID */
  demandId?: string | number;
  /** 关联月度计划任务ID */
  planItemId?: string | number;
  /** 岗位名称 */
  jobName?: string;
  /** 公司（平台部门）ID */
  companyDeptId?: string | number;
  /** 公司名称快照 */
  companyName?: string;
  /** 用工部门ID */
  useDeptId?: string | number;
  /** 用工部门名称快照 */
  useDeptName?: string;
  /** 岗位职级（字典编码） */
  jobLevel?: string;
  /** 工作城市 */
  workCity?: string;
  /** 岗位职责 */
  responsibility?: string;
  /** 任职要求 */
  qualification?: string;
  /** 薪资低值 */
  salaryMin?: number;
  /** 薪资高值 */
  salaryMax?: number;
  /** 薪资周期（month/year/day 等稳定编码） */
  salaryPeriod?: string;
  /** 招聘形式（字典 recruit_mode） */
  recruitMode?: string;
  /** 招聘期限标准天数 */
  standardDays?: number;
  /** 岗位招聘人数（非负整数） */
  recruitCount?: number;
  /** 岗位负责（招聘负责人）用户ID */
  ownerId?: string | number;
  /** 发布日期 */
  publishDate?: string;
  /** 关闭日期 */
  closeDate?: string;
  /** 岗位状态（draft/open/paused/closed 等稳定编码） */
  status?: string;
  /** 乐观锁版本号 */
  version?: number;
  /** 备注 */
  remark?: string;
  /** 展示用字段（@Translation 回填）：缺失时回退显示 ownerId */
  ownerName?: string;
  /** 协助人用户ID，**读回与提交同为 Long[] 数组**（收发对称） */
  assistantIds?: (string | number)[];
  /** 协助人昵称，逗号分隔（@Translation 回填） */
  assistantNames?: string;
  /** 一面面试官用户ID */
  firstInterviewerId?: string | number;
  /** 一面面试官昵称（@Translation 回填） */
  firstInterviewerName?: string;
  /** 二面面试官用户ID */
  secondInterviewerId?: string | number;
  /** 二面面试官昵称（@Translation 回填） */
  secondInterviewerName?: string;
}

/** 岗位新增/编辑表单（POST /recruit/jobs、PUT /recruit/jobs/{id}） */
export interface HrJobForm {
  jobId?: string | number;
  demandId?: string | number;
  planItemId?: string | number;
  jobName?: string;
  companyDeptId?: string | number;
  companyName?: string;
  useDeptId?: string | number;
  useDeptName?: string;
  jobLevel?: string;
  workCity?: string;
  responsibility?: string;
  qualification?: string;
  salaryMin?: number;
  salaryMax?: number;
  salaryPeriod?: string;
  recruitMode?: string;
  standardDays?: number;
  recruitCount?: number;
  ownerId?: string | number;
  publishDate?: string;
  /** 乐观锁版本号：编辑时必传 */
  version?: number;
  remark?: string;
}

/** 岗位动作（POST /recruit/jobs/{id}/actions/{action}） */
export type JobAction = 'close' | 'reopen';

/**
 * 岗位分配表单（POST /recruit/jobs/{id}/assign）
 * 与后端 RecruitJobAssignBo 对齐：{ ownerId, assistantIds: Long[], firstInterviewerId, secondInterviewerId }。
 * assistantIds 收发对称，提交与读回均为**数组**（后端另有只读 assistantIdText，@JsonIgnore 不外发）。
 */
export interface HrJobAssignForm {
  jobId: string | number;
  /** 招聘负责人用户ID（对应 hr_recruit_job.owner_id） */
  ownerId?: string | number;
  /** 协助人用户ID数组（对应 hr_recruit_job.assistant_ids，提交为 Long[]） */
  assistantIds?: (string | number)[];
  /** 一面面试官用户ID */
  firstInterviewerId?: string | number;
  /** 二面面试官用户ID */
  secondInterviewerId?: string | number;
}

/** 岗位查询条件（GET /recruit/jobs） */
export interface HrJobQuery extends PageQuery {
  jobNo?: string;
  jobName?: string;
  demandId?: string | number;
  planItemId?: string | number;
  companyDeptId?: string | number;
  useDeptId?: string | number;
  ownerId?: string | number;
  status?: string;
  recruitMode?: string;
  workCity?: string;
  params?: Record<string, any>;
}
