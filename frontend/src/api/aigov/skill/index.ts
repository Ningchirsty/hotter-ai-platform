import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigSkillQuery, AigSkillVersionQuery, AigSkillVersionVO, AigSkillVO } from './types';

/** 分页查询 Skill 清单 */
export function listSkill(query: AigSkillQuery): AxiosPromise<PageResult<AigSkillVO>> {
  return request({
    url: '/aigov/agent/skill/list',
    method: 'get',
    params: query
  });
}

/** 分页查询 Skill 版本清单 */
export function listSkillVersion(query: AigSkillVersionQuery): AxiosPromise<PageResult<AigSkillVersionVO>> {
  return request({
    url: '/aigov/agent/skill/version/list',
    method: 'get',
    params: query
  });
}
