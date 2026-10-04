import { loadSession } from '../auth/session';

export interface FieldError {
  field: string;
  message: string;
}

/** An RFC 9457 problem response, or a network failure surfaced in the same shape. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, title: string, detail: string, fieldErrors: FieldError[] = []) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.fieldErrors = fieldErrors;
  }
}

interface ClientHooks {
  onUnauthorized: () => void;
}

let hooks: ClientHooks = { onUnauthorized: () => undefined };

/** Lets the session owner react when the API rejects the current token. */
export function configureClient(next: ClientHooks): void {
  hooks = next;
}

const API_BASE = '/api/v1';

export async function apiRequest<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = loadSession()?.token;
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Network error', 'The API could not be reached. Check your connection and retry.');
  }

  if (response.status === 401 && token) {
    hooks.onUnauthorized();
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as { title?: string; detail?: string; errors?: FieldError[] };
    return new ApiError(
      response.status,
      problem.title ?? response.statusText,
      problem.detail ?? `Request failed with status ${response.status}`,
      problem.errors ?? [],
    );
  } catch {
    return new ApiError(response.status, response.statusText, `Request failed with status ${response.status}`);
  }
}

export function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  }
  const encoded = search.toString();
  return encoded ? `?${encoded}` : '';
}
