import catalog from './cloud-catalog.json';
export const VIDEO_CLOUD_PROFILES = catalog.profiles;
export type VideoCloudProfile = (typeof VIDEO_CLOUD_PROFILES)[number];
export interface VideoCloudReference {
  assetId: string | number;
  role: 'first_frame' | 'last_frame' | 'reference_image' | 'reference_video' | 'reference_audio';
}
export interface VideoCloudDraft {
  model: string;
  capability: string;
  prompt: string;
  seconds: number;
  resolution: string;
  ratio: string;
  references: VideoCloudReference[];
  generateAudio?: boolean;
  negativePrompt?: string;
  seed?: number;
  idempotencyKey?: string;
}
export interface VideoCloudStatus {
  configured: boolean;
  referenceDeliveryConfigured: boolean;
  profiles: VideoCloudProfile[];
  verifiedVariants: string[];
  validationVariants: string[];
  validationRemaining: number;
}
export function normalizeVideoCloudDraft(draft: VideoCloudDraft, profile: VideoCloudProfile): VideoCloudDraft {
  return {
    ...draft,
    seed: profile.hasSeed ? draft.seed : undefined,
    generateAudio: profile.hasAudioOutput ? (draft.generateAudio ?? true) : undefined,
    negativePrompt: profile.family === 'Wan' ? draft.negativePrompt : undefined,
    model: profile.id,
    capability: profile.capabilities.includes(draft.capability) ? draft.capability : profile.capabilities[0],
    seconds: profile.durations.includes(draft.seconds) ? draft.seconds : profile.durations[0],
    resolution: profile.resolutions.includes(draft.resolution) ? draft.resolution : profile.resolutions[0],
    ratio:
      profile.family === 'MiniMax' && draft.capability === 'T2V' && draft.ratio === 'adaptive'
        ? '16:9'
        : profile.ratios.includes(draft.ratio)
          ? draft.ratio
          : profile.ratios[0]
  };
}

export function videoCloudVariant(draft: VideoCloudDraft): string {
  return [
    draft.model,
    draft.capability,
    draft.seconds,
    draft.resolution,
    draft.ratio,
    draft.generateAudio === true
  ].join('|');
}
export function videoCloudAccess(
  draft: VideoCloudDraft,
  status?: VideoCloudStatus | null
): 'verified' | 'validation' | 'pending' {
  if (!status?.configured || draft.seed !== undefined || draft.negativePrompt?.trim()) return 'pending';
  const key = videoCloudVariant(draft);
  if (status.verifiedVariants?.includes(key)) return 'verified';
  if (status.validationRemaining > 0 && status.validationVariants?.includes(key)) return 'validation';
  return 'pending';
}
