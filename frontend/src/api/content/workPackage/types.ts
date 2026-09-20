/**
 * 设计开工包类型定义（对齐后端 CpWorkPackageVo / ContentWorkPackageServiceImpl.buildContent）。
 *
 * contentJson 的结构见 SPEC §4.5；实现里 spec 段落的字段为
 * outputSize / resolution / acceptance（以实现为准）。
 */

/** 开工包内：已确认事实 */
export interface WorkPackageFact {
  fieldCode?: string;
  fieldName?: string;
  value?: string;
  sourceLocator?: string;
  sourceExcerpt?: string;
  confirmedAt?: string;
}

/** 开工包内：素材条目 */
export interface WorkPackageAsset {
  fileId?: string | number;
  fileName?: string;
  fileKind?: string;
  dataLevel?: string;
}

/** 开工包内：素材分层 */
export interface WorkPackageAssets {
  /** 资料依据（Excel/Word/PDF） */
  productBasis?: WorkPackageAsset[];
  /** 待分类素材（图片/设计稿等） */
  unclassified?: WorkPackageAsset[];
  brand?: WorkPackageAsset[];
  reference?: WorkPackageAsset[];
  note?: string;
}

/** 开工包内：缺口（未满足的条件项） */
export interface WorkPackageGap {
  fieldCode?: string;
  fieldName?: string;
  note?: string;
}

/** 开工包内：允许的 AI 动作 */
export interface WorkPackageAiAction {
  capability?: string;
  name?: string;
  desc?: string;
  scope?: string;
}

/** 开工包内：输出规格 */
export interface WorkPackageSpec {
  outputSize?: string | null;
  resolution?: string | null;
  acceptance?: string;
}

/** 开工包全文（contentJson 解析后的结构） */
export interface WorkPackageContent {
  taskNo?: string;
  taskName?: string;
  deliverableType?: string;
  deliverableTypeName?: string;
  snapshotVersion?: number;
  product?: {
    productCode?: string;
    productName?: string;
    skuCode?: string;
    skuName?: string;
    version?: string;
    skuCodeFromTask?: string;
  };
  confirmedFacts?: WorkPackageFact[];
  assets?: WorkPackageAssets;
  copy?: { confirmed?: string[]; forbidden?: string[]; note?: string };
  gaps?: WorkPackageGap[];
  allowedAiActions?: WorkPackageAiAction[];
  /** 不可修改项（必须含产品主体、Logo、包装文字） */
  immutableItems?: string[];
  spec?: WorkPackageSpec;
  owner?: { ownerId?: string | number; ownerName?: string };
  deadline?: string | null;
  contentUnits?: { list?: any[]; note?: string };
}

/** 开工包 */
export interface CpWorkPackageVO extends BaseEntity {
  packageId?: string | number;
  taskId?: string | number;
  taskNo?: string;
  /** 签发时冻结的事实版本 */
  snapshotVersion?: number;
  /** 开工包全文（JSON 文本） */
  contentJson?: string;
  /** DRAFT / ISSUED */
  status?: string;
  generatedBy?: string | number;
  generatedAt?: string;
  issuedBy?: string | number;
  issuedAt?: string;
  remark?: string;
  createByName?: string;
}
