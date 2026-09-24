import type { PageResult } from '@/api/types';
import type { CpTaskFileVO } from '@/api/content/task/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  CreativeDnaForm,
  CreativeDirectionForm,
  CreativeHeroForm,
  CreativeProjectForm,
  CreativeProjectQuery,
  CreativeProjectVO,
  CreativeScreenForm,
  CreativeWorkflowVO,
  DnaPromptVO,
  DpGenerationVO,
  DpLayoutTemplateVO,
  DpDetailPageVO,
  DpStageEventVO,
  DpStoryboardVO,
  DpVisualDirectionVO,
  DpVisualDnaVO,
  DnaRecommendationVO,
  GateEvaluationVO,
  ProductionRunVO,
  ProjectProductImageVO
} from './types';

// ------------------------------------------------------------------
// 视觉项目（= ECOM_DETAIL 的内容协同任务）
// ------------------------------------------------------------------

/** 视觉项目分页（后端强制 deliverableType=ECOM_DETAIL） */
export function listCreativeProject(query: CreativeProjectQuery): AxiosPromise<PageResult<CreativeProjectVO>> {
  return request({
    url: '/creative/projects/list',
    method: 'get',
    params: query
  });
}

/** 视觉项目详情 */
export function getCreativeProject(taskId: string | number): AxiosPromise<CreativeProjectVO> {
  return request({
    url: '/creative/projects/' + taskId,
    method: 'get'
  });
}

/** 新建视觉项目 */
export function addCreativeProject(data: CreativeProjectForm): AxiosPromise<string | number> {
  return request({
    url: '/creative/projects',
    method: 'post',
    data: data
  });
}

/**
 * 上传项目参考图（表单字段名固定 file）。
 *
 * @param asProductImage 勾选「同时设为该产品的产品图」时传 true：
 *                       后端会在同一次调用里把该附件写回 cp_product.product_image。
 *                       项目没有关联产品时后端会直接拒绝（不落孤儿图）。
 */
export function uploadCreativeReference(
  taskId: string | number,
  file: File,
  asProductImage?: boolean
): AxiosPromise<string | number> {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: '/creative/projects/' + taskId + '/reference',
    method: 'post',
    params: asProductImage ? { asProductImage: true } : undefined,
    data: formData
  });
}

/** 项目附件列表（含参考图；sourceType 区分 上传图/参考图/产品图/生成图） */
export function listCreativeFiles(taskId: string | number): AxiosPromise<CpTaskFileVO[]> {
  return request({
    url: '/creative/projects/' + taskId + '/files',
    method: 'get'
  });
}

/**
 * 项目所属产品的产品图信息。
 *
 * 只有 configured === true 才表示该产品真的有产品图；未配置时 note 里写清了怎么补，
 * 页面如实照搬，不猜。
 */
export function getProjectProductImage(taskId: string | number): AxiosPromise<ProjectProductImageVO> {
  return request({
    url: '/creative/projects/' + taskId + '/product-image',
    method: 'get'
  });
}

/** 把项目里已有的某个附件登记为该产品的产品图 */
export function bindProjectProductImage(
  taskId: string | number,
  fileId: string | number
): AxiosPromise<ProjectProductImageVO> {
  return request({
    url: '/creative/projects/' + taskId + '/product-image',
    method: 'post',
    data: { fileId: fileId }
  });
}

/** 项目阶段事件时间线 */
export function listCreativeTimeline(taskId: string | number): AxiosPromise<DpStageEventVO[]> {
  return request({
    url: '/creative/projects/' + taskId + '/timeline',
    method: 'get'
  });
}

// ------------------------------------------------------------------
// 出图生产
// ------------------------------------------------------------------

/** 提交一次 HERO 出图 */
export function submitHero(taskId: string | number, data: CreativeHeroForm): AxiosPromise<DpGenerationVO> {
  return request({
    url: '/creative/projects/' + taskId + '/generations',
    method: 'post',
    data: data
  });
}

/** 项目出图候选列表（返回前会刷新内核状态） */
export function listGenerations(taskId: string | number): AxiosPromise<DpGenerationVO[]> {
  return request({
    url: '/creative/projects/' + taskId + '/generations',
    method: 'get'
  });
}

