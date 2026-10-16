import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigCapabilityOverviewQuery, AigCapabilityOverviewVO } from './overview-types';

/**
 * 能力概览（配置完整度聚合）。
 *
 * <p>权限沿用 `aig:capability:list`；筛选（能力名 / 标签 / 问题码 / 候选有无）全部下发到服务端，
 * 这样「只看缺模型候选的能力」在翻页后依然成立。</p>
 */
export function capabilityOverview(
  query: AigCapabilityOverviewQuery
): AxiosPromise<AigCapabilityOverviewVO[]> {
  return request({
    url: '/aigov/capability/overview',
    method: 'get',
    params: query
  });
}
