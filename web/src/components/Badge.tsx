import type { IncidentStatus, Severity, WorkloadHealth } from '../api/types';
import { humanize } from '../lib/format';

type Tone = 'ok' | 'warn' | 'bad' | 'critical' | 'neutral';

function Badge({ tone, label }: { tone: Tone; label: string }) {
  return <span className={`badge badge-${tone}`}>{label}</span>;
}

const HEALTH_TONE: Record<WorkloadHealth, Tone> = {
  OPERATIONAL: 'ok',
  DEGRADED: 'warn',
  PARTIAL_OUTAGE: 'bad',
  MAJOR_OUTAGE: 'critical',
};

const SEVERITY_TONE: Record<Severity, Tone> = { SEV1: 'critical', SEV2: 'bad', SEV3: 'warn', SEV4: 'neutral' };

const STATUS_TONE: Record<IncidentStatus, Tone> = { OPEN: 'bad', MITIGATED: 'warn', RESOLVED: 'ok' };

export function HealthBadge({ health }: { health: WorkloadHealth }) {
  return <Badge tone={HEALTH_TONE[health]} label={humanize(health)} />;
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  return <Badge tone={SEVERITY_TONE[severity]} label={severity} />;
}

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return <Badge tone={STATUS_TONE[status]} label={humanize(status)} />;
}
