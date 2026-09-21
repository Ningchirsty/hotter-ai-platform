import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrApplicationForm,
  HrApplicationQuery,
  HrApplicationTransferForm,
  HrApplicationTransitionForm,
  HrApplicationVO,
  HrArrivalRegisterForm,
  HrCandidateCreateForm,
  HrCandidateQuery,
  HrCandidateVO,
  HrNoArrivalForm,
  HrOfferRegisterForm,
  HrPhoneViewForm,
  HrStageLogVO,
  HrTalentPrecheckForm,
  HrTalentPrecheckVO
} from './types';

/**
 * 候选人 / 应聘记录 域接口封装
 *
 * 路径与 docs/hr-talent/SPEC-P3-招聘流程闭环.md §2.1 / §2.2 逐字一致，并与后端
 * CandidateController（/recruit/candidates）、RecruitApplicationController（/recruit/applications）
 * 完全对齐。权限串：recruit:candidate:list/query/add/edit/transfer/stage/phone-view。
 */

/* ============================ 候选人（一人一档视图） ============================ */

/** 分页查询候选人（存在应聘记录的人才视图，电话/邮箱脱敏） */
export function listCandidate(query: HrCandidateQuery): AxiosPromise<PageResult<HrCandidateVO>> {
  return request({
    url: '/recruit/candidates',
    method: 'get',
    params: query
  });
}

/** 候选人重复预检（不落库，返回强/中/弱匹配摘要） */
export function precheckCandidate(data: HrTalentPrecheckForm): AxiosPromise<HrTalentPrecheckVO> {
  return request({
    url: '/recruit/candidates/precheck',
    method: 'post',
    data: data
  });
}

/** 新增候选人（查重后创建主档 + 可选应聘记录；复用已有主档时传 talentId） */
export function addCandidate(data: HrCandidateCreateForm): AxiosPromise<string | number> {
  return request({
    url: '/recruit/candidates',
    method: 'post',
    data: data
  });
}

/**
 * 记录用途后查看电话明文（服务端写敏感操作审计）
 * 注意：必须在页面弹窗中强制填写 purpose 后方可调用。
 */
export function viewCandidatePhone(id: string | number, data: HrPhoneViewForm): AxiosPromise<string> {
  return request({
    url: `/recruit/candidates/${id}/phone-view`,
    method: 'post',
    data: data
  });
}

/* ============================ 应聘记录与阶段流转 ============================ */

/** 分页查询应聘记录 */
export function listApplication(query: HrApplicationQuery): AxiosPromise<PageResult<HrApplicationVO>> {
  return request({
    url: '/recruit/applications',
    method: 'get',
    params: query
  });
}

/** 查询应聘记录详情 */
export function getApplication(id: string | number): AxiosPromise<HrApplicationVO> {
  return request({
    url: '/recruit/applications/' + id,
    method: 'get'
  });
}

/** 为已有主档创建应聘记录 */
export function addApplication(data: HrApplicationForm) {
  return request({
    url: '/recruit/applications',
    method: 'post',
    data: data
  });
}

/** 更新应聘记录业务快照（带 version 乐观锁） */
export function updateApplication(data: HrApplicationForm) {
  return request({
    url: '/recruit/applications/' + data.applicationId,
    method: 'put',
    data: data
  });
}

/**
 * 阶段流转（核心）
 * toStage 与 result 二选一；进入一/二面/待背调/待录用/待报到前必须满足 SPEC-P3 §3.2 的前置条件。
 */
export function transitionApplication(id: string | number, data: HrApplicationTransitionForm) {
  return request({
    url: `/recruit/applications/${id}/transition`,
    method: 'post',
    data: data
  });
}

/** 转移招聘负责人 / 岗位 */
export function transferApplication(id: string | number, data: HrApplicationTransferForm) {
  return request({
    url: `/recruit/applications/${id}/transfer`,
    method: 'post',
    data: data
  });
}

/** 查询阶段历史（只追加，只读） */
export function listStageLogs(id: string | number): AxiosPromise<HrStageLogVO[]> {
  return request({
    url: `/recruit/applications/${id}/stage-logs`,
    method: 'get'
  });
}

/** 登记邀约结果与计划报到日期 */
export function registerOffer(id: string | number, data: HrOfferRegisterForm) {
  return request({
    url: `/recruit/applications/${id}/offer`,
    method: 'post',
    data: data
  });
}

/** 登记实际报到（后端同一事务内计入月度计划任务并刷新状态） */
export function registerArrival(id: string | number, data: HrArrivalRegisterForm) {
  return request({
    url: `/recruit/applications/${id}/arrival`,
    method: 'post',
    data: data
  });
}

/** 登记未报到及原因 */
export function registerNoArrival(id: string | number, data: HrNoArrivalForm) {
  return request({
    url: `/recruit/applications/${id}/no-arrival`,
    method: 'post',
    data: data
  });
}
