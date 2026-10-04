import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { configureClient } from '../api/client';
import { auth } from '../api/endpoints';
import { AuthContext, type AuthState } from './AuthContext';
import { clearSession, loadSession, saveSession, type Session } from './session';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession());

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  useEffect(() => {
    configureClient({ onUnauthorized: logout });
  }, [logout]);

  useEffect(() => {
    if (!session) {
      return undefined;
    }
    const timer = window.setTimeout(logout, Math.max(0, session.expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [session, logout]);

  const login = useCallback(async (email: string, password: string) => {
    const token = await auth.token({ email, password });
    const next: Session = {
      token: token.accessToken,
      expiresAt: Date.now() + token.expiresIn * 1000,
      user: token.user,
    };
    saveSession(next);
    setSession(next);
  }, []);

  const value = useMemo<AuthState>(() => ({ user: session?.user ?? null, login, logout }), [session, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
