import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrPhoneViewForm,
  HrTalentEducationForm,
  HrTalentEducationVO,
  HrTalentFollowUpForm,
  HrTalentFollowUpQuery,
  HrTalentFollowUpVO,
  HrTalentPrecheckForm,
  HrTalentPrecheckVO,
  HrTalentProfileChangeVO,
  HrTalentProfileDetailVO,
  HrTalentProfileForm,
  HrTalentProfileQuery,
  HrTalentProfileTagForm,
  HrTalentProfileVO,
  HrTalentProjectForm,
  HrTalentProjectVO,
  HrTalentTagVO,
  HrTalentWorkForm,
  HrTalentWorkVO
} from './types';

/**
 * 人才档案 域接口封装（人才主档 + 教育/工作/项目经历 + 人才标签 + 跟进 + 电话明文）
 *
 * 路径与后端已实现 Controller 逐字一致：
 * - TalentProfileController：/talent/profiles（列表/详情/预检/新增/编辑/删除/归档/变更历史/电话明文）
 * - TalentExperienceController：/talent/profiles/{id}/educations|works|projects
 * - TalentTagController：/talent/profiles/{id}/tags（读取用 PERM_PROFILE_LIST，更新用 PERM_PROFILE_EDIT）
 * - TalentFollowUpController：/talent/profiles/{id}/follow-ups
 *
 * 权限串：talent:profile:list/query/add/edit/archive/phone-view、recruit:candidate:add（预检）。
 * 人才导出（talent:profile:export）见 `@/api/hrtalent/export`。
 */

/* ============================ 人才主档 ============================ */

/** 分页查询人才主档（后端自动套人才可见范围，电话/邮箱脱敏） */
export function listProfile(query: HrTalentProfileQuery): AxiosPromise<PageResult<HrTalentProfileVO>> {
  return request({
    url: '/talent/profiles',
    method: 'get',
    params: query
  });
}

/** 查询人才主档详情（含出生日期、期望薪资、合并信息等） */
export function getProfile(id: string | number): AxiosPromise<HrTalentProfileDetailVO> {
  return request({
    url: '/talent/profiles/' + id,
    method: 'get'
  });
}

/**
 * 人才重复预检（POST /talent/profiles/precheck，权限 recruit:candidate:add）
 * 不落库，返回强/中/弱三级匹配摘要。
 */
export function precheckProfile(data: HrTalentPrecheckForm): AxiosPromise<HrTalentPrecheckVO> {
  return request({
    url: '/talent/profiles/precheck',
    method: 'post',
    data: data
  });
}

/** 新增人才主档（服务端入库前查重，强/中匹配拒绝静默创建） */
export function addProfile(data: HrTalentProfileForm): AxiosPromise<string | number> {
  return request({
    url: '/talent/profiles',
    method: 'post',
    data: data
  });
}

/** 更新人才主档（带 version 乐观锁，关键字段写变更历史） */
export function updateProfile(data: HrTalentProfileForm) {
  return request({
    url: '/talent/profiles/' + data.talentId,
    method: 'put',
    data: data
  });
}

/** 逻辑删除人才主档（仅无应聘记录可删） */
export function delProfile(ids: string | number | Array<string | number>) {
  return request({
    url: '/talent/profiles/' + ids,
    method: 'delete'
  });
}

/** 归档人才主档（reason 为 query 参数，可为空） */
export function archiveProfile(id: string | number, reason?: string) {
  return request({
    url: `/talent/profiles/${id}/archive`,
    method: 'post',
    params: { reason }
  });
}

/** 查询人才关键字段变更历史 */
export function listProfileChanges(
  id: string | number,
  query?: PageQuery
): AxiosPromise<PageResult<HrTalentProfileChangeVO>> {
  return request({
    url: `/talent/profiles/${id}/changes`,
    method: 'get',
    params: query
  });
}

/**
 * 查看人才电话明文（人才侧端点，必填用途，服务端先写审计再返发明文）
 *
 * 路径与权限：`POST /talent/profiles/{id}/phone-view`（权限 `talent:profile:phone-view`，对齐
 * TalentProfileController#phoneView 与 HrTalentConstants.PERM_PROFILE_PHONE_VIEW）。
 *
 * <p><b>两个端点并存，按页面归属选择</b>：</p>
 * <ul>
 *   <li>人才侧本端点（本函数）：**不要求该人才存在应聘记录**，人才档案页/人才池成员均可用；</li>
 *   <li>候选人侧 `POST /recruit/candidates/{id}/phone-view`（权限 recruit:candidate:phone-view）：
 *       服务层会 assertCandidate（要求存在应聘记录），仅候选人跟进页使用。</li>
 * </ul>
 *
 * purpose 为空时服务端会先写 denied 审计再抛中文业务异常（R.fail，HTTP 200 + JSON 体），
 * 页面仍应做非空校验以避免无谓请求，同时正常展示服务端返回的业务错误。
 */
