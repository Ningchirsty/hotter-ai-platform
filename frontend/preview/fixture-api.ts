/** Local, read-only preview fixtures. No HTTP client or production endpoint is imported. */
import workflows from './workflow-fixtures.json';
const ok = async <T>(data: T) => ({ data });
const blocked = async (..._args: unknown[]): Promise<never> => {
  throw new Error('只读界面预览：不上传、不生成、不修改服务器数据。');
};
const noMedia = async (..._args: unknown[]): Promise<string> => {
  throw new Error('界面样例没有实际产出文件；正式环境由原有鉴权接口读取。');
};
export const extractErrorMessage = async (error: unknown) => (error instanceof Error ? error.message : undefined);
export const listVideoWorkflows = () => ok(workflows.video);
export const listImageWorkflows = () => ok(workflows.image);
const videoTasks = ['SUCCEEDED', 'RUNNING', 'QUEUED', 'FAILED'].map((status, i) => ({
  id: 'preview-video-' + i,
  taskNo: 'PREVIEW-V-' + (i + 1),
  taskName: '界面样例 · ' + ['已完成任务', '生成中任务', '排队任务', '失败任务'][i],
  capabilityCode: ['T2V', 'I2V', 'FL2V', 'T2V'][i],
  workflowCode: ['wf-t2v-h3', 'wf-i2v-h3', 'wf-fl2v-h3', 'wf-t2v-h3'][i],
  modelCode: 'H3',
  status,
  tier: '高清 · 1080P',
  durationSeconds: 5,
  progress: status === 'RUNNING' ? 45 : status === 'SUCCEEDED' ? 100 : 0,
  createTime: '2026-10-06 15:00:00',
  comfyWorker: status === 'RUNNING' ? '预览样例节点' : null,
  errorMessage: status === 'FAILED' ? '界面样例：展示失败原因和重新执行入口。' : null
}));
const imageTasks = ['SUCCEEDED', 'RUNNING', 'QUEUED', 'FAILED'].map((status, i) => ({
  id: 'preview-image-' + i,
  taskNo: 'PREVIEW-I-' + (i + 1),
  taskName: '界面样例 · ' + ['已完成任务', '生成中任务', '排队任务', '失败任务'][i],
  capabilityCode: ['T2I', 'EDIT', 'WHITEBG', 'I2I'][i],
  workflowCode: ['wf-t2i-qwen21', 'wf-edit-qwen21', 'wf-whitebg-qwen21', 'wf-i2i-qwen21'][i],
  modelCode: 'QWEN21',
  status,
  sizeLabel: '1:1 方图 · 1MP（1024×1024）',
  progress: status === 'RUNNING' ? 40 : status === 'SUCCEEDED' ? 100 : 0,
  outputWidth: status === 'SUCCEEDED' ? 1024 : null,
  outputHeight: status === 'SUCCEEDED' ? 1024 : null,
  createTime: '2026-10-06 15:00:00',
  errorMessage: status === 'FAILED' ? '界面样例：展示失败原因。' : null
}));
const page = <T>(rows: T[]) => ok({ rows, total: rows.length });
export const listVideoTasks = (..._args: unknown[]) => page(videoTasks);
export const listImageTasks = (..._args: unknown[]) => page(imageTasks);
const detail = <T>(task: T) =>
  ok({
    ...task,
    prompt: '界面样例，无实际生成任务。',
    attemptCount: 1,
    events: [{ sequence: 1, eventType: 'CREATED', detail: '只读预览记录', createTime: '2026-10-06 15:00:00' }]
  });
export const getVideoTask = (id: string | number) => detail(videoTasks.find(t => t.id === String(id)));
export const getImageTask = (id: string | number) => detail(imageTasks.find(t => t.id === String(id)));
const assetExamples = [
  {
    id: 'preview-asset-1',
    assetType: 'IMAGE',
    sourceKind: 'UPLOAD',
    originalName: '界面样例 · 参考图.png',
    contentType: 'image/png',
    sizeBytes: 1048576,
    width: 1024,
    height: 1024,
    createTime: '2026-10-06 15:00:00'
  }
];
export const listVideoAssets = (..._args: unknown[]) => page(assetExamples);
export const listImageAssets = (..._args: unknown[]) => page(assetExamples);
export const getVideoWorkers = () =>
  ok({
    workers: [{ name: '预览样例节点', baseUrl: '', busy: true, unavailable: false, cooldownSecondsLeft: 0 }],
    concurrency: 1,
    running: true,
    queued: 1,
    queueCapacity: 16
  });
