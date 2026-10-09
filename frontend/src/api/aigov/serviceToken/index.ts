import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigServiceTokenIssuedVO, AigServiceTokenIssueForm, AigServiceTokenVO } from './types';

/**
 * 服务令牌清单。
 *
 * 刻意**不分页**：机器身份的条数天然很少（几十条以内），而分页会让"到底有几把还在用"
 * 变成一个需要翻页才能回答的问题；这里一次全给。
 */
export function listServiceToken(): AxiosPromise<AigServiceTokenVO[]> {
  return request({
    url: '/aigov/service-token/list',
    method: 'get'
  });
}

/**
 * 签发服务令牌。
 *
 * 返回体里的明文**只此一次**：调用方必须当场交付给使用方，平台之后无法找回。
 */
export function issueServiceToken(data: AigServiceTokenIssueForm): AxiosPromise<AigServiceTokenIssuedVO> {
  return request({
    url: '/aigov/service-token',
    method: 'post',
    data
  });
}

/**
 * 停用服务令牌（不物理删除，保留审计痕迹）。
 *
 * 影响面：该调用方的**所有**调用立刻 401，所以前端必须二次确认。
 */
export function revokeServiceToken(tokenId: string | number): AxiosPromise<void> {
  return request({
    url: '/aigov/service-token/' + tokenId + '/revoke',
    method: 'put'
  });
}
