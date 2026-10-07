/**
 * 黄金用例与评测类型定义（对齐后端 AigEvaluationCaseVo / AigEvaluationRunVo /
 * AigGoldenCaseEvidence）。
 *
 * 两条口径值得记住：
 * · 用例的判据（expected_json）与运行的打分明细（score_json）是 longtext：**详情接口返回原文，列表接口不含**；
 * · `reviewResult = MANUAL` 是「待人工复核」这个状态本身，不是一种复核结论。
 */

/** 用例清单行（不含判据原文） */
export interface AigEvaluationCaseVO extends BaseEntity {
  caseId?: string | number;
  caseCode?: string;
  caseName?: string;
  /** 用例类型：PLAN / VISUAL_DNA / IMAGE_QA */
  caseType?: string;
  scenarioCode?: string;
  /** 输入快照引用（inline:<json> / classpath:<相对路径>） */
  inputSnapshotRef?: string;
  costMin?: number;
  costMax?: number;
  dataLevel?: string;
  classification?: string;
  status?: string;
  remark?: string;
}

/** 用例详情（含判据原文，后端返回实体） */
export interface AigEvaluationCaseDetail extends AigEvaluationCaseVO {
  expectedJson?: string;
  rubricJson?: string;
}

/** 用例定义入参 */
export interface AigEvaluationCaseForm {
  caseCode?: string;
  caseName?: string;
  caseType?: string;
  scenarioCode?: string;
  inputSnapshotRef?: string;
  /** 机器判据（JSON 文本；判据写法在定义期即校验） */
  expectedJson?: string;
  /** 人工 Rubric（JSON 文本；填了就意味着这条用例需要人工复核） */
  rubricJson?: string;
  costMin?: number;
  costMax?: number;
  dataLevel?: string;
  classification?: string;
  remark?: string;
}

/** 评测运行行（不含打分明细） */
export interface AigEvaluationRunVO extends BaseEntity {
  runId?: string | number;
  runNo?: string;
  targetType?: string;
  targetVersionId?: string | number;
  caseId?: string | number;
  providerId?: string | number;
  modelCode?: string;
  externalCall?: string;
  totalScore?: number;
  /** 人工复核结论：PASS / FAIL / MANUAL（待复核）/ null（不要求复核） */
  reviewResult?: string;
  reviewerId?: string | number;
  reviewedAt?: string;
  costAmount?: number;
  latencyMs?: number;
  traceId?: string;
  /** 运行状态：RUNNING / PASS / FAIL / ERROR */
  resultStatus?: string;
  operateTime?: string;
  remark?: string;
}

/** 运行详情（含打分明细原文，后端返回实体） */
export interface AigEvaluationRunDetail extends AigEvaluationRunVO {
  scoreJson?: string;
}

/** 跑评测入参（用例集合必须等于版本声明的集合） */
export interface AigEvaluationRunForm {
  targetType: string;
  targetVersionId: string | number;
  caseCodes: string[];
  operatorId?: string | number;
  remark?: string;
}

/** 人工复核入参 */
export interface AigEvaluationReviewForm {
  runId: string | number;
  /** 只接受 PASS / FAIL（MANUAL 是状态本身） */
  reviewResult: string;
  totalScore?: number;
  reviewerId?: string | number;
  remark?: string;
}

/** 「黄金用例是否通过」的证据 */
export interface AigGoldenCaseEvidence {
  satisfied?: boolean;
  declaredCaseCodes?: string[];
  caseVerdicts?: Record<string, string>;
  reason?: string;
}
