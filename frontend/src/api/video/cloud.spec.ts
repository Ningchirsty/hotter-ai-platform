import { describe, it, expect } from 'vitest';
import type { VideoCloudDraft } from './cloud-models';
import { listVideoCloudModels, createVideoCloudTask, uploadVideoCloudAsset } from './cloud';
describe('Frontend-only video release gate', () => {
  it('never reports backend configuration or verified generation before a backend release', async () => {
    const status = (await listVideoCloudModels()).data!;
    expect(status.configured).toBe(false);
    expect(status.referenceDeliveryConfigured).toBe(false);
    expect(status.verifiedModels).toEqual([]);
    expect(status.profiles).toHaveLength(16);
  });
  it('rejects direct generation and upload calls even if a UI gate is bypassed', async () => {
    await expect(createVideoCloudTask({} as VideoCloudDraft)).rejects.toThrow('待接入');
    await expect(uploadVideoCloudAsset({} as File)).rejects.toThrow('待服务接入');
  });
});
