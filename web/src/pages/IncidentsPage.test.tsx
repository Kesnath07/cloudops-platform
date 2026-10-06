import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Incident, Page } from '../api/types';
import { mockApi, renderRoute, signInAs } from '../test/harness';
import { IncidentsPage } from './IncidentsPage';

function incident(id: string, title: string): Incident {
  return {
    id,
    workload: { id: 'w1', slug: 'payments-api', name: 'Payments API' },
    title,
    severity: 'SEV2',
    status: 'OPEN',
    openedByName: 'Olu Operator',
    openedAt: '2026-03-01T10:00:00Z',
    mitigatedAt: null,
    resolvedAt: null,
  };
}

function page(items: Incident[], number: number, totalPages: number): Page<Incident> {
  return { items, page: number, size: 20, totalItems: totalPages * 20, totalPages };
}

function renderIncidents(url = '/incidents') {
  return renderRoute(<IncidentsPage />, { path: '/incidents', url });
}

describe('IncidentsPage', () => {
  it('falls back to defaults for an invalid status or page in the URL', async () => {
    signInAs('VIEWER');
    const calls = mockApi({
      'GET /api/v1/incidents?page=0&size=20': { body: page([incident('i1', 'Checkout errors')], 0, 1) },
    });
    renderIncidents('/incidents?status=EXPLODED&page=abc');

    expect(await screen.findByRole('link', { name: 'Checkout errors' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'true');
    expect(calls.map((call) => call.path)).toEqual(['/api/v1/incidents?page=0&size=20']);
  });

  it('filters by status and pages through results', async () => {
    signInAs('VIEWER');
    const calls = mockApi({
      'GET /api/v1/incidents?page=0&size=20': { body: page([incident('i1', 'Checkout errors')], 0, 2) },
      'GET /api/v1/incidents?page=1&size=20': { body: page([incident('i2', 'Search latency')], 1, 2) },
      'GET /api/v1/incidents?status=MITIGATED&page=0&size=20': { body: page([], 0, 0) },
    });
    renderIncidents();
    const user = userEvent.setup();

    await screen.findByText('Page 1 of 2');
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByRole('link', { name: 'Search latency' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Mitigated' }));
    expect(await screen.findByText('No incidents match this filter.')).toBeInTheDocument();
    expect(calls.at(-1)?.path).toBe('/api/v1/incidents?status=MITIGATED&page=0&size=20');
  });

  it('shows the open-incident action only to operators', async () => {
    signInAs('OPERATOR');
    mockApi({ 'GET /api/v1/incidents?page=0&size=20': { body: page([], 0, 0) } });
    renderIncidents();

    expect(await screen.findByRole('link', { name: 'Open incident' })).toHaveAttribute('href', '/incidents/new');
  });
});
