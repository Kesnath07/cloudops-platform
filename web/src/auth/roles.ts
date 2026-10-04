import type { Role } from '../api/types';

const RANK: Record<Role, number> = { VIEWER: 0, OPERATOR: 1, ADMIN: 2 };

/** Mirrors the API's role hierarchy: ADMIN implies OPERATOR, which implies VIEWER. */
export function hasRole(actual: Role | undefined, required: Role): boolean {
  return actual !== undefined && RANK[actual] >= RANK[required];
}
