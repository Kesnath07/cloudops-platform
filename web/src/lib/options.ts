import type { Criticality, DeploymentEnvironment, DeploymentOutcome, Severity } from '../api/types';

/**
 * Console labels for the API's enumerations. Each map is keyed by the full union type, so adding a
 * value in `api/types.ts` fails the type check until it is labelled here, instead of the value
 * silently missing from a form. Key order is the display order.
 */
export const SEVERITY_DESCRIPTIONS: Record<Severity, string> = {
  SEV1: 'complete outage',
  SEV2: 'major impact',
  SEV3: 'partial degradation',
  SEV4: 'minor',
};

export const CRITICALITY_LABELS: Record<Criticality, string> = {
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};

export const ENVIRONMENT_LABELS: Record<DeploymentEnvironment, string> = {
  DEVELOPMENT: 'Development',
  STAGING: 'Staging',
  PRODUCTION: 'Production',
};

export const OUTCOME_LABELS: Record<DeploymentOutcome, string> = {
  SUCCEEDED: 'Succeeded',
  FAILED: 'Failed',
  ROLLED_BACK: 'Rolled back',
};

/** The keys of a label map, typed as its union rather than `string[]`. */
export function optionsOf<K extends string>(labels: Record<K, unknown>): K[] {
  return Object.keys(labels) as K[];
}

export const SEVERITIES = optionsOf(SEVERITY_DESCRIPTIONS);
