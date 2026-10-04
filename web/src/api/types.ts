// Contracts mirroring the API's view records. Timestamps are ISO-8601 strings.

export type Role = 'VIEWER' | 'OPERATOR' | 'ADMIN';
export type Criticality = 'HIGH' | 'MEDIUM' | 'LOW';
export type Severity = 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';
export type IncidentStatus = 'OPEN' | 'MITIGATED' | 'RESOLVED';
export type WorkloadHealth = 'OPERATIONAL' | 'DEGRADED' | 'PARTIAL_OUTAGE' | 'MAJOR_OUTAGE';
export type DeploymentEnvironment = 'DEVELOPMENT' | 'STAGING' | 'PRODUCTION';
export type DeploymentOutcome = 'SUCCEEDED' | 'FAILED' | 'ROLLED_BACK';

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface User {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  createdAt: string;
}

export interface AccessToken {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface Team {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  contactEmail: string | null;
}

export interface Workload {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  criticality: Criticality;
  team: { id: string; slug: string; name: string };
  repositoryUrl: string | null;
  runbookUrl: string | null;
  updatedAt: string;
}

export interface WorkloadRef {
  id: string;
  slug: string;
  name: string;
}

export interface Deployment {
  id: string;
  workloadId: string;
  environment: DeploymentEnvironment;
  version: string;
  commitSha: string | null;
  outcome: DeploymentOutcome;
  notes: string | null;
  deployedByName: string;
  deployedAt: string;
}

export interface WorkloadOverview {
  workload: Workload;
  health: WorkloadHealth;
  activeIncidents: number;
  lastProductionDeployment: Deployment | null;
}

export interface Incident {
  id: string;
  workload: WorkloadRef;
  title: string;
  severity: Severity;
  status: IncidentStatus;
  openedByName: string;
  openedAt: string;
  mitigatedAt: string | null;
  resolvedAt: string | null;
}

export interface TimelineEntry {
  id: string;
  message: string;
  status: IncidentStatus;
  severity: Severity;
  authorName: string;
  postedAt: string;
}

export interface IncidentDetail extends Incident {
  summary: string | null;
  timeline: TimelineEntry[];
}

export interface PlatformInfo {
  service: string;
  version: string;
  release: string;
}
