import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { IncidentDetail, WorkloadOverview } from '../api/types';
import { AuthProvider } from '../auth/AuthProvider';
import { mockApi, signInAs } from '../test/harness';
import { NewIncidentPage } from './NewIncidentPage';

function row(id: string, name: string): WorkloadOverview {
  return {
    workload: {
      id,
      slug: name.toLowerCase().replaceAll(' ', '-'),
      name,
      description: null,
      criticality: 'HIGH',
      team: { id: 't1', slug: 'platform', name: 'Platform' },
      repositoryUrl: null,
      runbookUrl: null,
      updatedAt: '2026-03-01T09:00:00Z',
    },
    health: 'OPERATIONAL',
    activeIncidents: 0,
    lastProductionDeployment: null,
  };
}

const OVERVIEW = [row('w1', 'Payments API'), row('w2', 'Search API')];

const OPENED: Pick<IncidentDetail, 'id'> = { id: 'i42' };

function renderNewIncident(url = '/incidents/new') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <AuthProvider>
        <Routes>
          <Route path="/incidents/new" element={<NewIncidentPage />} />
          <Route path="/incidents/:incidentId" element={<p>Incident detail page</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('NewIncidentPage', () => {
  it('opens an incident for the preselected workload and shows it', async () => {
    signInAs('OPERATOR');
    const calls = mockApi({
      'GET /api/v1/overview': { body: OVERVIEW },
      'POST /api/v1/incidents': { status: 201, body: OPENED },
    });
    renderNewIncident('/incidents/new?workloadId=w2');
    const user = userEvent.setup();

    expect(await screen.findByLabelText('Affected workload')).toHaveValue('w2');
    await user.type(screen.getByLabelText('Title'), '  Search latency above SLO  ');
    await user.selectOptions(screen.getByLabelText('Severity'), 'SEV2');
    await user.click(screen.getByRole('button', { name: 'Open incident' }));

    expect(await screen.findByText('Incident detail page')).toBeInTheDocument();
    expect(calls.find((call) => call.method === 'POST')?.body).toEqual({
      workloadId: 'w2',
      title: 'Search latency above SLO',
      severity: 'SEV2',
    });
  });

  it('keeps the form and shows field errors when the API rejects it', async () => {
    signInAs('OPERATOR');
    mockApi({
      'GET /api/v1/overview': { body: OVERVIEW },
      'POST /api/v1/incidents': {
        status: 400,
        body: {
          title: 'Validation failed',
          detail: 'One or more fields are invalid.',
          errors: [{ field: 'title', message: 'size must be between 0 and 200' }],
        },
      },
    });
    renderNewIncident();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText('Affected workload'), 'w1');
    await user.type(screen.getByLabelText('Title'), 'Checkout failing');
    await user.click(screen.getByRole('button', { name: 'Open incident' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Validation failed');
    expect(alert).toHaveTextContent('title: size must be between 0 and 200');
    expect(screen.getByLabelText('Title')).toHaveValue('Checkout failing');
    expect(screen.getByRole('button', { name: 'Open incident' })).toBeEnabled();
    expect(screen.queryByText('Incident detail page')).not.toBeInTheDocument();
  });

  it('does not submit until a workload is chosen', async () => {
    signInAs('OPERATOR');
    const calls = mockApi({ 'GET /api/v1/overview': { body: OVERVIEW } });
    renderNewIncident();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Title'), 'Checkout failing');
    await user.click(screen.getByRole('button', { name: 'Open incident' }));

    expect(screen.getByLabelText('Affected workload')).toBeInvalid();
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(0);
  });

  it('reports when the workload list cannot be loaded', async () => {
    signInAs('OPERATOR');
    mockApi({ 'GET /api/v1/overview': { status: 503, body: { title: 'Service Unavailable', detail: 'Try again shortly.' } } });
    renderNewIncident();

    expect(await screen.findByRole('alert')).toHaveTextContent('Service Unavailable Try again shortly.');
  });
});
