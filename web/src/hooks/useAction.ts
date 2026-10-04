import { useCallback, useState } from 'react';
import { ApiError } from '../api/client';

export interface Action<A extends unknown[]> {
  run: (...args: A) => Promise<boolean>;
  pending: boolean;
  error: ApiError | undefined;
}

/** Wraps a mutating API call with pending and error state; resolves to whether it succeeded. */
export function useAction<A extends unknown[]>(action: (...args: A) => Promise<unknown>): Action<A> {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError>();

  const run = useCallback(
    async (...args: A) => {
      setPending(true);
      setError(undefined);
      try {
        await action(...args);
        return true;
      } catch (caught) {
        setError(caught instanceof ApiError ? caught : new ApiError(0, 'Error', String(caught)));
        return false;
      } finally {
        setPending(false);
      }
    },
    [action],
  );

  return { run, pending, error };
}
