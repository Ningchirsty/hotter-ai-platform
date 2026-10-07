/**
 * Package 类型定义（对齐后端 AigPackageVo / AigPackageVersionVo / AigPackageRegisterVo /
 * AigPackageInstallVo / AigPackageInstallLogVo）。
 *
 * 注意后端返回的字段名：`scanResult` 是 PASS/REJECT/PENDING；`releaseStatus` 是 DRAFT 起的发布状态机。
 */

/** Package 清单行 */
export interface AigPackageVO extends BaseEntity {
  packageId?: string | number;
  packageCode?: string;
  packageName?: string;
  /** 类型：AGENT / SKILL / MIXED */
  packageType?: string;
  publisher?: string;
  licenseCode?: string;
  /** 包体 SHA-256（最近一次上传的包体哈希） */
  checksum?: string;
  /** 来源类型：UPLOAD / TRUSTED_SOURCE */
  sourceType?: string;
  sourceRef?: string;
  description?: string;
  status?: string;
  remark?: string;
}

/** Package 清单查询 */
export interface AigPackageQuery extends PageQuery {
  packageCode?: string;
  packageName?: string;
  packageType?: string;
  publisher?: string;
  sourceType?: string;
  status?: string;
  params?: Record<string, any>;
}

/** Package 版本行 */
export interface AigPackageVersionVO extends BaseEntity {
  packageVersionId?: string | number;
  packageId?: string | number;
  version?: string;
  /** Manifest 原文 SHA-256（复扫时用来发现「入库后被改动」） */
  manifestHash?: string;
  /** 扫描结论：PASS / REJECT / PENDING（未扫描为 null） */
  scanResult?: string;
  scanDetail?: string;
  releaseStatus?: string;
  releaseChannel?: string;
  evaluationRunId?: string | number;
  approvedBy?: string | number;
  approvedAt?: string;
  status?: string;
  remark?: string;
}

/** Package 版本清单查询 */
export interface AigPackageVersionQuery extends PageQuery {
  packageId?: string | number;
  releaseStatus?: string;
  releaseChannel?: string;
  scanResult?: string;
  version?: string;
  params?: Record<string, any>;
}

/**
 * 上传入参。
 *
 * 包身份与版本号不在入参里——它们以 Manifest 为准（Manifest 的 package_code/version/checksum 是必填项），
 * 传两份就会出现「入参说 1.0.0、Manifest 说 1.0.1」这种两处不一致。
 */
export interface AigPackageUploadForm {
  manifestJson: string;
  sourceRef?: string;
  remark?: string;
}

/** 上传登记结果（被拒也是 200：上传成功、Manifest 被拒，看 scanPass） */
export interface AigPackageRegisterVO {
  packageId?: string | number;
  packageVersionId?: string | number;
  packageCode?: string;
  version?: string;
  checksum?: string;
  manifestHash?: string;
  scanPass?: boolean;
  scanResult?: string;
  scanDetail?: string;
  manifest?: any;
}

/** 安装结果 */
export interface AigPackageInstallVO {
  packageId?: string | number;
  packageVersionId?: string | number;
  alreadyInstalled?: boolean;
  agents?: AigInstalledItem[];
  skills?: AigInstalledItem[];
  note?: string;
}

/** 装出来的一个对象 */
export interface AigInstalledItem {
  code?: string;
  parentId?: string | number;
  versionId?: string | number;
  version?: string;
  created?: boolean;
}

/** 安装账本行 */
export interface AigPackageInstallLogVO {
  logId?: string | number;
  packageVersionId?: string | number;
  /** 动作：UPLOAD / INSTALL / SCAN / SANDBOX / ... / DISABLE */
  action?: string;
  operatorId?: string | number;
  /** 结果：PASS / FAIL / REJECT */
  result?: string;
  detail?: string;
  evidenceRef?: string;
  operateTime?: string;
}

/** 停用结果（把该 Package 版本带进来的版本批量下线） */
export interface AigPackageDisableVO {
  packageId?: string | number;
  packageVersionId?: string | number;
  /** 幂等命中：带进来的版本全都已在停用状态，本次未改动 */
  alreadyDisabled?: boolean;
  disabled?: AigPackageDisabledItem[];
  /** 未改动的版本——每一项都带原因，页面要能回答「为什么这个没停掉」 */
  skipped?: AigPackageSkippedItem[];
  note?: string;
}

/** 被停用的一个版本 */
export interface AigPackageDisabledItem {
  /** AGENT_VERSION / SKILL_VERSION */
  targetType?: string;
  code?: string;
  parentId?: string | number;
  versionId?: string | number;
  version?: string;
  /** 停用前的状态（STABLE 下线与 DRAFT 下线的影响面不同，页面要显示出来） */
  fromStatus?: string;
}

/** 未改动的一个版本 */
export interface AigPackageSkippedItem {
  targetType?: string;
  code?: string;
  parentId?: string | number;
  versionId?: string | number;
  version?: string;
  fromStatus?: string;
  reason?: string;
}

/** Manifest 扫描结论（后端 AigManifestScanResult） */
export interface AigManifestScanVO {
  pass?: boolean;
  hitRules?: string[];
  detail?: string;
  manifestHash?: string;
  manifest?: any;
}
