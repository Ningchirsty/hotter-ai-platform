import type { ImageCapabilityCode } from '@/api/image/types';
import type { VideoCapabilityCode } from '@/api/video/types';

export type CreativeMedia = 'image' | 'video';
export type GenerationSource = 'local' | 'cloud';

export interface InspirationRoute {
  media: CreativeMedia;
  capability: ImageCapabilityCode | VideoCapabilityCode;
  workflowCode: string;
  model: string;
  prompt: string;
  reason: string;
  referenceHint: string;
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
  capabilityCode: string;
  submittable: boolean;
  status: string;
}

/** 云端未接入；本地提交仍由服务端工作流状态决定。 */
export function canSubmitLocal(
  source: GenerationSource,
  workflow?: Pick<InspirationWorkflow, 'status' | 'submittable'>
): boolean {
  return source === 'local' && workflow?.status === 'PUBLISHED' && workflow.submittable === true;
}

export function isRouteAvailable(route: InspirationRoute, workflows: InspirationWorkflow[]): boolean {
  return workflows.some(
    workflow =>
      workflow.workflowCode === route.workflowCode &&
      workflow.capabilityCode === route.capability &&
      canSubmitLocal('local', workflow)
  );
}
