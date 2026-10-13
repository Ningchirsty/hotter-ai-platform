import { describe, it, expect } from 'vitest';
import { VIDEO_CLOUD_PROFILES as models, normalizeVideoCloudDraft, videoCloudAccess, type VideoCloudDraft } from './cloud-models';

describe('supported production video admission', () => {
  const status={configured:true,referenceDeliveryConfigured:true,profiles:models,verifiedVariants:[],validationVariants:[],validationRemaining:0,submissionPolicy:'supported' as const};
  it('allows every supported model/capability/specification independently of expired test quota',()=>{
    for(const profile of models.filter(p=>p.protocol !== 'unconfirmed')) for(const capability of profile.capabilities) for(const seconds of profile.durations) for(const resolution of profile.resolutions) for(const ratio of profile.ratios) {
      if(profile.family==='MiniMax' && capability==='T2V' && ratio==='adaptive')continue;
      expect(videoCloudAccess({model:profile.id,capability,seconds,resolution,ratio,prompt:'scene',references:[],generateAudio:profile.hasAudioOutput},status)).toBe('available');
    }
  });
  it('retains schema restrictions instead of treating any parameters as usable',()=>{
    const draft={model:models[0].id,capability:'T2V',seconds:3,resolution:'720p',ratio:'16:9',prompt:'scene',references:[]};
    expect(videoCloudAccess({...draft,seconds:999},status)).toBe('pending');
    expect(videoCloudAccess({...draft,generateAudio:true},status)).toBe('pending');
    expect(videoCloudAccess({...draft,seed:123},status)).toBe('pending');
    expect(videoCloudAccess(draft,{...status,configured:false})).toBe('pending');
  });
});
describe('Cloud video selection', () => {
  it('all model modes have Chinese display labels without changing API model IDs', () => {
    expect(models.every(m => /^[\u4e00-\u9fff]+$/.test(m.mode))).toBe(true);
    expect(models.find(m => m.id === 'kling-3.0-turbo')?.mode).toBe('极速');
    expect(models.find(m => m.id === 'kling-3.0-omni')?.mode).toBe('全能');
    expect(models.find(m => m.id === 'wan3.0-video-prime')?.mode).toBe('增强');
    expect(models.find(m => m.id === 'doubao-seedance-2-0-mini-260615')?.mode).toBe('轻量');
  });
  it('contains the reviewed provider video model IDs and excludes HappyHorse', () => {
    expect(models).toHaveLength(16);
    expect(models.map(m => m.id).toSorted()).toEqual(
      [
        'kling-3.0-turbo',
        'kling-3.0-omni',
        'kling-3.0',
        'kling-2.5-turbo',
        'kling-2.6',
        'kling-o1',
        'wan2.7-t2v',
        'wan2.7-i2v',
        'wan2.7-r2v',
        'wan3.0-video',
        'wan3.0-video-prime',
        'doubao-seedance-2-0-260128',
        'doubao-seedance-2-0-mini-260615',
        'doubao-seedance-2-0-fast-260128',
        'doubao-seedance-2-5-260628',
        'MiniMax-H3'
      ].toSorted()
    );
    expect(models.some(m => /happyhorse/i.test(m.id))).toBe(false);
  });
  it('keeps prompt and valid choices when switching modes, resets incompatible choices', () => {
    const fast = models.find(m => m.id === 'doubao-seedance-2-0-fast-260128')!;
    const d: VideoCloudDraft = {
      model: 'old',
      capability: 'T2V',
      prompt: 'camera',
      seconds: 30,
      resolution: '4k',
      ratio: '16:9',
      references: [],
      generateAudio: true
    };
    const result = normalizeVideoCloudDraft(d, fast);
    expect(result.prompt).toBe('camera');
    expect(result.seconds).toBe(4);
    expect(result.resolution).toBe('480p');
    expect(result.ratio).toBe('16:9');
  });
  it.each(models.map(m => [m.id, m] as const))(
    '%s never retains unsupported capability, duration or resolution',
    (_id, m) => {
      const d: VideoCloudDraft = {
        model: 'other',
        capability: 'BAD',
        prompt: 'scene',
        seconds: 999,
        resolution: 'bad',
        ratio: 'bad',
        references: [],
        generateAudio: true,
        negativePrompt: 'bad',
        seed: 12
      };
      const n = normalizeVideoCloudDraft(d, m);
      expect(m.capabilities).toContain(n.capability);
      expect(m.durations).toContain(n.seconds);
      expect(m.resolutions).toContain(n.resolution);
      expect(m.ratios).toContain(n.ratio);
      if (!m.hasAudioOutput) expect(n.generateAudio).toBeUndefined();
      if (!m.hasSeed) expect(n.seed).toBeUndefined();
      if (m.family !== 'Wan') expect(n.negativePrompt).toBeUndefined();
    }
  );
  it('H3 text only cannot use adaptive ratio', () => {
    const h3 = models.find(m => m.family === 'MiniMax')!;
    const n = normalizeVideoCloudDraft(
      {
        model: h3.id,
        capability: 'T2V',
        prompt: 'scene',
        seconds: 5,
        resolution: '768p',
        ratio: 'adaptive',
        references: []
      },
      h3
    );
    expect(n.ratio).toBe('16:9');
  });
});
