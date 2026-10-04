import { apiRequest, query } from './client';
import type {
  AccessToken,
  Criticality,
  Deployment,
  DeploymentEnvironment,
  DeploymentOutcome,
  IncidentDetail,
  Incident,
  IncidentStatus,
  Page,
  PlatformInfo,
  Role,
  Severity,
  Team,
  User,
  Workload,
  WorkloadOverview,
} from './types';

export const auth = {
  register: (body: { email: string; displayName: string; password: string }) =>
    apiRequest<User>('POST', '/auth/register', body),
  token: (body: { email: string; password: string }) => apiRequest<AccessToken>('POST', '/auth/token', body),
};

export const users = {
  me: () => apiRequest<User>('GET', '/users/me'),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    apiRequest<undefined>('PUT', '/users/me/password', body),
  list: (page = 0) => apiRequest<Page<User>>('GET', `/users${query({ page, size: 50 })}`),
  changeRole: (userId: string, role: Role) => apiRequest<User>('PUT', `/users/${userId}/role`, { role }),
};

export const teams = {
  list: () => apiRequest<Team[]>('GET', '/teams'),
  create: (body: { slug: string; name: string; description?: string; contactEmail?: string }) =>
    apiRequest<Team>('POST', '/teams', body),
};

export interface WorkloadInput {
  slug: string;
  name: string;
  description?: string;
  teamId: string;
  criticality: Criticality;
  repositoryUrl?: string;
  runbookUrl?: string;
}

export const workloads = {
  get: (id: string) => apiRequest<Workload>('GET', `/workloads/${id}`),
  create: (body: WorkloadInput) => apiRequest<Workload>('POST', '/workloads', body),
  overview: () => apiRequest<WorkloadOverview[]>('GET', '/overview'),
};

export interface DeploymentInput {
  environment: DeploymentEnvironment;
  version: string;
  commitSha?: string;
  outcome: DeploymentOutcome;
  notes?: string;
}

export const deployments = {
  history: (workloadId: string, page = 0) =>
    apiRequest<Page<Deployment>>('GET', `/workloads/${workloadId}/deployments${query({ page, size: 10 })}`),
  record: (workloadId: string, body: DeploymentInput) =>
    apiRequest<Deployment>('POST', `/workloads/${workloadId}/deployments`, body),
};

export const incidents = {
  list: (filter: { status?: IncidentStatus; workloadId?: string; page?: number }) =>
    apiRequest<Page<Incident>>('GET', `/incidents${query({ ...filter, size: 20 })}`),
  get: (id: string) => apiRequest<IncidentDetail>('GET', `/incidents/${id}`),
  open: (body: { workloadId: string; title: string; summary?: string; severity: Severity }) =>
    apiRequest<IncidentDetail>('POST', '/incidents', body),
  postUpdate: (id: string, body: { message: string; status?: IncidentStatus; severity?: Severity }) =>
    apiRequest<IncidentDetail>('POST', `/incidents/${id}/updates`, body),
};

export const platform = {
  info: () => apiRequest<PlatformInfo>('GET', '/platform/info'),
};
