/** Completion-driven polling: retain unknown states and never overlap requests. */
export function createTaskPoller<T extends { status: string }>(options: {
  fetch: (id: string) => Promise<T | undefined>;
  onUpdate: (id: string, task: T) => void;
  onTerminal: (id: string, task: T) => void;
  onCycle?: (finished: boolean) => Promise<void>;
  intervalMs?: number;
}) {
  const ids = new Set<string>();
  const terminal = new Set(['SUCCEEDED', 'FAILED', 'TIMEOUT', 'CANCELED']);
  let timer: ReturnType<typeof setTimeout> | undefined;
  let busy = false;
  let disposed = false;
  function schedule() {
    if (disposed || busy || timer !== undefined || !ids.size) return;
    timer = setTimeout(() => { timer = undefined; void poll(); }, options.intervalMs ?? 3000);
  }
  async function poll() {
    if (disposed || busy || !ids.size) return;
    busy = true;
    let finished = false;
    try {
      for (const id of Array.from(ids)) {
        try {
          const task = await options.fetch(id);
          if (disposed) return;
          if (!task) continue;
          options.onUpdate(id, task);
          if (terminal.has(task.status)) {
            ids.delete(id);
            finished = true;
            options.onTerminal(id, task);
          }
        } catch {
          // A failed request does not prove a task has finished.
        }
      }
      if (!disposed) await options.onCycle?.(finished);
    } catch {
      // List refresh failure must not stop active task tracking.
    } finally {
      busy = false;
      schedule();
    }
  }
  return {
    start(id: number | string) { if (!disposed) { ids.add(String(id)); schedule(); } },
    stop(id: number | string) {
      ids.delete(String(id));
      if (!ids.size && timer !== undefined) { clearTimeout(timer); timer = undefined; }
    },
    dispose() { disposed = true; ids.clear(); if (timer !== undefined) clearTimeout(timer); timer = undefined; },
    poll
  };
}
