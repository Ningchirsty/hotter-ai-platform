import { afterEach, describe, expect, it, vi } from 'vitest';
import { createTaskPoller } from './task-polling';

afterEach(() => vi.useRealTimers());
describe('task recovery polling', () => {
  it('retains an active task after network failure and reaches its terminal state', async () => {
    vi.useFakeTimers();
    const fetch = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ status: 'SUCCEEDED' });
    const terminal = vi.fn();
    const poller = createTaskPoller({ fetch, onUpdate: vi.fn(), onTerminal: terminal });
    poller.start('old-page-task');
    await vi.advanceTimersByTimeAsync(3000);
    expect(terminal).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(3000);
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(terminal).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(15000);
    expect(fetch).toHaveBeenCalledTimes(2);
    poller.dispose();
  });
  it('does not overlap slow fetches or lose tasks added during a request', async () => {
    vi.useFakeTimers();
    let release!: (task: {status: string}) => void;
    const fetch = vi.fn().mockImplementationOnce(() => new Promise(resolve => { release = resolve; }))
      .mockResolvedValue({ status: 'SUCCEEDED' });
    const poller = createTaskPoller({ fetch, onUpdate: vi.fn(), onTerminal: vi.fn() });
    poller.start('1');
    const running = poller.poll();
    poller.start('2');
    await vi.advanceTimersByTimeAsync(12000);
    await poller.poll();
    expect(fetch).toHaveBeenCalledTimes(1);
    release({ status: 'SUCCEEDED' });
    await running;
    await vi.advanceTimersByTimeAsync(3000);
    expect(fetch).toHaveBeenNthCalledWith(2, '2');
    poller.dispose();
  });
  it('continues after list refresh failure', async () => {
    vi.useFakeTimers();
    const fetch = vi.fn().mockResolvedValue({ status: 'RUNNING' });
    const poller = createTaskPoller({ fetch, onUpdate: vi.fn(), onTerminal: vi.fn(),
      onCycle: vi.fn().mockRejectedValue(new Error('list offline')) });
    poller.start('1');
    await vi.advanceTimersByTimeAsync(9000);
    expect(fetch).toHaveBeenCalledTimes(3);
    poller.dispose();
  });
  it('does not update or reschedule after unmount during a request', async () => {
    vi.useFakeTimers();
    let release!: (task: {status: string}) => void;
    const update = vi.fn();
    const fetch = vi.fn(() => new Promise<{status: string}>(resolve => { release = resolve; }));
    const poller = createTaskPoller({ fetch, onUpdate: update, onTerminal: vi.fn() });
    poller.start('1');
    const running = poller.poll();
    poller.dispose();
    release({ status: 'SUCCEEDED' });
    await running;
    await vi.advanceTimersByTimeAsync(9000);
    expect(update).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledOnce();
  });
  it.each(['FAILED', 'TIMEOUT', 'CANCELED'])('stops only on actual terminal state %s', async status => {
    vi.useFakeTimers();
    const fetch = vi.fn().mockResolvedValue({ status });
    const terminal = vi.fn();
    const poller = createTaskPoller({ fetch, onUpdate: vi.fn(), onTerminal: terminal });
    poller.start('1');
    await vi.advanceTimersByTimeAsync(12000);
    expect(fetch).toHaveBeenCalledOnce();
    expect(terminal).toHaveBeenCalledOnce();
    poller.dispose();
  });
});
