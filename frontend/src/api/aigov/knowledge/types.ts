/**
 * 知识中心类型（对应后端 `/aigov/knowledge`）。
 *
 * @author ai-gov
 */

/** 知识集合 */
export interface AigKnowledgeCollectionVO {
  collectionId?: string | number;
  collectionCode?: string;
  collectionName?: string;
  ownerId?: string | number;
  orgScope?: string;
  dataLevel?: string;
  status?: string;
  remark?: string;
}

/** 知识文档 */
export interface AigKnowledgeDocumentVO {
  documentId?: string | number;
  collectionId?: string | number;
  sourceName?: string;
  /** PDF / DOCX / TXT / MARKDOWN */
  sourceType?: string;
  ownerId?: string | number;
  status?: string;
}

/** 文档版本 */
export interface AigKnowledgeDocumentVersionVO {
  versionId?: string | number;
  documentId?: string | number;
  version?: string;
  sha256?: string;
  sourceRef?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  /** UPLOADED/SCANNING/PARSING/CHUNKING/INDEXING/READY/FAILED/RETIRED */
  status?: string;
}

/** 新建集合 */
export interface AigKnowledgeCollectionForm {
  collectionCode?: string;
  collectionName?: string;
  orgScope?: string;
  dataLevel?: string;
  remark?: string;
}

/** 登记文档与版本 */
export interface AigKnowledgeDocumentForm {
  collectionId?: string | number;
  sourceName?: string;
  sha256?: string;
  sourceRef?: string;
  version?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  remark?: string;
}

/** 登记结果 */
export interface AigKnowledgeDocumentRegisterVO {
  documentId?: string | number;
  versionId?: string | number;
  version?: string;
  status?: string;
  duplicateHint?: boolean;
  duplicateVersionId?: string | number;
}

/** 检索测试入参 */
export interface AigKnowledgeRetrievalTestForm {
  query?: string;
  collectionCodes?: string[];
  versionIds?: (string | number)[];
  maxChunks?: number;
  /**
   * 检索模式：`KEYWORD`（默认，同正式执行路径）/ `VECTOR`（向量索引）。
   *
   * 两路各自跑，**不提供混合模式**：排序权重属于"按试点评测确定"的参数。
   */
  mode?: string;
}

/** 检索引用 */
export interface AigKnowledgeCitationVO {
  chunkId?: string | number;
  versionId?: string | number;
  documentId?: string | number;
  sourceName?: string;
  locator?: string;
  excerptRef?: string;
  dataLevel?: string;
}

/** 检索测试结果 */
export interface AigKnowledgeRetrievalTestVO {
  citations?: AigKnowledgeCitationVO[];
  /** 实际使用的检索模式（服务端回显） */
  mode?: string;
  noSupport?: boolean;
  reason?: string;
}

/** 解析并索引结果（`POST /document-versions/{id}/index`） */
export interface AigKnowledgeIndexingVO {
  versionId?: string | number;
  status?: string;
  parserEngineId?: string;
  embeddingEngineId?: string;
  encoding?: string;
  chunkCount?: number;
  vectorIndexEngine?: string;
  errorCode?: string;
  message?: string;
}

/**
 * 知识分段预览（设计 §6.4「解析预览、分段」）。
 *
 * 只带受限长度的 `textExcerpt`：**正文不在这个接口里**（在受控存储引用里），
 * 两个布尔字段如实说明"正文在哪、有没有进索引"。
 */
export interface AigKnowledgeChunkVO {
  chunkId?: string | number;
  ordinal?: number;
  locator?: string;
  textExcerpt?: string;
  dataLevel?: string;
  hasFullText?: boolean;
  indexed?: boolean;
}

/**
 * 「谁在引用这个知识集合」（设计 §6.4「引用该版本的 Agent」）。
 *
 * 退役前的影响面：只有绑定 ID 没法判断"停了会影响谁"。
 * `enabled=N` 的也返回并如实标注——它不会被采用，但仍是"这份资源曾经给过谁"的记录。
 */
export interface AigAgentResourceReferenceVO {
  bindingId?: string | number;
  agentVersionId?: string | number;
  agentId?: string | number;
  /** 版本或 Agent 已删除时为空（留空才能看出"它指着的东西不在了"） */
  agentCode?: string;
  agentName?: string;
  agentVersion?: string;
  releaseStatus?: string;
  enabled?: string;
  companyId?: string | number;
  brandId?: string | number;
  scenarioCode?: string;
  roleScope?: string;
  createTime?: string;
}
