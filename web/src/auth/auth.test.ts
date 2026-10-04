import { describe, expect, it } from 'vitest';
import { nextStatuses } from '../lib/incidents';
import { testUser } from '../test/harness';
import { hasRole } from './roles';
import { clearSession, loadSession, saveSession } from './session';

describe('hasRole', () => {
  it('follows the role hierarchy', () => {
    expect(hasRole('ADMIN', 'OPERATOR')).toBe(true);
    expect(hasRole('OPERATOR', 'VIEWER')).toBe(true);
    expect(hasRole('OPERATOR', 'ADMIN')).toBe(false);
    expect(hasRole(undefined, 'VIEWER')).toBe(false);
  });
});

describe('session storage', () => {
  it('round-trips a live session and discards expired ones', () => {
    saveSession({ token: 't', expiresAt: 2_000, user: testUser() });

    expect(loadSession(1_000)?.token).toBe('t');
    expect(loadSession(3_000)).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('ignores corrupted storage', () => {
    sessionStorage.setItem('cloudops.session', '{not json');
    expect(loadSession()).toBeNull();
    clearSession();
  });
});

describe('incident lifecycle', () => {
  it('offers only valid transitions', () => {
    expect(nextStatuses('OPEN')).toEqual(['MITIGATED', 'RESOLVED']);
    expect(nextStatuses('RESOLVED')).toEqual([]);
  });
});
