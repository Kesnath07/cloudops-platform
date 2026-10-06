import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { useAction } from './useAction';
import { useApi } from './useApi';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe('useApi', () => {
  it('ignores a slow response for a key that is no longer current', async () => {
    const slow = deferred<string>();
    const fast = deferred<string>();
    const { result, rerender } = renderHook(({ id }) => useApi(`item-${id}`, () => (id === 1 ? slow.promise : fast.promise)), {
      initialProps: { id: 1 },
    });

    rerender({ id: 2 });
    await act(async () => fast.resolve('second'));
    await act(async () => slow.resolve('first'));

    expect(result.current.data).toBe('second');
    expect(result.current.loading).toBe(false);
  });

  it('wraps unexpected failures as ApiError and clears them on reload', async () => {
    const loader = vi.fn<() => Promise<string>>().mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce('ok');
    const { result } = renderHook(() => useApi('item', loader));

    await waitFor(() => expect(result.current.error).toBeInstanceOf(ApiError));
    expect(result.current.error?.message).toContain('boom');

    act(() => result.current.reload());
    expect(result.current.error).toBeUndefined();
    await waitFor(() => expect(result.current.data).toBe('ok'));
    expect(loader).toHaveBeenCalledTimes(2);
  });
});

describe('useAction', () => {
  it('tracks pending state and reports success', async () => {
    const call = deferred<undefined>();
    const { result } = renderHook(() => useAction(() => call.promise));

    let outcome: Promise<boolean> | undefined;
    act(() => {
      outcome = result.current.run();
    });
    expect(result.current.pending).toBe(true);

    await act(async () => call.resolve(undefined));
    await expect(outcome).resolves.toBe(true);
    expect(result.current.pending).toBe(false);
    expect(result.current.error).toBeUndefined();
  });

  it('keeps the API error and resets it on the next attempt', async () => {
    const failure = new ApiError(409, 'Conflict', 'Slug taken');
    const action = vi.fn<() => Promise<void>>().mockRejectedValueOnce(failure).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useAction(action));

    await act(async () => {
      expect(await result.current.run()).toBe(false);
    });
    expect(result.current.error).toBe(failure);

    await act(async () => {
      expect(await result.current.run()).toBe(true);
    });
    expect(result.current.error).toBeUndefined();
  });
});
