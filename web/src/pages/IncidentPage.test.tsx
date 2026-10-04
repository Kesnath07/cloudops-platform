import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { IncidentDetail } from '../api/types';
import { mockApi, renderRoute, signInAs } from '../test/harness';
import { IncidentPage } from './IncidentPage';

const INCIDENT: IncidentDetail = {
  id: 'i1',
  workload: { id: 'w1', slug: 'payments-api', name: 'Payments API' },
  title: 'Card authorisations failing',
  summary: 'Processor returning 502',
  severity: 'SEV1',
  status: 'OPEN',
  openedByName: 'Dana',
  openedAt: '2026-03-01T10:00:00Z',
  mitigatedAt: null,
  resolvedAt: null,
  timeline: [
    { id: 'u1', message: 'Incident opened', status: 'OPEN', severity: 'SEV1', authorName: 'Dana', postedAt: '2026-03-01T10:00:00Z' },
  ],
};

function renderIncident() {
  return renderRoute(<IncidentPage />, { path: '/incidents/:incidentId', url: '/incidents/i1' });
}

describe('IncidentPage', () => {
  it('shows the timeline read-only to viewers', async () => {
    signInAs('VIEWER');
    mockApi({ 'GET /api/v1/incidents/i1': { body: INCIDENT } });
    renderIncident();

    expect(await screen.findByRole('heading', { name: 'Card authorisations failing' })).toBeInTheDocument();
    expect(screen.getByText('Incident opened')).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Post update' })).not.toBeInTheDocument();
  });

  it('lets operators post an update with a valid transition', async () => {
    signInAs('OPERATOR');
    const mitigated: IncidentDetail = { ...INCIDENT, status: 'MITIGATED' };
    let current = INCIDENT;
    const calls = mockApi({
      'GET /api/v1/incidents/i1': () => ({ body: current }),
      'POST /api/v1/incidents/i1/updates': () => {
        current = mitigated;
        return { body: mitigated };
      },
    });
    renderIncident();
    const user = userEvent.setup();

    const form = await screen.findByRole('form', { name: 'Post update' });
    const statusSelect = within(form).getByLabelText('Change status');
    expect(within(statusSelect).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Keep open',
      'Mitigated',
      'Resolved',
    ]);

    await user.type(within(form).getByLabelText('Update'), 'Failed over to secondary');
    await user.selectOptions(statusSelect, 'MITIGATED');
    await user.click(within(form).getByRole('button', { name: 'Post update' }));

    expect(await screen.findByText('Mitigated', { selector: '.badge' })).toBeInTheDocument();
    expect(calls.find((call) => call.method === 'POST')?.body).toEqual({
      message: 'Failed over to secondary',
      status: 'MITIGATED',
    });
  });

  it('hides the update form once resolved', async () => {
    signInAs('ADMIN');
    mockApi({ 'GET /api/v1/incidents/i1': { body: { ...INCIDENT, status: 'RESOLVED', resolvedAt: '2026-03-01T12:00:00Z' } } });
    renderIncident();

    expect(await screen.findByText('Resolved', { selector: '.badge' })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Post update' })).not.toBeInTheDocument();
  });
});