/** 重试失败候选（新建一次候选） */
export function retryGeneration(generationId: string | number): AxiosPromise<DpGenerationVO> {
  return request({
    url: '/creative/generations/' + generationId + '/retry',
    method: 'post'
  });
}

/** 可用出图工作流 */
export function listCreativeWorkflows(): AxiosPromise<CreativeWorkflowVO[]> {
  return request({
    url: '/creative/workflows',
    method: 'get'
  });
}

/** 跨项目候选分页（AI 生产中心） */
export function listProductions(query: {
  pageNum?: number;
  pageSize?: number;
  status?: string;
}): AxiosPromise<PageResult<DpGenerationVO>> {
  return request({
    url: '/creative/productions',
    method: 'get',
    params: query
  });
}

// ------------------------------------------------------------------
// Visual DNA（视觉基因）
// ------------------------------------------------------------------

/** 生成一版视觉基因 */
export function generateDna(taskId: string | number): AxiosPromise<DpVisualDnaVO> {
  return request({
    url: `/creative/projects/${taskId}/dna/generate`,
    method: 'post'
  });
}

/** 最新一版视觉基因（未生成过时 data 为 null） */
export function getDna(taskId: string | number): AxiosPromise<DpVisualDnaVO | null> {
  return request({
    url: `/creative/projects/${taskId}/dna`,
    method: 'get'
  });
}

/** 按参考图推荐规范内容（不落库，返回推荐值 + 逐字段依据） */
export function recommendDna(taskId: string | number): AxiosPromise<DnaRecommendationVO> {
  return request({
    url: `/creative/projects/${taskId}/dna/recommend`,
    method: 'post',
    timeout: 60000
  });
}

/** 视觉基因版本列表（倒序） */
export function listDnaVersions(taskId: string | number): AxiosPromise<DpVisualDnaVO[]> {
  return request({
    url: `/creative/projects/${taskId}/dna/versions`,
    method: 'get'
  });
}

/** 保存视觉基因编辑（已锁定版本会自动新建版本） */
export function saveDna(taskId: string | number, data: CreativeDnaForm): AxiosPromise<DpVisualDnaVO> {
  return request({
    url: `/creative/projects/${taskId}/dna`,
    method: 'put',
    data: data
  });
}

/** 锁定视觉基因（锁定前必须通过自洽校验） */
export function lockDna(taskId: string | number, dnaId?: string | number): AxiosPromise<DpVisualDnaVO> {
  return request({
    url: `/creative/projects/${taskId}/dna/lock`,
    method: 'post',
    params: dnaId ? { dnaId } : undefined
  });
}

/** 按当前生效基因派生提示词 */
export function getDnaPrompt(taskId: string | number, screenHint?: string): AxiosPromise<DnaPromptVO> {
  return request({
    url: `/creative/projects/${taskId}/dna/prompt`,
    method: 'get',
    params: screenHint ? { screenHint } : undefined
  });
}

// ------------------------------------------------------------------
// 视觉方向 / 分镜 / 视觉门
// ------------------------------------------------------------------

/** 生成 A/B/C 视觉方向 */
export function generateDirections(taskId: string | number): AxiosPromise<DpVisualDirectionVO[]> {
  return request({ url: `/creative/projects/${taskId}/directions/generate`, method: 'post' });
}

/** 方向列表 */
export function listDirections(taskId: string | number): AxiosPromise<DpVisualDirectionVO[]> {
  return request({ url: `/creative/projects/${taskId}/directions`, method: 'get' });
}

/** 选定方向 */
export function selectDirection(taskId: string | number, directionId: string | number) {
  return request({
    url: `/creative/projects/${taskId}/directions/${directionId}/select`,
    method: 'post'
  });
}

/** 编辑方向文案 */
export function updateDirection(taskId: string | number, data: CreativeDirectionForm) {
  return request({ url: `/creative/projects/${taskId}/directions`, method: 'put', data });
}

