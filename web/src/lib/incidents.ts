import type { IncidentStatus } from '../api/types';

/** Client-side copy of the API's lifecycle rules, used only to offer valid choices. */
const TRANSITIONS: Record<IncidentStatus, IncidentStatus[]> = {
  OPEN: ['MITIGATED', 'RESOLVED'],
  MITIGATED: ['OPEN', 'RESOLVED'],
  RESOLVED: [],
};

export function nextStatuses(status: IncidentStatus): IncidentStatus[] {
  return TRANSITIONS[status];
}
