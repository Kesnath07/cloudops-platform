import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Page, User } from '../api/types';
import { mockApi, renderRoute, signInAs, testUser } from '../test/harness';
import { UsersPage } from './UsersPage';

const LIST = 'GET /api/v1/users?page=0&size=50';

function page(items: User[]): Page<User> {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: items.length > 0 ? 1 : 0 };
}

function renderUsers() {
  return renderRoute(<UsersPage />, { path: '/admin/users', url: '/admin/users' });
}

describe('UsersPage', () => {
  it('changes another user role and reloads the list, but never the current user', async () => {
    const admin = signInAs('ADMIN');
    const viewer = testUser('VIEWER');
    let promoted = false;
    const calls = mockApi({
      [LIST]: () => ({ body: page([admin, { ...viewer, role: promoted ? 'OPERATOR' : 'VIEWER' }]) }),
      [`PUT /api/v1/users/${viewer.id}/role`]: () => {
        promoted = true;
        return { body: { ...viewer, role: 'OPERATOR' } };
      },
    });
    renderUsers();
    const user = userEvent.setup();

    expect(await screen.findByLabelText(`Role for ${admin.email}`)).toBeDisabled();
    await user.selectOptions(screen.getByLabelText(`Role for ${viewer.email}`), 'OPERATOR');

    expect(await screen.findByLabelText(`Role for ${viewer.email}`)).toHaveValue('OPERATOR');
    expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({ role: 'OPERATOR' });
    expect(calls.filter((call) => call.path.startsWith('/api/v1/users?'))).toHaveLength(2);
  });

  it('reports a failed role change without offering a list retry', async () => {
    const admin = signInAs('ADMIN');
    const viewer = testUser('VIEWER');
    mockApi({
      [LIST]: { body: page([admin, viewer]) },
      [`PUT /api/v1/users/${viewer.id}/role`]: {
        status: 422,
        body: { title: 'Business rule violated', detail: 'Not allowed' },
      },
    });
    renderUsers();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText(`Role for ${viewer.email}`), 'ADMIN');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Not allowed');
    expect(within(alert).queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('offers a retry when the list fails to load', async () => {
    signInAs('ADMIN');
    let attempts = 0;
    mockApi({
      [LIST]: () => {
        attempts += 1;
        return attempts === 1 ? { status: 500, body: { title: 'Internal error', detail: 'Unexpected' } } : { body: page([]) };
      },
    });
    renderUsers();
    const user = userEvent.setup();

    await user.click(within(await screen.findByRole('alert')).getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('No user accounts on this page.')).toBeInTheDocument();
  });
});
