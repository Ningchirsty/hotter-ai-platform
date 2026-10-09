import type { AxiosPromise } from '@/utils/api-types';
import { VIDEO_CLOUD_PROFILES, type VideoCloudDraft, type VideoCloudStatus } from './cloud-models';
export * from './cloud-models';

/** Frontend-only release: cloud video generation requires a separately verified backend release. */
export const listVideoCloudModels = (): AxiosPromise<VideoCloudStatus> =>
  Promise.resolve({
    code: 200,
    data: { configured: false, referenceDeliveryConfigured: false, profiles: VIDEO_CLOUD_PROFILES, verifiedModels: [] }
  });
export const createVideoCloudTask = (_data: VideoCloudDraft): AxiosPromise<{ taskId: string | number }> =>
  Promise.reject(new Error('云端视频服务待接入，当前仅支持参数与计费预览'));
export const uploadVideoCloudAsset = (_file: File): AxiosPromise<{ assetId: string | number }> =>
  Promise.reject(new Error('云端视频参考素材上传待服务接入后开放'));
