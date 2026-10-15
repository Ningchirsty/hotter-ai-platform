import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigAgentResourceReferenceVO,
  AigKnowledgeChunkVO,
  AigKnowledgeCollectionForm,
  AigKnowledgeCollectionVO,
  AigKnowledgeDocumentForm,
  AigKnowledgeDocumentVO,
  AigKnowledgeDocumentRegisterVO,
  AigKnowledgeDocumentVersionVO,
  AigKnowledgeIndexingVO,
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

/**
 * 解析并索引（设计 §6.4 的完整管道；附件「知识处理引擎：优先内部可部署，按试点评测选型」）。
 *
 * 与 `advanceVersion` 的区别：`advance` 只推状态；本接口会**真的解析、分段、嵌向量、写索引**，
 * 结束后版本进入 `READY`（真能被检索）。文件随请求上传——本服务不取对象存储里的原文。
 *
 * 幂等键必须由调用方给出并**在重试时复用**：换个键重试等于让同一份资料再索引一次。
 */
export function indexVersion(
  versionId: string | number,
  idempotencyKey: string,
  file: File
): AxiosPromise<AigKnowledgeIndexingVO> {
  const data = new FormData();
  data.append('file', file);
  data.append('idempotencyKey', idempotencyKey);
  return request({
    url: `/aigov/knowledge/document-versions/${versionId}/index`,
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

/** 检索测试（只检索、不生成） */
export function retrievalTest(
  data: AigKnowledgeRetrievalTestForm
): AxiosPromise<AigKnowledgeRetrievalTestVO> {
  return request({ url: '/aigov/knowledge/retrieval-tests', method: 'post', data });
}

/**
 * 某版本的分段预览（设计 §6.4「解析预览、分段」）。
 *
 * 权限沿用 `aig:knowledge:retrieval:test`：给的是受限长度的预览，与检索测试返回的
 * 引用预览是同一类东西，**不是原文**。
 */
export function listVersionChunks(
  versionId: string | number,
  limit?: number
): AxiosPromise<AigKnowledgeChunkVO[]> {
  return request({
    url: `/aigov/knowledge/document-versions/${versionId}/chunks`,
    method: 'get',
    params: { limit }
  });
}

/**
 * 「谁在引用这个知识集合」（设计 §6.4「引用该版本的 Agent」）。
 *
 * 退役之前先看影响面——只给绑定 ID，运维没法判断"停了会影响谁"。
 * 返回含停用中的绑定（如实标注 enabled），它们不会被采用。
 */
export function listCollectionReferences(
  collectionId: string | number
): AxiosPromise<AigAgentResourceReferenceVO[]> {
  return request({ url: `/aigov/knowledge/collections/${collectionId}/references`, method: 'get' });
}
