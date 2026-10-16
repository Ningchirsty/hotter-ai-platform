import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigCapabilityCandidateForm,
  AigCapabilityCandidateVO
} from '@/api/aigov/capability/overview-types';

/**
 * 「模型候选」接口（= 能力 × 模型绑定，后端 `/aigov/binding`）。
 *
 * <p><b>为什么不复用 `@/api/aigov/binding`</b>：那个模块指向的是 `/aigov/agent/binding`
 * （Agent 版本的可见范围绑定），与"能力配哪个模型"完全不是一回事 —— 这正是走读里
 * 「一个『绑』字四个意思」的现场。新代码统一叫**模型候选**，路径独立，不再共用名字。</p>
 *
 * <p>权限：读 `aig:route:list`，写 `aig:route:add|edit|remove`（P0 沿用；P1 会拆成
 * `aig:capability:candidate:*`）。</p>
 */

/** 某能力的候选列表（按用途与优先级排序） */
export function listCandidates(capabilityCode: string): AxiosPromise<AigCapabilityCandidateVO[]> {
  return request({
    url: '/aigov/binding/list',
    method: 'get',
    params: { capabilityCode, pageNum: 1, pageSize: 200 }
  });
}

/** 新增候选 */
export function addCandidate(data: AigCapabilityCandidateForm) {
  return request({
    url: '/aigov/binding',
    method: 'post',
    data
  });
}

/** 编辑候选的用途与优先级（不能换模型） */
export function updateCandidate(bindId: string | number, data: AigCapabilityCandidateForm) {
  return request({
    url: '/aigov/binding/' + bindId,
    method: 'put',
    data
  });
}

/** 删除候选（逻辑删除） */
export function deleteCandidate(bindId: string | number) {
  return request({
    url: '/aigov/binding/' + bindId,
    method: 'delete'
  });
}