/** 生成一版分镜 */
export function generateStoryboard(taskId: string | number): AxiosPromise<DpStoryboardVO> {
  return request({ url: `/creative/projects/${taskId}/storyboard/generate`, method: 'post' });
}

/** 最新一版分镜（含屏） */
export function getStoryboard(taskId: string | number): AxiosPromise<DpStoryboardVO | null> {
  return request({ url: `/creative/projects/${taskId}/storyboard`, method: 'get' });
}

/** 分镜版本列表 */
export function listStoryboardVersions(taskId: string | number): AxiosPromise<DpStoryboardVO[]> {
  return request({ url: `/creative/projects/${taskId}/storyboard/versions`, method: 'get' });
}

/** 编辑一屏 */
export function updateScreen(taskId: string | number, data: CreativeScreenForm) {
  return request({ url: `/creative/projects/${taskId}/storyboard/screen`, method: 'put', data });
}

/** 锁定分镜 */
export function lockStoryboard(taskId: string | number, storyboardId?: string | number) {
  return request({
    url: `/creative/projects/${taskId}/storyboard/lock`,
    method: 'post',
    params: storyboardId ? { storyboardId } : undefined
  });
}

/** 视觉门评估 */
export function getVisualGate(taskId: string | number): AxiosPromise<GateEvaluationVO> {
  return request({ url: `/creative/projects/${taskId}/visual-gate`, method: 'get' });
}

/** 提交视觉门审核 */
export function submitVisualGate(taskId: string | number): AxiosPromise<GateEvaluationVO> {
  return request({ url: `/creative/projects/${taskId}/visual-gate/submit`, method: 'post' });
}

/** 处理视觉门（人工确认或打回） */
export function reviewVisualGate(
  taskId: string | number,
  option: 'CONFIRM' | 'BLOCK',
  comment?: string
): AxiosPromise<GateEvaluationVO> {
  return request({
    url: `/creative/projects/${taskId}/visual-gate/review`,
    method: 'post',
    params: { option, comment }
  });
}

// ------------------------------------------------------------------
// R2：逐屏批量生产、选定、QA
// ------------------------------------------------------------------

/** 按已锁定分镜逐屏批量出图 */
export function startProduction(taskId: string | number, force = false): AxiosPromise<ProductionRunVO> {
  return request({
    url: `/creative/projects/${taskId}/production/start`,
    method: 'post',
    params: { force }
  });
}

/** 刷新生产状态（候选状态 / QA 结论 / 自动重试） */
export function refreshProduction(taskId: string | number): AxiosPromise<ProductionRunVO> {
  return request({ url: `/creative/projects/${taskId}/production/refresh`, method: 'post' });
}

/** 单屏重生成 */
export function regenerateScreen(taskId: string | number, screenId: string | number) {
  return request({
    url: `/creative/projects/${taskId}/screens/${screenId}/regenerate`,
    method: 'post'
  });
}

/** 选定候选（人动作；自动登记产出并发起质检） */
export function selectCandidate(taskId: string | number, generationId: string | number) {
  return request({
    url: `/creative/projects/${taskId}/generations/${generationId}/select`,
    method: 'post'
  });
}

/** 发起质检（只筛除不放行） */
export function runCandidateQa(taskId: string | number, generationId: string | number) {
  return request({
    url: `/creative/projects/${taskId}/generations/${generationId}/qa`,
    method: 'post'
  });
}

// ------------------------------------------------------------------
// R3：模板库与详情页排版
// ------------------------------------------------------------------

/** 模板列表（含与渲染服务的实时校验和对账） */
export function listLayoutTemplates(): AxiosPromise<DpLayoutTemplateVO[]> {
  return request({ url: '/creative/templates', method: 'get' });
}

/** 与渲染服务对账（登记新模板；校验和变化则退回草稿） */
export function syncLayoutTemplates(): AxiosPromise<DpLayoutTemplateVO[]> {
  return request({ url: '/creative/templates/sync', method: 'post' });
}

/** 发布模板 */
export function publishLayoutTemplate(templateId: string | number) {
  return request({ url: `/creative/templates/${templateId}/publish`, method: 'post' });
}

