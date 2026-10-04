import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { vi } from 'vitest';
import type { Role, User } from '../api/types';
import { AuthProvider } from '../auth/AuthProvider';
import { saveSession } from '../auth/session';

export interface MockResponse {
  status?: number;
  body?: unknown;
}

export interface RecordedCall {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: unknown;
}

/**
 * Replaces fetch with a route table keyed by "METHOD /path". Unmatched requests fail the test
 * loudly instead of hanging.
 */
export function mockApi(routes: Record<string, MockResponse | ((call: RecordedCall) => MockResponse)>) {
  const calls: RecordedCall[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init: RequestInit = {}) => {
      const method = init.method ?? 'GET';
      const call: RecordedCall = {
        method,
        path: input,
        headers: (init.headers ?? {}) as Record<string, string>,
        body: init.body ? JSON.parse(String(init.body)) : undefined,
      };
      calls.push(call);
      const route = routes[`${method} ${input}`];
      if (!route) {
        throw new Error(`Unexpected request: ${method} ${input}`);
      }
      const response = typeof route === 'function' ? route(call) : route;
      const status = response.status ?? 200;
      return new Response(status === 204 ? null : JSON.stringify(response.body ?? {}), {
        status,
        headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
      });
    }),
  );
  return calls;
}

export function testUser(role: Role = 'VIEWER'): User {
  return {
    id: `user-${role.toLowerCase()}`,
    email: `${role.toLowerCase()}@cloudops.test`,
    displayName: `Test ${role}`,
    role,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

export function signInAs(role: Role): User {
  const user = testUser(role);
  saveSession({ token: `token-${role}`, expiresAt: Date.now() + 3_600_000, user });
  return user;
}

export function renderRoute(element: ReactElement, { path, url }: { path: string; url: string }) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <AuthProvider>
        <Routes>
          <Route path={path} element={element} />
          <Route path="/login" element={<p>Login screen</p>} />
          <Route path="*" element={<p>Navigated elsewhere</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}
