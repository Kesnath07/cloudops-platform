import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Team } from '../api/types';
import { mockApi, renderRoute, signInAs } from '../test/harness';
import { NewWorkloadPage } from './NewWorkloadPage';

const TEAMS: Team[] = [
  { id: 't1', slug: 'payments', name: 'Payments', description: null, contactEmail: null },
  { id: 't2', slug: 'search', name: 'Search', description: null, contactEmail: null },
];

function renderNewWorkload() {
  return renderRoute(<NewWorkloadPage />, { path: '/workloads/new', url: '/workloads/new' });
}

describe('NewWorkloadPage', () => {
  it('registers a workload with trimmed input and navigates to it', async () => {
    signInAs('OPERATOR');
    const calls = mockApi({
      'GET /api/v1/teams': { body: TEAMS },
      'POST /api/v1/workloads': { status: 201, body: { id: 'w9' } },
    });
    renderNewWorkload();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Slug'), 'search-api');
    await user.type(screen.getByLabelText('Name'), '  Search API ');
    await user.selectOptions(screen.getByLabelText('Owning team'), 't2');
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByText('Navigated elsewhere')).toBeInTheDocument();
    expect(calls.find((call) => call.method === 'POST')?.body).toEqual({
      slug: 'search-api',
      name: 'Search API',
      teamId: 't2',
      criticality: 'MEDIUM',
    });
  });

  it('replaces the form with a retryable error when teams cannot be loaded', async () => {
    signInAs('OPERATOR');
    let attempts = 0;
    mockApi({
      'GET /api/v1/teams': () => {
        attempts += 1;
        return attempts === 1 ? { status: 500, body: { title: 'Internal error', detail: 'Unexpected' } } : { body: TEAMS };
      },
    });
    renderNewWorkload();
    const user = userEvent.setup();

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal error');
    expect(screen.queryByLabelText('Owning team')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByLabelText('Owning team')).toHaveValue('t1');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('explains that a team is needed when none exist', async () => {
    signInAs('OPERATOR');
    mockApi({ 'GET /api/v1/teams': { body: [] } });
    renderNewWorkload();

    expect(await screen.findByText(/must create a team/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Register' })).not.toBeInTheDocument();
  });

  it('keeps the input and shows a conflict reported by the API', async () => {
    signInAs('OPERATOR');
    mockApi({
      'GET /api/v1/teams': { body: TEAMS },
      'POST /api/v1/workloads': {
        status: 409,
        body: { title: 'Conflict', detail: "A workload with slug 'search-api' already exists" },
      },
    });
    renderNewWorkload();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Slug'), 'search-api');
    await user.type(screen.getByLabelText('Name'), 'Search API');
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByRole('alert')).toHaveTextContent("A workload with slug 'search-api' already exists");
    expect(screen.getByLabelText('Slug')).toHaveValue('search-api');
  });
});
