import { describe, it, expect } from 'vitest';
import { VIDEO_CLOUD_PROFILES } from './cloud-models';
import {
  calculateVideoCost,
  getVideoPrice,
  VIDEO_PRICING,
  videoRateLabel,
  videoStartingRate,
  videoOptionQuote,
  videoPriceGrid,
  formatCnyFromUsd
} from './cloud-pricing';
const draft = (model: string, resolution: string, seconds: number) => ({ model, resolution, seconds, references: [] });
describe('Cloud video fee estimates', () => {
  it.each(VIDEO_CLOUD_PROFILES.map(p => [p.id, p] as const))(
    '%s has pricing for every exposed resolution',
    (_id, p) => {
      for (const resolution of p.resolutions) {
        const q = calculateVideoCost(draft(p.id, resolution, p.durations[0]));
        expect(q).toBeDefined();
        expect(q!.estimatedUsd).toBeGreaterThan(0);
        expect(q!.multiplier).toBe(VIDEO_PRICING.groupRatio);
      }
    }
  );
  it('seconds and selected resolution change the quote', () => {
    expect(calculateVideoCost(draft('kling-3.0-turbo', '720p', 5))?.estimatedUsd).toBeCloseTo(0.55);
    expect(calculateVideoCost(draft('kling-3.0-turbo', '720p', 10))?.estimatedUsd).toBeCloseTo(1.1);
    expect(calculateVideoCost(draft('kling-3.0-turbo', '1080p', 5))?.estimatedUsd).toBeCloseTo(0.815);
  });
  it('Seedance uses its published billing-token example instead of a per-second tariff', () => {
    const q = calculateVideoCost(draft('doubao-seedance-2-0-fast-260128', '720p', 5))!;
    expect(q.estimatedTokens).toBe(108000);
    expect(q.unitPrice).toBe(2.56);
    expect(q.estimatedUsd).toBeCloseTo(0.27648);
    expect(q.approximate).toBe(true);
  });
  it('uses group multiplier without multiplying by legacy model_ratio again', () => {
    const d = draft('wan2.7-t2v', '720p', 5);
    expect(calculateVideoCost(d)?.estimatedUsd).toBeCloseTo(0.408);
    expect(calculateVideoCost(d, getVideoPrice(d.model), 2)?.estimatedUsd).toBeCloseTo(0.816);
  });
  it('reference video cost remains explicitly incomplete until its extra tokens are known', () => {
    const d = {
      ...draft('doubao-seedance-2-5-260628', '720p', 5),
      references: [{ assetId: '1', role: 'reference_video' as const }]
    };
    const q = calculateVideoCost(d)!;
    expect(q.referenceVideoExtra).toBe(true);
    expect(q.estimatedTokens).toBe(108000);
  });
  it('never shows a zero quote for missing or invalid price data', () => {
    expect(calculateVideoCost(draft('missing', '720p', 5))).toBeUndefined();
    expect(calculateVideoCost(draft('wan2.7-t2v', 'unknown', 5))).toBeUndefined();
    expect(calculateVideoCost(draft('wan2.7-t2v', '720p', -1))).toBeUndefined();
    expect(calculateVideoCost(draft('wan2.7-t2v', '720p', 5), undefined, NaN)).toBeUndefined();
  });
  it('mode labels and group starting rates reflect the selected model tariff', () => {
    const profiles = VIDEO_CLOUD_PROFILES.filter(p => p.group === 'Seedance 2.0');
    expect(videoStartingRate(profiles)).toContain(formatCnyFromUsd(1.6));
    const turbo = VIDEO_CLOUD_PROFILES.find(p => p.id === 'kling-3.0-turbo')!;
    expect(videoRateLabel(turbo, '1080p')).toContain(formatCnyFromUsd(0.163));
  });
});

