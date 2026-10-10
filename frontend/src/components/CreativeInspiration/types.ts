import type { ImageCapabilityCode } from '@/api/image/types';
import type { ImageCloudCapability } from './cloud-image-capabilities';
import type { GeneratedInspiration } from '@/api/image/inspiration';
import type { CloudImageModelsVO } from '@/api/image/types';
import { imageCapabilityVerified } from './cloud-image-capabilities';
import type { VideoCapabilityCode } from '@/api/video/types';

export type CreativeMedia = 'image' | 'video';
export type GenerationSource = 'local' | 'cloud';

export interface InspirationRoute {
  media: CreativeMedia;
  source?: GenerationSource;
  capability: ImageCapabilityCode | VideoCapabilityCode | ImageCloudCapability;
  workflowCode: string;
  model: string;
  prompt: string;
  reason: string;
  referenceHint: string;
  output?: import('@/api/image/types').CloudImageOutputParams;
  templateTitle?: string;
}

export interface CoverRegion {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface InspirationBase {
  id: string;
  title: string;
  category: string;
  tags: string[];
  routes: InspirationRoute[];
}

export interface ImageInspirationWork extends InspirationBase {
  media: 'image';
  /** 用户提供的参考截图中的展示区域，不是任务产出。 */
  cover: CoverRegion;
  assetId?: string;
  provenance?: GeneratedInspiration;
}

export interface VideoReference {
  src: string;
  poster: string;
  sourceName: string;
  sourceUrl: string;
}

export interface VideoInspirationWork extends InspirationBase {
  media: 'video';
  video: VideoReference;
}

export type InspirationWork = ImageInspirationWork | VideoInspirationWork;

/** 复用现有服务端能力接口的安全视图。 */
export interface InspirationWorkflow {
  workflowCode: string;
  modelCode?: string;
  capabilityCode: string;
  submittable: boolean;
  status: string;
}

/** 本地提交仍由服务端工作流发布状态决定。 */
export function canSubmitLocal(
  source: GenerationSource,
  workflow?: Pick<InspirationWorkflow, 'status' | 'submittable'>
): boolean {
  return source === 'local' && workflow?.status === 'PUBLISHED' && workflow.submittable === true;
}

export function isRouteAvailable(route: InspirationRoute, workflows: InspirationWorkflow[], cloudStatus?: CloudImageModelsVO): boolean {
  if (route.source === 'cloud') return Boolean(cloudStatus?.configured && cloudStatus.models.includes(route.model)
    && imageCapabilityVerified(cloudStatus, route.model, route.capability));
  return workflows.some(
    workflow =>
      workflow.workflowCode === route.workflowCode &&
      workflow.capabilityCode === route.capability &&
      canSubmitLocal('local', workflow)
  );
}
