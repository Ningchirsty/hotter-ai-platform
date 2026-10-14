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
  noSupport?: boolean;
  reason?: string;
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
