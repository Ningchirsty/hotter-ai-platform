import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrTalentGroupForm,
  HrTalentGroupMemberVO,
  HrTalentGroupQuery,
  HrTalentGroupVO,
  HrTalentPoolForm,
  HrTalentPoolMemberForm,
  HrTalentPoolMemberVO,
  HrTalentPoolQuery,
  HrTalentPoolVO,
  HrTalentTagForm,
  HrTalentTagQuery,
  HrTalentTagVO
} from './types';

/**
 * 人才池 / 分组 / 标签 域接口封装
 *
 * 路径与后端已实现 Controller 逐字一致：
 * - TalentPoolController：/talent/pools（列表/详情/新增/编辑/删除 + /{poolId}/members）
 * - TalentGroupController：/talent/groups（列表/详情/新增/编辑/删除 + /{id}/members）
 * - TalentTagController：/talent/tags（标签字典 CRUD）
 *
 * 权限串：talent:pool:list/add/edit/member；分组接口用
 * 「talent:pool:member 或 talent:profile:list」二者满足其一；标签字典维护用 talent:profile:edit。
 */

/* ============================ 人才池 ============================ */

/** 分页查询人才池（自动套可见范围） */
export function listPool(query: HrTalentPoolQuery): AxiosPromise<PageResult<HrTalentPoolVO>> {
  return request({
    url: '/talent/pools',
    method: 'get',
    params: query
  });
}

/** 查询人才池详情 */
export function getPool(id: string | number): AxiosPromise<HrTalentPoolVO> {
  return request({
    url: '/talent/pools/' + id,
    method: 'get'
  });
}

/** 新增人才池 */
export function addPool(data: HrTalentPoolForm): AxiosPromise<string | number> {
  return request({
    url: '/talent/pools',
    method: 'post',
    data: data
  });
}

/** 更新人才池 */
export function updatePool(data: HrTalentPoolForm) {
  return request({
    url: '/talent/pools/' + data.poolId,
    method: 'put',
    data: data
  });
}

/** 逻辑删除人才池（只结束成员关系，不删除人才主档） */
export function delPool(ids: string | number | Array<string | number>) {
  return request({
    url: '/talent/pools/' + ids,
    method: 'delete'
  });
}

/** 分页查询池成员（自动叠加人才可见范围） */
export function listPoolMember(
  poolId: string | number,
  query: PageQuery & { memberStatus?: string }
): AxiosPromise<PageResult<HrTalentPoolMemberVO>> {
  return request({
    url: `/talent/pools/${poolId}/members`,
    method: 'get',
    params: query
  });
}

/**
 * 加入人才池（POST /talent/pools/{poolId}/members）
 *
 * 重复加入后端**按幂等处理并返回已有关系**，不报错；页面据返回的 memberId 提示「已在池中」。
 */
export function addPoolMember(
  poolId: string | number,
  data: HrTalentPoolMemberForm
): AxiosPromise<HrTalentPoolMemberVO> {
  return request({
    url: `/talent/pools/${poolId}/members`,
    method: 'post',
    data: data
  });
}

/** 移出人才池成员（只结束成员关系，不删除人才主档；reason 为 query 参数） */
export function removePoolMember(poolId: string | number, memberId: string | number, reason?: string) {
  return request({
    url: `/talent/pools/${poolId}/members/${memberId}`,
    method: 'delete',
    params: { reason }
  });
}

/* ============================ 人才分组 ============================ */

/** 分页查询分组（公共分组按可见范围，个人收藏只返回本人） */
export function listGroup(query: HrTalentGroupQuery): AxiosPromise<PageResult<HrTalentGroupVO>> {
  return request({
    url: '/talent/groups',
    method: 'get',
    params: query
  });
}

/** 查询分组详情 */
export function getGroup(id: string | number): AxiosPromise<HrTalentGroupVO> {
  return request({
    url: '/talent/groups/' + id,
    method: 'get'
  });
}

/** 新增分组（公共分组需人才池管理员，个人收藏默认归当前用户） */
export function addGroup(data: HrTalentGroupForm): AxiosPromise<string | number> {
  return request({
    url: '/talent/groups',
    method: 'post',
    data: data
  });
}

/** 更新分组 */
export function updateGroup(data: HrTalentGroupForm) {
  return request({
    url: '/talent/groups/' + data.groupId,
    method: 'put',
    data: data
  });
}

/** 逻辑删除分组（只删除分组与成员关系，不删除人才主档） */
export function delGroup(id: string | number) {
  return request({
    url: '/talent/groups/' + id,
    method: 'delete'
  });
}

/** 分页查询分组成员（自动叠加人才可见范围） */
export function listGroupMember(
  id: string | number,
  query: PageQuery
): AxiosPromise<PageResult<HrTalentGroupMemberVO>> {
  return request({
    url: `/talent/groups/${id}/members`,
    method: 'get',
    params: query
  });
}

/**
 * 加入分组（POST /talent/groups/{id}/members）
 *
 * 注意：后端 talentId 是 **@RequestParam**（不是请求体），因此以 params 传递。
 * 重复加入同样按幂等处理，返回已有关系。
 */
export function addGroupMember(id: string | number, talentId: string | number): AxiosPromise<HrTalentGroupMemberVO> {
  return request({
    url: `/talent/groups/${id}/members`,
    method: 'post',
    params: { talentId }
  });
}

/** 移出分组成员（只结束关系，不删除人才主档） */
export function removeGroupMember(id: string | number, memberId: string | number) {
  return request({
    url: `/talent/groups/${id}/members/${memberId}`,
    method: 'delete'
  });
}

/* ============================ 标签字典 ============================ */

/** 分页查询标签字典（非管理员的敏感标签默认隐藏；权限 talent:profile:list） */
export function listTag(query: HrTalentTagQuery): AxiosPromise<PageResult<HrTalentTagVO>> {
  return request({
    url: '/talent/tags',
    method: 'get',
    params: query
  });
}

/** 查询标签详情 */
export function getTag(id: string | number): AxiosPromise<HrTalentTagVO> {
  return request({
    url: '/talent/tags/' + id,
    method: 'get'
  });
}

/** 新增标签（名称与敏感标记经服务层校验；权限 talent:profile:edit） */
export function addTag(data: HrTalentTagForm): AxiosPromise<string | number> {
  return request({
    url: '/talent/tags',
    method: 'post',
    data: data
  });
}

/** 更新标签 */
export function updateTag(data: HrTalentTagForm) {
  return request({
    url: '/talent/tags/' + data.tagId,
    method: 'put',
    data: data
  });
}

/** 逻辑删除标签 */
export function delTag(ids: string | number | Array<string | number>) {
  return request({
    url: '/talent/tags/' + ids,
    method: 'delete'
  });
}
