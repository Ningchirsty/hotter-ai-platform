import { describe, it, expect, vi, beforeEach } from 'vitest';
const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@/utils/request', () => ({ default: mocks.request }));
import { listVideoCloudModels, createVideoCloudTask, uploadVideoCloudAsset } from './cloud';
import { videoCloudAccess, type VideoCloudDraft, type VideoCloudStatus } from './cloud-models';
const draft: VideoCloudDraft = {
  model: 'wan2.7-t2v',
  capability: 'T2V',
  prompt: 'scene',
  seconds: 5,
  resolution: '720p',
  ratio: '16:9',
  generateAudio: true,
  references: [],
  idempotencyKey: 'once'
};
const status: VideoCloudStatus = {
  configured: true,
  referenceDeliveryConfigured: true,
  profiles: [],
  verifiedVariants: ['wan2.7-t2v|T2V|5|720p|16:9|true'],
  validationVariants: [],
  validationRemaining: 0
};
describe('Cloud video real backend wiring and exact acceptance gate', () => {
  beforeEach(() => mocks.request.mockReset());
  it('fetches authenticated backend status without a provider key', () => {
    listVideoCloudModels();
    expect(mocks.request).toHaveBeenCalledWith({ url: '/video/cloud/models', method: 'get' });
  });
  it('preserves selected parameters and idempotency key in real task submission', () => {
    createVideoCloudTask(draft);
    expect(mocks.request).toHaveBeenCalledWith({ url: '/video/cloud/tasks', method: 'post', data: draft });
  });
  it('uploads a file as multipart without overriding boundary', () => {
    const file = new File(['ref'], 'ref.png', { type: 'image/png' });
    uploadVideoCloudAsset(file);
    const call = mocks.request.mock.calls[0][0];
    expect(call.url).toBe('/video/cloud/assets');
    expect(call.data.get('file')).toBe(file);
    expect(call.headers).toBeUndefined();
  });
  it('opens only the actually accepted capability and output combination', () => {
    expect(videoCloudAccess(draft, status)).toBe('verified');
    for (const change of [
      { capability: 'I2V' },
      { seconds: 10 },
      { resolution: '1080p' },
      { ratio: '9:16' },
      { generateAudio: false },
      { seed: 1 },
      { negativePrompt: 'blur' }
    ])
      expect(videoCloudAccess({ ...draft, ...change }, status)).toBe('pending');
  });
  it('never opens validation for ordinary users or after allowance is consumed', () => {
    const validating = {
      ...status,
      verifiedVariants: [],
      validationVariants: status.verifiedVariants,
      validationRemaining: 1
    };
    expect(videoCloudAccess(draft, validating)).toBe('validation');
    expect(videoCloudAccess(draft, { ...validating, validationRemaining: 0 })).toBe('pending');
    expect(videoCloudAccess(draft, null)).toBe('pending');
  });
});
