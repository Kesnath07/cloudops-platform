import { describe, expect, it, vi } from 'vitest';
import { saveSession } from '../auth/session';
import { mockApi, testUser } from '../test/harness';
import { ApiError, apiRequest, configureClient, query } from './client';

describe('apiRequest', () => {
  it('sends the session token and JSON body', async () => {
    saveSession({ token: 'abc123', expiresAt: Date.now() + 60_000, user: testUser() });
    const calls = mockApi({ 'POST /api/v1/teams': { status: 201, body: { id: 't1' } } });

    const result = await apiRequest<{ id: string }>('POST', '/teams', { name: 'Payments' });

    expect(result.id).toBe('t1');
    expect(calls[0]?.headers.Authorization).toBe('Bearer abc123');
    expect(calls[0]?.headers['Content-Type']).toBe('application/json');
    expect(calls[0]?.body).toEqual({ name: 'Payments' });
  });

  it('omits the Authorization header without a session', async () => {
    const calls = mockApi({ 'GET /api/v1/platform/info': { body: {} } });

    await apiRequest('GET', '/platform/info');

    expect(calls[0]?.headers.Authorization).toBeUndefined();
  });

  it('turns problem responses into ApiError with field errors', async () => {
    mockApi({
      'POST /api/v1/incidents': {
        status: 400,
        body: { title: 'Validation failed', detail: 'One or more fields are invalid.', errors: [{ field: 'title', message: 'must not be blank' }] },
      },
    });

    const error = await apiRequest('POST', '/incidents', {}).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, title: 'Validation failed', fieldErrors: [{ field: 'title', message: 'must not be blank' }] });
  });

  it('reports an expired token so the session can end', async () => {
    saveSession({ token: 'stale', expiresAt: Date.now() + 60_000, user: testUser() });
    const onUnauthorized = vi.fn();
    configureClient({ onUnauthorized });
    mockApi({ 'GET /api/v1/users/me': { status: 401 } });

    await expect(apiRequest('GET', '/users/me')).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('maps network failures to a readable error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(apiRequest('GET', '/overview')).rejects.toMatchObject({ status: 0, title: 'Network error' });
  });

  it('bounds every request with a timeout and reports when it expires', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new DOMException('signal timed out', 'TimeoutError'));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiRequest('GET', '/overview')).rejects.toMatchObject({ status: 0, title: 'Request timed out' });
    expect((fetchMock.mock.calls[0]?.[1] as RequestInit).signal).toBeInstanceOf(AbortSignal);
  });

  it('rejects a successful response that is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<!doctype html><html></html>', { status: 200, headers: { 'Content-Type': 'text/html' } })),
    );

    await expect(apiRequest('GET', '/overview')).rejects.toMatchObject({ status: 200, title: 'Unexpected response' });
  });

  it('returns undefined for 204 responses', async () => {
    mockApi({ 'PUT /api/v1/users/me/password': { status: 204 } });

    await expect(apiRequest('PUT', '/users/me/password', {})).resolves.toBeUndefined();
  });
});

describe('query', () => {
  it('drops empty values', () => {
    expect(query({ status: 'OPEN', workloadId: undefined, page: 0, q: '' })).toBe('?status=OPEN&page=0');
    expect(query({})).toBe('');
  });
});