export const fetchVideoAssetBlobUrl = noMedia;
export const fetchVideoAssetThumbnailBlobUrl = noMedia;
export const fetchImageAssetBlobUrl = noMedia;
export const fetchImageAssetThumbnailBlobUrl = noMedia;
// Preview uploads receive an ephemeral ID; files remain in the browser's blob URLs.
let previewAssetSequence = 0;
const previewUpload = async (_file: File, progress?: (percent: number) => void) => {
  progress?.(100);
  return ok({ assetId: 'preview-upload-' + (++previewAssetSequence) });
};
export const uploadVideoAsset = previewUpload;
export const deleteVideoAsset = blocked;
export const createVideoTask = blocked;
export const executeVideoTask = blocked;
export const retryVideoTask = blocked;
export const retryImageTask = blocked;
export const cancelVideoTask = blocked;
export const uploadImageAsset = previewUpload;
export const deleteImageAsset = blocked;
export const createImageTask = blocked;
export const executeImageTask = blocked;
export const cancelImageTask = blocked;

// 预览继续禁止产生真实任务；服务状态展示明确为未配置。
export const listCloudImageModels = () => ok({ configured: false, models: [], capabilities: ['T2I','EDIT','MULTI','MASK','OUTPAINT','TRANSPARENT'], verified: false,
  profiles: [{"model": "flux-2-pro", "testedAt": "2026-10-07", "maxReferenceImages": 0, "maxReferenceBytes": 10485760, "capabilities": [{"code": "T2I", "verified": false, "historicallyVerified": false, "status": "HTTP_503"}]}, {"model": "gpt-image-2.5-flare", "testedAt": "2026-10-07", "maxReferenceImages": 16, "maxReferenceBytes": 20971520, "capabilities": [{"code": "T2I", "verified": false, "historicallyVerified": true, "status": "RESULT_UNKNOWN"}, {"code": "EDIT", "verified": false, "historicallyVerified": false, "status": "UNVERIFIED"}, {"code": "MULTI", "verified": false, "historicallyVerified": false, "status": "UNVERIFIED"}, {"code": "MASK", "verified": false, "historicallyVerified": false, "status": "UNVERIFIED"}, {"code": "OUTPAINT", "verified": false, "historicallyVerified": false, "status": "UNVERIFIED"}, {"code": "TRANSPARENT", "verified": false, "historicallyVerified": false, "status": "UNVERIFIED"}]}, {"model": "gpt-image-2.5-sunburst", "testedAt": "2026-10-07", "maxReferenceImages": 16, "maxReferenceBytes": 20971520, "capabilities": [{"code": "T2I", "verified": true, "historicallyVerified": true, "status": "PASSED"}, {"code": "EDIT", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "MULTI", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "MASK", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "OUTPAINT", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "TRANSPARENT", "verified": true, "historicallyVerified": false, "status": "PASSED"}]}, {"model": "qwen-image-3.0", "testedAt": "2026-10-07", "maxReferenceImages": 3, "maxReferenceBytes": 10485760, "capabilities": [{"code": "T2I", "verified": false, "historicallyVerified": false, "status": "OUTPUT_UNVERIFIED"}, {"code": "EDIT", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "MULTI", "verified": true, "historicallyVerified": false, "status": "PASSED"}]}, {"model": "qwen-image-3.0-pro", "testedAt": "2026-10-07", "maxReferenceImages": 3, "maxReferenceBytes": 10485760, "capabilities": [{"code": "T2I", "verified": false, "historicallyVerified": false, "status": "OUTPUT_UNVERIFIED"}, {"code": "EDIT", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "MULTI", "verified": true, "historicallyVerified": false, "status": "PASSED"}]}, {"model": "wan2.7-image", "testedAt": "2026-10-07", "maxReferenceImages": 9, "maxReferenceBytes": 10485760, "capabilities": [{"code": "T2I", "verified": false, "historicallyVerified": false, "status": "OUTPUT_UNVERIFIED"}, {"code": "EDIT", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "MULTI", "verified": false, "historicallyVerified": false, "status": "HTTP_400"}]}, {"model": "wan2.7-image-pro", "testedAt": "2026-10-07", "maxReferenceImages": 9, "maxReferenceBytes": 10485760, "capabilities": [{"code": "T2I", "verified": false, "historicallyVerified": false, "status": "OUTPUT_UNVERIFIED"}, {"code": "EDIT", "verified": true, "historicallyVerified": false, "status": "PASSED"}, {"code": "MULTI", "verified": true, "historicallyVerified": false, "status": "PASSED"}]}]
});
export const checkCloudImageModels = blocked;
export const createCloudImageTask = blocked;

/** Read-only requests from the reused inspiration/cloud components. */
export default async function request(config: { url: string; method?: string; [key: string]: unknown }) {
  if ((config.method ?? 'get').toLowerCase() !== 'get') return blocked();
  if (config.url === '/video/cloud/models') return ok({ configured: false, models: [], profiles: [], verified: false, referenceDeliveryConfigured: false });
  if (config.url === '/image/inspirations') return ok({ rows: [], total: 0 });
  if (config.url === '/image/templates') return ok({ items: [], total: 0, categories: [], feed: { version: 1, online: false, checkedAt: '', syncCode: 'LOCAL_PREVIEW' } });
  throw new Error('本地预览未提供此数据接口');
}