/** 退役模板 */
export function retireLayoutTemplate(templateId: string | number) {
  return request({ url: `/creative/templates/${templateId}/retire`, method: 'post' });
}

/** 详情页与版本列表 */
export function getDetailPage(taskId: string | number): AxiosPromise<DpDetailPageVO> {
  return request({ url: `/creative/projects/${taskId}/detail-page`, method: 'get' });
}

/** 渲染一版机排版 V0.8（同步等待渲染完成） */
export function renderDetailPage(taskId: string | number): AxiosPromise<DpDetailPageVO> {
  return request({ url: `/creative/projects/${taskId}/detail-page/render`, method: 'post', timeout: 180000 });
}

/** 终审：通过或打回 */
export function reviewDetailVersion(
  taskId: string | number,
  versionId: string | number,
  approve: boolean,
  comment?: string
) {
  return request({
    url: `/creative/projects/${taskId}/detail-page/versions/${versionId}/review`,
    method: 'post',
    params: { approve, comment }
  });
}

/** 上传人工精修后的最终版 V1.0 */
export function uploadDetailFinal(
  taskId: string | number,
  file: File,
  comment?: string
): AxiosPromise<DpDetailPageVO> {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: `/creative/projects/${taskId}/detail-page/final`,
    method: 'post',
    params: comment ? { comment } : undefined,
    data: formData
  });
}

/** 取版本长图 blob URL（调用方负责 revokeObjectURL） */
export const fetchDetailPreviewBlobUrl = async (
  taskId: string | number,
  versionId: string | number
): Promise<string> => {
  const res = await request({
    url: `/creative/projects/${taskId}/detail-page/versions/${versionId}/preview`,
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '长图');
};

/**
 * 把返回体转成可直接放进 `<img>` 的 blob URL。
 *
 * 为什么要判空与判 JSON：若依在未鉴权时返回 HTTP 200 + `{"code":401,...}`，
 * 直接 `URL.createObjectURL` 会得到一个「坏图」而不是可读的错误。
 */
const toMediaBlobUrl = async (data: unknown, what: string): Promise<string> => {
  const blob = data as Blob;
  if (!blob || blob.size === 0) {
    throw new Error(what + '内容为空');
  }
  const type = blob.type || '';
  if (type.includes('application/json') || type.startsWith('text/')) {
    const text = await blob.text();
    let message = text;
    try {
      const parsed = JSON.parse(text) as { msg?: string };
      message = parsed.msg || text;
    } catch {
      /* 保持原文 */
    }
    throw new Error(message || '读取' + what + '失败');
  }
  return URL.createObjectURL(blob);
};

/** 取候选缩略图 blob URL（失败时调用方退回原图；调用方负责 revokeObjectURL） */
export const fetchGenerationThumbnailBlobUrl = async (generationId: string | number): Promise<string> => {
  const res = await request({
    url: '/creative/generations/' + generationId + '/thumbnail',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '缩略图');
};

/** 取候选原图 blob URL（调用方负责 revokeObjectURL） */
export const fetchGenerationPreviewBlobUrl = async (generationId: string | number): Promise<string> => {
  const res = await request({
    url: '/creative/generations/' + generationId + '/preview',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '产出图');
};

/** 取项目附件（参考图）原图 blob URL（调用方负责 revokeObjectURL） */
export const fetchCreativeFileBlobUrl = async (
  taskId: string | number,
  fileId: string | number
): Promise<string> => {
  const res = await request({
    url: '/creative/projects/' + taskId + '/files/' + fileId + '/content',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '参考图');
};

/**
 * 取该产品当前产品图的 blob URL（调用方负责 revokeObjectURL）。
 *
 * 与 fetchDetailPreviewBlobUrl / fetchCreativeFileBlobUrl 完全同构：只做「字节 → 对象URL」，
 * 不做任何自动回收——回收时机由页面决定，避免图片在渲染前就被 revoke。
 * 未配置产品图时后端会明确报错，页面照实显示，不静默留空。
 */
export const fetchProductImageBlobUrl = async (taskId: string | number): Promise<string> => {
  const res = await request({
    url: `/creative/projects/${taskId}/product-image/content`,
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '产品图');
};
