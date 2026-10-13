import { describe, it, expect, vi } from 'vitest';
import { createVideoPreviewPool } from './video-preview-pool';
vi.mock('@/api/image/templates', () => ({ templateVideoPreview: vi.fn() }));
describe('template video preview lifetimes', () => {
  it('shares a card and detail request until the last view releases it', async () => {
    const fetch = vi.fn().mockResolvedValue('blob:shared'), revoke = vi.fn();
    const pool = createVideoPreviewPool(fetch, revoke);
    const [card, detail] = await Promise.all([pool.acquire('video', 2), pool.acquire('video', 2)]);
    expect(fetch).toHaveBeenCalledTimes(1);
    card.release(); card.release(); expect(revoke).not.toHaveBeenCalled();
    detail.release(); expect(revoke).toHaveBeenCalledExactlyOnceWith('blob:shared');
    const newView = await pool.acquire('video', 2); expect(fetch).toHaveBeenCalledTimes(2); newView.release();
  });
  it('limits concurrent downloads and starts a waiting card after completion', async () => {
    const pending = new Map<string, (url: string) => void>();
    const fetch = vi.fn((id: string) => new Promise<string>(resolve => pending.set(id, resolve)));
    const pool = createVideoPreviewPool(fetch, vi.fn());
    const jobs = ['a','b','c'].map(id => pool.acquire(id, 1));
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    pending.get('a')!('blob:a');
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
    pending.get('b')!('blob:b'); pending.get('c')!('blob:c');
    (await Promise.all(jobs)).forEach(lease => lease.release());
  });
  it('allows a failed download to retry and never reuses a previous revision', async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error('unavailable')).mockResolvedValue('blob:ok');
    const pool = createVideoPreviewPool(fetch, vi.fn());
    await expect(pool.acquire('video', 1)).rejects.toThrow('unavailable');
    const first = await pool.acquire('video', 1), revised = await pool.acquire('video', 2);
    expect(fetch).toHaveBeenCalledTimes(3); expect(fetch).toHaveBeenLastCalledWith('video', 2);
    first.release(); revised.release();
  });
});