describe('Video capability and output-option quotes', () => {
  const current = { ...draft('kling-3.0-turbo', '720p', 5), capability: 'T2V' };
  it.each(VIDEO_CLOUD_PROFILES.map(p => [p.id, p] as const))(
    '%s covers every supported duration, resolution and capability',
    (_id, profile) => {
      for (const capability of profile.capabilities) {
        const grid = videoPriceGrid(profile, { ...current, capability });
        expect(grid).toHaveLength(profile.durations.length);
        expect(grid.flatMap(r => r.cells)).toHaveLength(profile.durations.length * profile.resolutions.length);
        for (const row of grid)
          for (const cell of row.cells) {
            expect(cell.quote?.estimatedUsd).toBeGreaterThan(0);
            expect(cell.quote?.multiplier).toBe(VIDEO_PRICING.groupRatio);
          }
      }
    }
  );
  it('quotes a different mode with that mode price and normalizes unsupported options', () => {
    const profile = VIDEO_CLOUD_PROFILES.find(p => p.id === 'doubao-seedance-2-0-mini-260615')!;
    const q = videoOptionQuote(profile, { ...current, seconds: 30, resolution: '4k' })!;
    expect(q.unitPrice).toBe(1.6);
    expect(q.estimatedUsd).toBeGreaterThan(0);
    expect(current.model).toBe('kling-3.0-turbo');
    expect(current.seconds).toBe(5);
  });
  it('rejects unsupported explicit options rather than advertising invalid combinations', () => {
    const profile = VIDEO_CLOUD_PROFILES[0];
    expect(videoOptionQuote(profile, current, { seconds: 30 })).toBeUndefined();
    expect(videoOptionQuote(profile, current, { resolution: '4k' })).toBeUndefined();
    expect(videoOptionQuote(profile, current, { capability: 'R2V' })).toBeUndefined();
  });
  it('only includes uploaded reference video surcharge markers for the matching model and capability', () => {
    const profile = VIDEO_CLOUD_PROFILES.find(p => p.id === 'doubao-seedance-2-5-260628')!;
    const d = {
      ...current,
      model: profile.id,
      capability: 'R2V',
      references: [{ assetId: '1', role: 'reference_video' as const }]
    };
    expect(videoOptionQuote(profile, d)?.referenceVideoExtra).toBe(true);
    expect(videoOptionQuote(profile, d, { capability: 'T2V' })?.referenceVideoExtra).toBe(false);
    const other = VIDEO_CLOUD_PROFILES.find(p => p.id === 'doubao-seedance-2-0-260128')!;
    expect(videoOptionQuote(other, d)?.referenceVideoExtra).toBe(false);
  });
  it('does not add a capability surcharge absent from the provider expression', () => {
    const profile = VIDEO_CLOUD_PROFILES[0];
    expect(videoOptionQuote(profile, current, { capability: 'I2V' })?.estimatedUsd).toBe(
      videoOptionQuote(profile, current, { capability: 'T2V' })?.estimatedUsd
    );
    expect(videoOptionQuote(profile, current, { capability: 'FL2V' })?.multiplier).toBe(1);
  });
});

describe('Provider RMB display conversion', () => {
  it('converts with the provider CNY display rate, without changing USD billing or group ratio', () => {
    const d = draft('wan2.7-t2v', '720p', 5);
    const before = structuredClone(d);
    const q = calculateVideoCost(d)!;
    expect(q.unitPrice).toBe(0.0816);
    expect(q.estimatedUsd).toBeCloseTo(0.408);
    expect(q.multiplier).toBe(1);
    expect(q.unitLabel).toBe('元 / 视频秒');
    expect(formatCnyFromUsd(q.estimatedUsd)).toBe('¥2.7398');
    expect(q.formula).toContain('¥0.548');
    expect(q.formula).not.toContain('$');
    expect(d).toEqual(before);
    expect(VIDEO_PRICING.displayCurrency.rateField).toBe('usd_cny_rate');
    expect(VIDEO_PRICING.displayCurrency.usdToCny).toBe(6.715255);
  });
  it.each(VIDEO_CLOUD_PROFILES.map(p => [p.id, p] as const))('%s displays RMB for every option', (_id, p) => {
    expect(videoStartingRate([p])).toContain('¥');
    expect(videoStartingRate([p])).not.toContain('$');
    for (const resolution of p.resolutions) {
      expect(videoRateLabel(p, resolution)).toContain('¥');
      const grid = videoPriceGrid(p, { ...draft(p.id, resolution, p.durations[0]), capability: p.capabilities[0] });
      for (const row of grid) for (const cell of row.cells) {
        expect(cell.quote!.formula).toContain('¥');
        expect(cell.quote!.formula).not.toContain('$');
        expect(cell.quote!.unitLabel).toContain('元');
      }
    }
  });
  it('converts token tariffs and reference-video base fees once, retaining incomplete-cost markers', () => {
    const q = calculateVideoCost({ ...draft('doubao-seedance-2-5-260628', '720p', 5), references: [{assetId: '1', role: 'reference_video' as const}] })!;
    expect(q.unitLabel).toBe('元 / 百万计费 Token');
    expect(q.estimatedTokens).toBe(108000);
    expect(q.referenceVideoExtra).toBe(true);
    expect(q.approximate).toBe(true);
    expect(formatCnyFromUsd(q.unitPrice)).toBe(formatCnyFromUsd(4.84));
    expect(formatCnyFromUsd(q.estimatedUsd)).toBe(formatCnyFromUsd(4.84 * 0.108));
  });
});
