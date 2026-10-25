export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string) {
    super(status === 403 ? 'Access denied.' : status === 409 ? 'This record changed or already exists. Refresh and try again.' : status === 401 ? 'Your session expired. Sign in again.' : status === 400 ? 'Check the entered values.' : 'The request could not be completed.');
  }
}

export function apiUrl(path: string, configuredBase = import.meta.env.VITE_API_BASE ?? ''): string {
  const base = configuredBase.trim().replace(/\/+$/, '');
  return `${base}${path}`;
}

export function createApi(getToken: () => string | null, onUnauthorized: () => void) {
  return async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers = new Headers(options.headers);
    const token = getToken();
    if (token) headers.set('Authorization', `Bearer ${token}`);
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    let response: Response;
    try {
      response = await fetch(apiUrl(path), { ...options, headers });
    } catch {
      throw new ApiError(0, 'NETWORK_ERROR');
    }
    if (response.status === 401 && token) onUnauthorized();
    if (!response.ok) {
      let code = 'REQUEST_ERROR';
      try { code = (await response.json()).code ?? code; } catch { /* No public body. */ }
      throw new ApiError(response.status, code);
    }
    return response.status === 204 ? undefined as T : await response.json() as T;
  };
}

export function query(values: Record<string, string | number | undefined | null>): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  });
  return params.toString();
}
