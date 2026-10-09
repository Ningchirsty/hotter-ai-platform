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
  /**
   * 结论产出方：PLATFORM=平台执行器跑的；ADMIN=管理员人工评测后录入。
   * 同一条用例两次都是 PASS，一次平台跑的、一次人填的，对读的人完全是两件事，列表页必须显示。
   */
  executedBy?: string;
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
  /**
   * 其中结论由管理员人工评测录入（executed_by=ADMIN）的用例编码。
   * 门槛对两种来源一视同仁（都认 PASS），但来源必须看得见——机器结论的可信度来自
   * 「平台判据在同样输入上判过了」，人工结论的可信度来自「一个人看了并签了字」。
   */
  adminCaseCodes?: string[];
}

/** 人工评测录入入参（平台没有该对象的执行器时，由管理员产出证据） */
export interface AigEvaluationManualRunForm {
  targetType: string;
  targetVersionId: string | number;
  /** 逐用例结论，必须覆盖版本声明的全部黄金用例 */
  cases: AigEvaluationManualCase[];
  /** 评测方法与依据（必填）：在哪个环境、用什么输入、按什么标准看的 */
  method: string;
  operatorId?: string | number;
  /** 平台侧是否发生外部调用（Y/N，默认 N）；在外部环境跑出来的要如实填 Y */
  externalCall?: string;
  /** 实际成本；用例声明了成本范围时必填（未上报不能当作在范围内） */
  costAmount?: number;
  remark?: string;
}

/** 人工评测的单条用例结论 */
export interface AigEvaluationManualCase {
  caseCode: string;
  /** 只接受 PASS / FAIL */
  verdict: string;
  /** 证据引用（报告链接/截图/工单号） */
  evidenceRef?: string;
  note?: string;
}
