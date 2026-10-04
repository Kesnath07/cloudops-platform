import { useCallback, useEffect, useEffectEvent, useState } from 'react';
import { ApiError } from '../api/client';

interface Result<T> {
  key: string;
  data?: T;
  error?: ApiError;
}

export interface ApiResource<T> {
  data: T | undefined;
  error: ApiError | undefined;
  loading: boolean;
  reload: () => void;
}

/**
 * Loads data whenever {@code key} changes. Results are tagged with the key that produced them,
 * so a slow response for an old key can never overwrite the data for the current one.
 */
export function useApi<T>(key: string, loader: () => Promise<T>): ApiResource<T> {
  const [result, setResult] = useState<Result<T> | null>(null);
  const [generation, setGeneration] = useState(0);
  const requestKey = `${key}#${generation}`;
  const load = useEffectEvent(loader);

  useEffect(() => {
    let active = true;
    load().then(
      (data) => active && setResult({ key: requestKey, data }),
      (error: unknown) =>
        active &&
        setResult({
          key: requestKey,
          error: error instanceof ApiError ? error : new ApiError(0, 'Error', String(error)),
        }),
    );
    return () => {
      active = false;
    };
  }, [requestKey]);

  const reload = useCallback(() => setGeneration((value) => value + 1), []);
  const current = result?.key === requestKey;

  return {
    data: result?.data,
    error: current ? result?.error : undefined,
    loading: !current,
    reload,
  };
}
