import type { VideoCloudDraft, VideoCloudProfile } from './cloud-models';
import snapshot from './cloud-pricing.json';

/** Public provider pricing snapshot. Quotes never alter generation request parameters. */
export const VIDEO_PRICING = snapshot;
export interface VideoPrice {
  model: string;
  unit: string;
  usdPerSecond: Record<string, number>;
  usdPerMillionTokens: number | null;
  sampleTokensPerSecond: Record<string, number>;
  channelPriceMayVary: boolean;
}
const prices: VideoPrice[] = snapshot.models;
export function getVideoPrice(model: string) {
  return prices.find(p => p.model === model);
}
export function formatUsd(amount: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 4
  }).format(amount);
}
export interface VideoCostQuote {
  unitPrice: number;
  unitLabel: string;
  multiplier: number;
  estimatedUsd: number;
  estimatedTokens?: number;
  formula: string;
  referenceVideoExtra: boolean;
  approximate: boolean;
  channelPriceMayVary: boolean;
}
export function calculateVideoCost(
  draft: Pick<VideoCloudDraft, 'model' | 'resolution' | 'seconds' | 'references'>,
  price: VideoPrice | undefined = getVideoPrice(draft.model),
  multiplier: number = snapshot.groupRatio
): VideoCostQuote | undefined {
  if (
    !price ||
    price.model !== draft.model ||
    !Number.isFinite(multiplier) ||
    multiplier < 0 ||
    !Number.isInteger(draft.seconds) ||
    draft.seconds <= 0
  )
    return;
  const resolution = draft.resolution.toLowerCase();
  const unitPrice = price.unit === 'second' ? price.usdPerSecond[resolution] : price.usdPerMillionTokens;
  if (typeof unitPrice !== 'number' || !Number.isFinite(unitPrice) || unitPrice < 0) return;
  const referenceVideoExtra =
    price.unit === 'million_tokens' && draft.references.some(r => r.role === 'reference_video');
  if (price.unit === 'second')
    return {
      unitPrice,
      unitLabel: '美元 / 视频秒',
      multiplier,
      estimatedUsd: unitPrice * draft.seconds * multiplier,
      formula: `${draft.seconds} 秒 × ${formatUsd(unitPrice)} / 秒 × ${multiplier} 倍`,
      referenceVideoExtra: false,
      approximate: false,
      channelPriceMayVary: price.channelPriceMayVary
    };
  if (price.unit !== 'million_tokens') return;
  const tokensPerSecond = price.sampleTokensPerSecond[resolution];
  if (!Number.isFinite(tokensPerSecond) || tokensPerSecond <= 0) return;
  const estimatedTokens = Math.ceil(tokensPerSecond * draft.seconds);
  return {
    unitPrice,
    unitLabel: '美元 / 百万计费 Token',
    multiplier,
    estimatedUsd: ((unitPrice * estimatedTokens) / 1000000) * multiplier,
    estimatedTokens,
    formula: `约 ${estimatedTokens.toLocaleString('zh-CN')} Token × ${formatUsd(unitPrice)} / 百万 Token × ${multiplier} 倍`,
    referenceVideoExtra,
    approximate: true,
    channelPriceMayVary: price.channelPriceMayVary
  };
}
export function videoRateLabel(profile: VideoCloudProfile, resolution: string) {
  const price = getVideoPrice(profile.id);
  if (!price) return '单价待公布';
  if (price.unit === 'million_tokens' && price.usdPerMillionTokens !== null)
    return `${formatUsd(price.usdPerMillionTokens)} / 百万 Token`;
  const effective = profile.resolutions.includes(resolution) ? resolution : profile.resolutions[0];
  const amount = price.usdPerSecond[effective];
  return typeof amount === 'number' ? `${effective.toUpperCase()} · ${formatUsd(amount)} / 秒` : '单价待公布';
}
export function videoStartingRate(profiles: VideoCloudProfile[]) {
  const list = profiles.map(p => getVideoPrice(p.id)).filter((p): p is VideoPrice => !!p);
  if (!list.length) return '单价待公布';
  if (list.every(p => p.unit === 'million_tokens'))
    return `${formatUsd(Math.min(...list.map(p => p.usdPerMillionTokens!)))} 起 / 百万 Token`;
  if (list.every(p => p.unit === 'second')) {
    const amounts = profiles
      .flatMap(p => p.resolutions.map(r => getVideoPrice(p.id)?.usdPerSecond[r]))
      .filter((v): v is number => typeof v === 'number' && Number.isFinite(v));
    if (amounts.length) return `${formatUsd(Math.min(...amounts))} 起 / 秒`;
  }
  return '按所选型号计费';
}

/** Quote a supported option without changing the current form or carrying unrelated references across models. */
export function videoOptionQuote(
  profile: VideoCloudProfile,
  draft: Pick<VideoCloudDraft, 'model' | 'capability' | 'resolution' | 'seconds' | 'references'>,
  option: Partial<Pick<VideoCloudDraft, 'capability' | 'resolution' | 'seconds'>> = {}
): VideoCostQuote | undefined {
  const capability =
    option.capability ?? (profile.capabilities.includes(draft.capability) ? draft.capability : profile.capabilities[0]);
  const resolution =
    option.resolution ?? (profile.resolutions.includes(draft.resolution) ? draft.resolution : profile.resolutions[0]);
  const seconds = option.seconds ?? (profile.durations.includes(draft.seconds) ? draft.seconds : profile.durations[0]);
  if (
    !profile.capabilities.includes(capability) ||
    !profile.resolutions.includes(resolution) ||
    !profile.durations.includes(seconds)
  )
    return;
  return calculateVideoCost({
    model: profile.id,
    resolution,
    seconds,
    references: profile.id === draft.model && capability === draft.capability ? draft.references : []
  });
}

export function videoPriceGrid(
  profile: VideoCloudProfile,
  draft: Pick<VideoCloudDraft, 'model' | 'capability' | 'resolution' | 'seconds' | 'references'>
) {
  return profile.durations.map(seconds => ({
    seconds,
    cells: profile.resolutions.map(resolution => ({
      resolution,
      quote: videoOptionQuote(profile, draft, { seconds, resolution })
    }))
  }));
}