export function viewTalentPhone(id: string | number, data: HrPhoneViewForm): AxiosPromise<string> {
  return request({
    url: `/talent/profiles/${id}/phone-view`,
    method: 'post',
    data: data
  });
}

/* ============================ 教育经历 ============================ */

/** 查询教育经历列表（不分页，详情页直接渲染） */
export function listEducation(id: string | number): AxiosPromise<HrTalentEducationVO[]> {
  return request({
    url: `/talent/profiles/${id}/educations/list`,
    method: 'get'
  });
}

/** 新增教育经历 */
export function addEducation(id: string | number, data: HrTalentEducationForm) {
  return request({
    url: `/talent/profiles/${id}/educations`,
    method: 'post',
    data: data
  });
}

/** 编辑教育经历 */
export function updateEducation(id: string | number, data: HrTalentEducationForm) {
  return request({
    url: `/talent/profiles/${id}/educations/${data.educationId}`,
    method: 'put',
    data: data
  });
}

/** 逻辑删除教育经历 */
export function delEducation(id: string | number, ids: string | number | Array<string | number>) {
  return request({
    url: `/talent/profiles/${id}/educations/${ids}`,
    method: 'delete'
  });
}

/* ============================ 工作经历 ============================ */

/** 查询工作经历列表（不分页） */
export function listWork(id: string | number): AxiosPromise<HrTalentWorkVO[]> {
  return request({
    url: `/talent/profiles/${id}/works/list`,
    method: 'get'
  });
}

/** 新增工作经历（「是否当前任职」为真时服务端把离职日期置空） */
export function addWork(id: string | number, data: HrTalentWorkForm) {
  return request({
    url: `/talent/profiles/${id}/works`,
    method: 'post',
    data: data
  });
}

/** 编辑工作经历 */
export function updateWork(id: string | number, data: HrTalentWorkForm) {
  return request({
    url: `/talent/profiles/${id}/works/${data.workId}`,
    method: 'put',
    data: data
  });
}

/** 逻辑删除工作经历 */
export function delWork(id: string | number, ids: string | number | Array<string | number>) {
  return request({
    url: `/talent/profiles/${id}/works/${ids}`,
    method: 'delete'
  });
}

/* ============================ 项目经历 ============================ */

/** 查询项目经历列表（不分页） */
export function listProject(id: string | number): AxiosPromise<HrTalentProjectVO[]> {
  return request({
    url: `/talent/profiles/${id}/projects/list`,
    method: 'get'
  });
}

/** 新增项目经历 */
export function addProject(id: string | number, data: HrTalentProjectForm) {
  return request({
    url: `/talent/profiles/${id}/projects`,
    method: 'post',
    data: data
  });
}

/** 编辑项目经历 */
export function updateProject(id: string | number, data: HrTalentProjectForm) {
  return request({
    url: `/talent/profiles/${id}/projects/${data.projectId}`,
    method: 'put',
    data: data
  });
}

/** 逻辑删除项目经历 */
export function delProject(id: string | number, ids: string | number | Array<string | number>) {
  return request({
    url: `/talent/profiles/${id}/projects/${ids}`,
    method: 'delete'
  });
}

/* ============================ 人才标签 ============================ */

/** 查询某人才当前有效的标签（读取权限 talent:profile:list） */
export function listProfileTags(id: string | number): AxiosPromise<HrTalentTagVO[]> {
  return request({
    url: `/talent/profiles/${id}/tags`,
    method: 'get'
  });
}

/** 更新人才的标签关系（全量覆盖语义，重复挂载幂等；权限 talent:profile:edit） */
export function updateProfileTags(id: string | number, data: HrTalentProfileTagForm) {
  return request({
    url: `/talent/profiles/${id}/tags`,
    method: 'put',
    data: data
  });
}

/* ============================ 人才跟进 ============================ */

/** 分页查询某位人才的跟进记录（权限 talent:profile:list） */
export function listFollowUp(
  id: string | number,
  query: HrTalentFollowUpQuery
): AxiosPromise<PageResult<HrTalentFollowUpVO>> {
  return request({
    url: `/talent/profiles/${id}/follow-ups`,
    method: 'get',
    params: query
  });
}

/** 新增跟进记录（权限 talent:profile:edit） */
export function addFollowUp(id: string | number, data: HrTalentFollowUpForm) {
  return request({
    url: `/talent/profiles/${id}/follow-ups`,
    method: 'post',
    data: data
  });
}

/** 更新跟进记录 */
export function updateFollowUp(id: string | number, data: HrTalentFollowUpForm) {
  return request({
    url: `/talent/profiles/${id}/follow-ups/${data.followId}`,
    method: 'put',
    data: data
  });
}

/** 逻辑删除跟进记录 */
export function delFollowUp(id: string | number, ids: string | number | Array<string | number>) {
  return request({
    url: `/talent/profiles/${id}/follow-ups/${ids}`,
    method: 'delete'
  });
}
