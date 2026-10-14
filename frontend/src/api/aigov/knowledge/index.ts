import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigKnowledgeCollectionForm,
  AigKnowledgeCollectionVO,
  AigKnowledgeDocumentForm,
  AigKnowledgeDocumentVO,
  AigKnowledgeDocumentRegisterVO,
  AigKnowledgeDocumentVersionVO,
  AigKnowledgeRetrievalTestForm,
  AigKnowledgeRetrievalTestVO
} from './types';

/**
 * 知识中心接口（设计 §6.4、§9.2）。
 *
 * 读与写分开授权：`document:list` 看文档与版本；`document:upload` 登记；
 * `document:manage` 推进/失败/退役；`retrieval:test` 只检索不生成。
 */

/** 集合清单 */
export function listCollections(): AxiosPromise<AigKnowledgeCollectionVO[]> {
  return request({ url: '/aigov/knowledge/collections', method: 'get' });
}

/** 新建集合 */
export function createCollection(data: AigKnowledgeCollectionForm): AxiosPromise<number | string> {
  return request({ url: '/aigov/knowledge/collections', method: 'post', data });
}

/** 文档清单（可按集合过滤） */
export function listDocuments(collectionId?: string | number): AxiosPromise<AigKnowledgeDocumentVO[]> {
  return request({ url: '/aigov/knowledge/documents', method: 'get', params: { collectionId } });
}

/** 登记文档与版本（返回重复文件提示） */
export function registerDocument(
  data: AigKnowledgeDocumentForm
): AxiosPromise<AigKnowledgeDocumentRegisterVO> {
  return request({ url: '/aigov/knowledge/documents', method: 'post', data });
}

/** 版本详情 */
export function getDocumentVersion(versionId: string | number): AxiosPromise<AigKnowledgeDocumentVersionVO> {
  return request({ url: '/aigov/knowledge/document-versions/' + versionId, method: 'get' });
}

/** 推进一个处理阶段（幂等键由调用方给出） */
export function advanceVersion(
  versionId: string | number,
  idempotencyKey: string
): AxiosPromise<string> {
  return request({
    url: '/aigov/knowledge/document-versions/' + versionId + '/advance',
    method: 'post',
    params: { idempotencyKey }
  });
}

/** 退役版本 */
export function retireVersion(versionId: string | number): AxiosPromise<string> {
  return request({
    url: '/aigov/knowledge/document-versions/' + versionId + '/retire',
    method: 'post'
  });
}

/** 检索测试（只检索、不生成） */
export function retrievalTest(
  data: AigKnowledgeRetrievalTestForm
): AxiosPromise<AigKnowledgeRetrievalTestVO> {
  return request({ url: '/aigov/knowledge/retrieval-tests', method: 'post', data });
}
