import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { CpProductForm, CpProductQuery, CpProductVO } from './types';

// 查询产品与SKU分页列表
export function listProduct(query: CpProductQuery): AxiosPromise<PageResult<CpProductVO>> {
  return request({
    url: '/content/product/list',
    method: 'get',
    params: query
  });
}

// 查询产品下拉选项（任务新建时选择产品）
export function productOptions(): AxiosPromise<CpProductVO[]> {
  return request({
    url: '/content/product/options',
    method: 'get'
  });
}

// 查询产品详情
export function getProduct(productId: string | number): AxiosPromise<CpProductVO> {
  return request({
    url: '/content/product/' + productId,
    method: 'get'
  });
}

// 新增产品（后端 POST 落在 /content/product 根路径）
export function addProduct(data: CpProductForm) {
  return request({
    url: '/content/product',
    method: 'post',
    data: data
  });
}

// 修改产品（后端 PUT 落在 /content/product 根路径，产品ID随body提交）
export function updateProduct(data: CpProductForm) {
  return request({
    url: '/content/product',
    method: 'put',
    data: data
  });
}

// 删除产品
export function delProduct(productId: string | number | Array<string | number>) {
  return request({
    url: '/content/product/' + productId,
    method: 'delete'
  });
}
