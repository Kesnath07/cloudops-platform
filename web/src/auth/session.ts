import type { User } from '../api/types';

export interface Session {
  token: string;
  expiresAt: number;
  user: User;
}

const STORAGE_KEY = 'cloudops.session';

/**
 * Sessions live in sessionStorage: they survive a page reload but not closing the tab, and the
 * access token's own short expiry bounds how long a leaked token stays useful.
 */
export function loadSession(now: number = Date.now()): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const session = JSON.parse(raw) as Session;
    if (typeof session.token !== 'string' || session.expiresAt <= now) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function saveSession(session: Session): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}
