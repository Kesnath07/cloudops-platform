import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Deployment, Page, Workload } from '../api/types';
import { mockApi, renderRoute, signInAs } from '../test/harness';
import { WorkloadPage } from './WorkloadPage';

const WORKLOAD: Workload = {
  id: 'w1',
  slug: 'payments-api',
  name: 'Payments API',
  description: 'Card authorisation',
  criticality: 'HIGH',
  team: { id: 't1', slug: 'payments', name: 'Payments' },
  repositoryUrl: 'https://git.example.org/payments-api',
  runbookUrl: null,
  updatedAt: '2026-03-01T09:00:00Z',
};

function page<T>(items: T[]): Page<T> {
  return { items, page: 0, size: 10, totalItems: items.length, totalPages: items.length > 0 ? 1 : 0 };
}

const RELEASE: Deployment = {
  id: 'd1',
  workloadId: 'w1',
  environment: 'PRODUCTION',
  version: '1.8.0',
  commitSha: '0123456789abcdef',
  outcome: 'SUCCEEDED',
  notes: null,
  deployedByName: 'Olu Operator',
  deployedAt: '2026-03-01T10:00:00Z',
};

const HISTORY = 'GET /api/v1/workloads/w1/deployments?page=0&size=10';
const INCIDENTS = 'GET /api/v1/incidents?workloadId=w1&size=20';

function renderWorkload() {
  return renderRoute(<WorkloadPage />, { path: '/workloads/:workloadId', url: '/workloads/w1' });
}

describe('WorkloadPage', () => {
  it('shows the workload with its incidents and deployment history', async () => {
    signInAs('VIEWER');
    mockApi({
      'GET /api/v1/workloads/w1': { body: WORKLOAD },
      [HISTORY]: { body: page([RELEASE]) },
      [INCIDENTS]: { body: page([]) },
    });
    renderWorkload();

    expect(await screen.findByRole('heading', { name: 'Payments API' })).toBeInTheDocument();
    expect(screen.getByText(/owned by Payments · High criticality/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Repository' })).toHaveAttribute('href', WORKLOAD.repositoryUrl);
    expect(screen.queryByRole('link', { name: 'Runbook' })).not.toBeInTheDocument();
    expect(await screen.findByText('No incidents recorded.')).toBeInTheDocument();
    expect(await screen.findByText('0123456789ab')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Open incident' })).not.toBeInTheDocument();
    expect(screen.queryByText('Record a deployment')).not.toBeInTheDocument();
  });

  it('offers a retry when the deployment history fails to load', async () => {
    signInAs('VIEWER');
    let attempts = 0;
    mockApi({
      'GET /api/v1/workloads/w1': { body: WORKLOAD },
      [HISTORY]: () => {
        attempts += 1;
        return attempts === 1
          ? { status: 503, body: { title: 'Service unavailable', detail: 'Try again shortly.' } }
          : { body: page([RELEASE]) };
      },
      [INCIDENTS]: { body: page([]) },
    });
    renderWorkload();
    const user = userEvent.setup();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Service unavailable');
    await user.click(within(alert).getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('1.8.0')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(attempts).toBe(2);
  });

  it('lets operators record a deployment and refreshes the history', async () => {
    signInAs('OPERATOR');
    let recorded = false;
    const calls = mockApi({
      'GET /api/v1/workloads/w1': { body: WORKLOAD },
      [HISTORY]: () => ({ body: page(recorded ? [RELEASE] : []) }),
      [INCIDENTS]: { body: page([]) },
      'POST /api/v1/workloads/w1/deployments': () => {
        recorded = true;
        return { status: 201, body: RELEASE };
      },
    });
    renderWorkload();
    const user = userEvent.setup();

    expect(await screen.findByText('No deployments recorded.')).toBeInTheDocument();
    await user.click(screen.getByText('Record a deployment'));
    await user.type(screen.getByLabelText('Version'), ' 1.8.0 ');
    await user.selectOptions(screen.getByLabelText('Environment'), 'STAGING');
    await user.click(screen.getByRole('button', { name: 'Record deployment' }));

    expect(await screen.findByText('1.8.0')).toBeInTheDocument();
    const post = calls.find((call) => call.method === 'POST');
    expect(post?.body).toEqual({ environment: 'STAGING', version: '1.8.0', outcome: 'SUCCEEDED' });
  });

  it('shows a retryable error when the workload cannot be loaded', async () => {
    signInAs('VIEWER');
    mockApi({
      'GET /api/v1/workloads/w1': { status: 404, body: { title: 'Resource not found', detail: "Workload 'w1' was not found" } },
      [HISTORY]: { body: page([]) },
      [INCIDENTS]: { body: page([]) },
    });
    renderWorkload();

    expect(await screen.findByRole('alert')).toHaveTextContent("Workload 'w1' was not found");
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
