import { useEffect, useState } from 'react';
import { useSession } from './session';

export function useResource<T>(path: string | null, refresh = 0) {
  const { request } = useSession();
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    if (!path) { setData(null); setLoading(false); return; }
    setLoading(true); setError(null);
    request<T>(path).then(value => { if (active) setData(value); })
      .catch(err => { if (active) { setData(null); setError(err instanceof Error ? err.message : 'Request failed.'); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [path, refresh, request]);
  return { data, error, loading };
}
