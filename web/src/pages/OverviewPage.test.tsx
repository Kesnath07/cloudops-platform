import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { WorkloadOverview } from '../api/types';
import { RequireAuth } from '../auth/RequireAuth';
import { mockApi, renderRoute, signInAs } from '../test/harness';
import { OverviewPage } from './OverviewPage';

const ROW: WorkloadOverview = {
  workload: {
    id: 'w1',
    slug: 'payments-api',
    name: 'Payments API',
    description: null,
    criticality: 'HIGH',
    team: { id: 't1', slug: 'payments', name: 'Payments' },
    repositoryUrl: null,
    runbookUrl: null,
    updatedAt: '2026-01-01T00:00:00Z',
  },
  health: 'PARTIAL_OUTAGE',
  activeIncidents: 2,
  lastProductionDeployment: null,
};

describe('OverviewPage', () => {
  it('renders workload health for viewers without operator actions', async () => {
    signInAs('VIEWER');
    mockApi({ 'GET /api/v1/overview': { body: [ROW] } });
    renderRoute(<OverviewPage />, { path: '/', url: '/' });

    expect(screen.getByRole('status')).toHaveTextContent('Loading');
    expect(await screen.findByRole('link', { name: 'Payments API' })).toHaveAttribute('href', '/workloads/w1');
    expect(screen.getByText('Partial outage')).toBeInTheDocument();
    expect(screen.getByText('1 workloads · 1 impacted')).toBeInTheDocument();
    expect(screen.getByText('Never deployed')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Register workload' })).not.toBeInTheDocument();
  });

  it('offers workload registration to operators', async () => {
    signInAs('OPERATOR');
    mockApi({ 'GET /api/v1/overview': { body: [] } });
    renderRoute(<OverviewPage />, { path: '/', url: '/' });

    expect(await screen.findByText('No workloads registered yet.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Register workload' })).toBeInTheDocument();
  });

  it('shows errors and retries', async () => {
    signInAs('VIEWER');
    let attempts = 0;
    mockApi({
      'GET /api/v1/overview': () => {
        attempts += 1;
        return attempts === 1
          ? { status: 503, body: { title: 'Service Unavailable', detail: 'Try again later' } }
          : { body: [ROW] };
      },
    });
    renderRoute(<OverviewPage />, { path: '/', url: '/' });

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again later');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Payments API')).toBeInTheDocument();
  });

  it('redirects anonymous visitors to the login page', () => {
    renderRoute(<RequireAuth />, { path: '/', url: '/' });

    expect(screen.getByText('Login screen')).toBeInTheDocument();
  });
});
