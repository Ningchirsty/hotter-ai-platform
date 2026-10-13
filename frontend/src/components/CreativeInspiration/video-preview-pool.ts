import { templateVideoPreview } from '@/api/image/templates';

export interface PreviewLease { url: string; release: () => void }
/** Share card/detail media and keep authenticated downloads within two concurrent requests. */
export function createVideoPreviewPool(fetch: (id: string, revision: number) => Promise<string>, revoke: (url: string) => void) {
  let active = 0;
  const queue: (() => void)[] = [];
  const entries = new Map<string, { promise: Promise<string>; refs: number }>();
  function schedule(load: () => Promise<string>): Promise<string> {
    return new Promise((resolve, reject) => {
      const start = () => {
        active++;
        Promise.resolve().then(load).then(resolve, reject).finally(() => { active--; queue.shift()?.(); });
      };
      if (active < 2) start(); else queue.push(start);
    });
  }
  async function acquire(id: string, revision: number): Promise<PreviewLease> {
    const key = JSON.stringify([id, revision]);
    let entry = entries.get(key);
    if (!entry) {
      entry = { refs: 0, promise: schedule(() => fetch(id, revision)) };
      entries.set(key, entry);
    }
    entry.refs++;
    const owned = entry;
    try {
      const url = await owned.promise;
      let released = false;
      return { url, release() {
        if (released) return;
        released = true;
        if (--owned.refs === 0) { if (entries.get(key) === owned) entries.delete(key); revoke(url); }
      } };
    } catch (error) { if (entries.get(key) === owned) entries.delete(key); throw error; }
  }
  return { acquire };
}
export const videoPreviewPool = createVideoPreviewPool(templateVideoPreview, url => URL.revokeObjectURL(url));
