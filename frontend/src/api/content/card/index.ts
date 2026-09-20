import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { CpCardQuery, CpCardResolveForm, CpInteractionCardVO } from './types';

// 查询互动卡分页（支持「待我确认」与「仅看阻断项」筛选）
export function listCard(query: CpCardQuery): AxiosPromise<PageResult<CpInteractionCardVO>> {
  return request({
    url: '/content/card/list',
    method: 'get',
    params: query
  });
}

// 处理互动卡（后端路径为 /content/card/resolve，cardId 随 body 提交）
export function resolveCard(data: CpCardResolveForm): AxiosPromise<CpInteractionCardVO> {
  return request({
    url: '/content/card/resolve',
    method: 'post',
    data: data
  });
}
