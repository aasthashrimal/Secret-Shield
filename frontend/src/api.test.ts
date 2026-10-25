import { afterEach, expect, it, vi } from 'vitest';
import { createApi, ApiError, apiUrl } from './api';

afterEach(() => vi.unstubAllGlobals());

it('uses relative API paths locally and an explicit HTTPS base for static hosting', () => {
  expect(apiUrl('/api/health/live', '')).toBe('/api/health/live');
  expect(apiUrl('/api/health/live', 'https://api.example.invalid/'))
    .toBe('https://api.example.invalid/api/health/live');
});

it('sends the in-memory token and expires the session on 401', async () => {
  const expired = vi.fn();
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), { status: 401, headers: { 'Content-Type': 'application/json' } }));
  vi.stubGlobal('fetch', fetchMock);
  const request = createApi(() => 'synthetic-test-token', expired);
  await expect(request('/api/repositories')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
  expect(expired).toHaveBeenCalledOnce();
  expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer synthetic-test-token');
});

it('reports forbidden and conflict responses without exposing server error text', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: 'FORBIDDEN', message: 'unsafe-detail' }), { status: 403 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'CONFLICT', message: 'unsafe-detail' }), { status: 409 })));
  const request = createApi(() => 'synthetic-token', vi.fn());
  await expect(request('/api/repositories/1')).rejects.toMatchObject({ status: 403, message: 'Access denied.' });
  await expect(request('/api/repositories/1')).rejects.toMatchObject({ status: 409, message: expect.stringContaining('Refresh') });
  expect(new ApiError(403, 'FORBIDDEN').message).not.toContain('unsafe-detail');
});
