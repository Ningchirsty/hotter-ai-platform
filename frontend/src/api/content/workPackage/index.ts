import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { CpWorkPackageVO } from './types';

// 生成开工包（草稿）：不满足开工条件时后端返回可读的阻断说明
export function generateWorkPackage(taskId: string | number): AxiosPromise<string | number> {
  return request({
    url: '/content/workPackage/generate',
    method: 'post',
    params: { taskId: taskId }
  });
}

// 签发开工包
export function issueWorkPackage(packageId: string | number) {
  return request({
    url: '/content/workPackage/' + packageId + '/issue',
    method: 'post'
  });
}

// 按任务查询最新开工包（未生成时 data 为 null）
export function getWorkPackageByTask(taskId: string | number): AxiosPromise<CpWorkPackageVO | null> {
  return request({
    url: '/content/workPackage/byTask/' + taskId,
    method: 'get'
  });
}
