/**
 * 面试管理 域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P3 §2.3 / §3.3（docs/hr-talent/SPEC-P3-招聘流程闭环.md）
 * 2. 后端已实现类：RecruitInterviewVo / RecruitInterviewerVo
 *    + RecruitInterviewQueryBo / RecruitInterviewBo / InterviewCancelBo / InterviewFeedbackBo
 * 3. DDL：script/sql/hr_recruit.sql（hr_recruit_interview / hr_recruit_interviewer）
 *
 * 字段名 = DDL 列名去掉下划线后的 camelCase；bigint 一律用 string | number。
 * interviewerIds 提交与读回均为**数组**（后端另有只读 interviewerIdText 不外发）。
 */

/** 面试状态（hr_recruit_interview.status，字典 recruit_interview_status，对齐 InterviewStatusEnum） */
export type InterviewStatus = 'pending' | 'scheduled' | 'finished' | 'cancelled' | 'rescheduled';

/** 面试方式（hr_recruit_interview.method，字典 recruit_interview_method，对齐 InterviewMethodEnum） */
export type InterviewMethod = 'onsite' | 'video' | 'phone';

/** 面试结果（hr_recruit_interview.result，字典 recruit_interview_result，对齐 InterviewResultEnum） */
export type InterviewResult = 'pending' | 'pass' | 'fail' | 'reserve' | 'absent';

/** 面试参与人反馈状态（hr_recruit_interviewer.feedback_status，稳定编码） */
export type InterviewerFeedbackStatus = 'pending' | 'submitted' | 'waived';

/** 面试参与人视图（RecruitInterviewerVo，一名面试官一行） */
export interface HrInterviewerVO extends BaseEntity {
  /** 面试参与人ID */
  interviewerId?: string | number;
  /** 面试记录ID */
  interviewId?: string | number;
  /** 面试官用户ID */
  userId?: string | number;
  /** 面试官姓名快照 */
  userName?: string;
  /** 面试官当前昵称（@Translation 回填） */
  userNickName?: string;
  /** 面试官角色（lead主面/assist协同/hr/tech 等稳定编码） */
  interviewerRole?: string;
  /** 反馈状态（pending/submitted/waived） */
  feedbackStatus?: string;
  /** 反馈时间 */
  feedbackTime?: string;
  /** 面试官评分 */
  score?: number;
  /** 面试官个人意见/结论 */
  feedback?: string;
  /** 备注 */
  remark?: string;
}

/** 面试记录视图（RecruitInterviewVo） */
export interface HrInterviewVO extends BaseEntity {
  /** 面试记录ID */
  interviewId?: string | number;
  /** 应聘记录ID */
  applicationId?: string | number;
  /** 面试轮次（1 一面、2 二面、3 及以后为扩展轮次） */
  roundNo?: number;
  /** 计划面试时间 */
  scheduleTime?: string;
  /** 实际结束时间 */
  endTime?: string;
  /** 面试方式（字典 recruit_interview_method） */
  method?: string;
  /** 面试地点或线上链接 */
  location?: string;
  /** 面试状态（字典 recruit_interview_status） */
  status?: string;
  /** 面试状态中文兜底（后端只读 getter，字典未配置时仍可读） */
  statusLabel?: string;
  /** 面试评分（汇总） */
  score?: number;
  /** 面试结果编码（字典 recruit_interview_result） */
  result?: string;
  /** 面试结果标签（字典 recruit_interview_result） */
  resultLabel?: string;
  /** 面试汇总意见（多人意见见 interviewers） */
  feedback?: string;
  /** 反馈时间 */
  feedbackTime?: string;
  /** 取消原因 */
  cancelReason?: string;
  /** 备注（含改期/取消的操作留痕） */
  remark?: string;
  /** 面试官用户ID数组（收发对称） */
  interviewerIds?: (string | number)[];
  /** 面试官昵称，逗号分隔（@Translation 回填） */
  interviewerNames?: string;
  /** 面试官明细（每人一行，含个人意见与反馈状态） */
  interviewers?: HrInterviewerVO[];
  /** 应聘编号（只读关联带出） */
  applicationNo?: string;
  /** 候选人姓名（只读关联人才主档带出） */
  candidateName?: string;
  /** 岗位名称（只读关联岗位执行项带出） */
  jobName?: string;
  /** 关联月度计划任务ID（只读关联带出） */
  planItemId?: string | number;
  /** 应聘当前阶段编码（字典 recruit_candidate_stage） */
  currentStage?: string;
  /** 应聘当前阶段标签（字典 recruit_candidate_stage） */
  currentStageLabel?: string;
}

/** 面试检索条件（GET /recruit/interviews，对齐 RecruitInterviewQueryBo + PageQuery） */
export interface HrInterviewQuery extends PageQuery {
  /** 应聘记录ID */
  applicationId?: string | number;
  /** 面试轮次 */
  roundNo?: number;
  /** 面试状态（字典 recruit_interview_status） */
  status?: string;
  /** 面试结果（字典 recruit_interview_result） */
  result?: string;
  /** 面试方式（字典 recruit_interview_method） */
  method?: string;
  /** 面试官用户ID */
  interviewerUserId?: string | number;
  /** 计划面试时间起 */
  scheduleTimeBegin?: string;
  /** 计划面试时间止 */
  scheduleTimeEnd?: string;
}

/** 面试安排 / 改期表单（POST /recruit/interviews、PUT /recruit/interviews/{id}） */
export interface HrInterviewForm {
  interviewId?: string | number;
  /** 应聘记录ID（新增必填） */
  applicationId?: string | number;
  /** 面试轮次（必填，从 1 开始） */
  roundNo?: number;
  /** 计划面试时间（必填） */
  scheduleTime?: string;
  /** 实际结束时间 */
  endTime?: string;
  /** 面试方式（字典 recruit_interview_method） */
  method?: string;
  /** 面试地点或线上链接 */
  location?: string;
  /** 面试官用户ID数组（新增必填，最多 20 人） */
  interviewerIds?: (string | number)[];
  /** 改期原因（改期时填写，写入操作留痕） */
  changeReason?: string;
  remark?: string;
}

/** 面试取消入参（POST /recruit/interviews/{id}/cancel，对齐 InterviewCancelBo） */
export interface HrInterviewCancelForm {
  /** 取消原因（必填，最长 500） */
  cancelReason?: string;
}

/**
 * 面试反馈入参（POST /recruit/interviews/{id}/feedback，对齐 InterviewFeedbackBo）
 *
 * 当前登录用户必须是该面试的面试官；feedback 为该面试官的个人意见。
 * overallScore / conclusion 为可选：填写时后端同时更新面试记录的汇总结论。
 */
export interface HrInterviewFeedbackForm {
  /** 面试官评分 */
  score?: number;
  /** 面试官个人意见（必填） */
  feedback?: string;
  /** 面试官个人结论（字典 recruit_interview_result） */
  result?: string;
  /** 汇总评分（可选） */
  overallScore?: number;
  /** 汇总结论（可选） */
  conclusion?: string;
}
