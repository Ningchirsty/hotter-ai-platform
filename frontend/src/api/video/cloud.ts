import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { VideoCloudDraft, VideoCloudStatus } from './cloud-models';
export * from './cloud-models';
export const listVideoCloudModels = (): AxiosPromise<VideoCloudStatus> =>
  request({ url: '/video/cloud/models', method: 'get' });
export const createVideoCloudTask = (data: VideoCloudDraft): AxiosPromise<{ taskId: string | number }> =>
  request({ url: '/video/cloud/tasks', method: 'post', data });
export const uploadVideoCloudAsset = (file: File): AxiosPromise<{ assetId: string | number }> => {
  const data = new FormData();
  data.append('file', file);
  return request({ url: '/video/cloud/assets', method: 'post', data, timeout: 180000 });
};
