import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { TalentDuplicateVO } from '../duplicate/types';
import type {
  TalentArchiveForm,
  TalentContactForm,
  TalentContactVO,
  TalentDetailVO,
  TalentForm,
  TalentGrantForm,
  TalentGrantVO,
  TalentQuery,
  TalentVO
} from './types';

// 查询人才档案列表（数据范围由服务端按区域/单条授权追加）
export function listTalent(query: TalentQuery): AxiosPromise<PageResult<TalentVO>> {
  return request({
    url: '/talent/profile/list',
    method: 'get',
    params: query
  });
}

// 查询人才档案详情
export function getTalent(talentId: string | number): AxiosPromise<TalentDetailVO> {
  return request({
    url: '/talent/profile/' + talentId,
    method: 'get'
  });
}

// 查看完整手机号（会写入敏感审计；前端只在弹窗内展示，不落本地存储）
export function getFullPhone(talentId: string | number): AxiosPromise<string> {
  return request({
    url: '/talent/profile/fullPhone/' + talentId,
    method: 'get'
  });
}

// 重复预检（不落库）
export function preCheckTalent(data: TalentForm): AxiosPromise<TalentDuplicateVO[]> {
  return request({
    url: '/talent/profile/preCheck',
    method: 'post',
    data: data
  });
}

// 新增人才档案
export function addTalent(data: TalentForm) {
  return request({
    url: '/talent/profile',
    method: 'post',
    data: data
  });
}

// 修改人才档案
export function updateTalent(data: TalentForm) {
  return request({
    url: '/talent/profile',
    method: 'put',
    data: data
  });
}

// 归档人才档案
export function archiveTalent(data: TalentArchiveForm) {
  return request({
    url: '/talent/profile/archive',
    method: 'put',
    data: data
  });
}

// 查询联系记录
export function listContact(talentId: string | number): AxiosPromise<TalentContactVO[]> {
  return request({
    url: '/talent/profile/contact/' + talentId,
    method: 'get'
  });
}

// 新增联系记录
export function addContact(data: TalentContactForm) {
  return request({
    url: '/talent/profile/contact',
    method: 'post',
    data: data
  });
}

// 查询授权配置
export function listGrant(talentId: string | number): AxiosPromise<TalentGrantVO[]> {
  return request({
    url: '/talent/profile/grant/' + talentId,
    method: 'get'
  });
}

// 新增授权
export function addGrant(data: TalentGrantForm) {
  return request({
    url: '/talent/profile/grant',
    method: 'post',
    data: data
  });
}

// 撤销授权
export function revokeGrant(grantId: string | number) {
  return request({
    url: '/talent/profile/grant/' + grantId,
    method: 'delete'
  });
}
