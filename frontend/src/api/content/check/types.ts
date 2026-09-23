/**
 * 成品一致性检查 API 类型。
 *
 * 能力定位：把「生成的结果图」与「原参考图」比对，判断成品是否忠实还原参考图，
 * 覆盖任务出稿之后的验收环节。结论不回流为产品事实。
 */

/** 检查状态（对应字典 cp_check_status）。 */
export type ContentCheckStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

/** 检查结论（对应字典 cp_check_verdict）。 */
export type ContentCheckVerdict = 'CONSISTENT' | 'INCONSISTENT' | 'UNCERTAIN';

/** 成品一致性检查视图对象。 */
export interface CpOutputCheckVO extends BaseEntity {
  checkId?: string | number;
  /** 检查单号（CK+日期+序号） */
  checkNo?: string;
  taskId?: string | number;
  /** 任务号（后端补齐） */
  taskNo?: string;
  /** 任务名称（后端补齐） */
  taskName?: string;
  /** 交付类型（后端补齐） */
  deliverableType?: string;
  productId?: string | number;
  /** 产品名称（后端补齐） */
  productName?: string;
  referenceFileId?: string | number;
  /** 参考图文件名（后端补齐） */
  referenceFileName?: string;
  resultFileId?: string | number;
  /** 成品图文件名（后端补齐） */
  resultFileName?: string;
  status?: ContentCheckStatus;
  /** 检查结论；未出结论时为空 */
  verdict?: ContentCheckVerdict;
  /** 一致性得分 0-100；算不出时为空（后端刻意不编造） */
  score?: number | string;
  /** 结论摘要 */
  summary?: string;
  /** 差异清单 JSON 字符串：findings 数组 */
  findingsJson?: string;
  /** 本地确定性度量 JSON 字符串 */
  metricsJson?: string;
  modelId?: string | number;
  modelKey?: string;
  deploymentType?: string;
  invokerName?: string;
  traceId?: string;
  /** 未取得结论的原因（status=FAILED 时有值） */
  failureReason?: string;
  checkedBy?: string | number;
  checkedAt?: string;
  remark?: string;
  /** 创建人账号（@Translation 翻译） */
  createByName?: string;
}

/** 检查分页查询条件。 */
export interface CpOutputCheckQuery extends PageQuery {
  taskId?: string | number;
  /** 任务号（模糊） */
  taskNo?: string;
  status?: ContentCheckStatus;
  verdict?: ContentCheckVerdict;
  /** 只看未通过（结论为不一致/无法判定，或检查失败） */
  onlyFailed?: boolean;
}

/** 差异清单条目。 */
export interface CheckFinding {
  category?: string;
  severity?: string;
  description?: string;
  [key: string]: any;
}

/** 本地确定性度量。 */
export interface CheckMetrics {
  referenceWidth?: number;
  referenceHeight?: number;
  resultWidth?: number;
  resultHeight?: number;
  referenceAspect?: number;
  resultAspect?: number;
  comparable?: boolean;
  /** 判定相似度 0-100（由「最差区域」得出，两张图各自独立比对）；不可比时为空 */
  similarity?: number | null;
  /** 比对口径：PIXEL=同尺寸逐像素；GRID=尺寸不同按网格 */
  compareMode?: 'PIXEL' | 'GRID';
  /**
   * 全图平均通道差 0-255（R/G/B 一起算）。
   * 仅供参考、**不用于判定**：白底商品图里背景常占九成以上且完全相同，
   * 拿全图平均会把中央那点真实差异平均掉（实测产品块红改蓝只得 5/255）。
   */
  meanChannelDiff?: number | null;
  /** 最差 1% 区域的平均通道差 0-255：判定所依据的量 */
  hotspotChannelDiff?: number | null;
  /** 仅网格口径的相似度，供对照（对高频纹理不敏感，不作为结论依据） */
  gridSimilarity?: number | null;
  meanLumaDiff?: number | null;
  /** 差异最集中的方位（人类可读，如「左上（通道差 40/255）」） */
  diffZones?: string[];
  gridSize?: number;
  notes?: string[];
  [key: string]: any;
}

/** 发起检查的表单（multipart）。 */
export interface RunCheckForm {
  taskId: string | number;
  /** 引用任务中已有的图片附件（与 referenceFile 二选一） */
  referenceFileId?: string | number;
  /** 新上传的参考图 */
  referenceFile?: File;
  /** 成品图（必填） */
  resultFile: File;
  remark?: string;
}
