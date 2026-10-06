import type { Role } from '../api/types';

const RANK: Record<Role, number> = { VIEWER: 0, OPERATOR: 1, ADMIN: 2 };

/** Every role, from least to most privileged. */
export const ROLES = (Object.keys(RANK) as Role[]).sort((a, b) => RANK[a] - RANK[b]);

/** Mirrors the API's role hierarchy: ADMIN implies OPERATOR, which implies VIEWER. */
export function hasRole(actual: Role | undefined, required: Role): boolean {
  return actual !== undefined && RANK[actual] >= RANK[required];
}
