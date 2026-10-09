import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigEvaluationCaseDetail,
  AigEvaluationCaseForm,
  AigEvaluationCaseVO,
  AigEvaluationManualRunForm,
  AigEvaluationReviewForm,
  AigEvaluationRunDetail,
  AigEvaluationRunForm,
  AigEvaluationRunVO,
  AigGoldenCaseEvidence
} from './types';

/** 列出黄金用例（裁剪 VO，不含判据原文） */
export function listCase(caseType?: string, scenarioCode?: string): AxiosPromise<AigEvaluationCaseVO[]> {
  return request({
    url: '/aigov/evaluation/case/list',
    method: 'get',
    params: { caseType, scenarioCode }
  });
}

/** 查用例详情（含判据原文） */
export function getCase(caseId: string | number): AxiosPromise<AigEvaluationCaseDetail> {
  return request({
    url: '/aigov/evaluation/case/' + caseId,
    method: 'get'
  });
}

/** 定义黄金用例（判据在定义期即校验：未知判据名/类型写错/空判据对象都当场拒绝） */
export function defineCase(data: AigEvaluationCaseForm) {
  return request({
    url: '/aigov/evaluation/case',
    method: 'post',
    data: data
  });
}

/** 列出某个版本的评测运行（裁剪 VO，按时间倒序） */
export function listRun(targetType: string, targetVersionId: string | number): AxiosPromise<AigEvaluationRunVO[]> {
  return request({
    url: '/aigov/evaluation/run/list',
    method: 'get',
    params: { targetType, targetVersionId }
  });
}

/** 查运行详情（含打分明细原文） */
export function getRun(runId: string | number): AxiosPromise<AigEvaluationRunDetail> {
  return request({
    url: '/aigov/evaluation/run/' + runId,
    method: 'get'
  });
}

/** 跑整组黄金用例（用例集合必须与版本 config_json 声明的集合一致） */
export function runEvaluation(data: AigEvaluationRunForm): AxiosPromise<AigEvaluationRunDetail[]> {
  return request({
    url: '/aigov/evaluation/run',
    method: 'post',
    data: data
  });
}

/** 提交人工复核结论（Rubric 用例的最后一道判断） */
export function reviewRun(data: AigEvaluationReviewForm) {
  return request({
    url: '/aigov/evaluation/review',
    method: 'post',
    data: data
  });
}

/**
 * 人工评测录入（平台没有该对象的执行器时，由管理员产出黄金用例证据）。
 * 录入的行一律带 executed_by=ADMIN，与机器结论在库里、在证据里都能分开看。
 */
export function manualRun(data: AigEvaluationManualRunForm): AxiosPromise<AigEvaluationRunDetail[]> {
  return request({
    url: '/aigov/evaluation/manual-run',
    method: 'post',
    data: data
  });
}

/** 「黄金用例是否通过」的证据（发布门槛 GOLDEN_CASE 的判据来源） */
export function caseEvidence(targetType: string, targetVersionId: string | number): AxiosPromise<AigGoldenCaseEvidence> {
  return request({
    url: '/aigov/evaluation/evidence',
    method: 'get',
    params: { targetType, targetVersionId }
  });
}

/** 版本声明的黄金用例集合（跑评测要按它跑；页面读不到 config_json，因此由后端给出） */
export function declaredCases(targetType: string, targetVersionId: string | number): AxiosPromise<string[]> {
  return request({
    url: '/aigov/evaluation/declared-cases',
    method: 'get',
    params: { targetType, targetVersionId }
  });
}
