import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { loadSession } from '../auth/session';
import { mockApi, renderRoute, testUser } from '../test/harness';
import { LoginPage } from './LoginPage';

async function submit(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  it('stores the session and leaves the login page on success', async () => {
    const calls = mockApi({
      'POST /api/v1/auth/token': {
        body: { accessToken: 'jwt-value', tokenType: 'Bearer', expiresIn: 3600, user: testUser('OPERATOR') },
      },
    });
    renderRoute(<LoginPage />, { path: '/signin', url: '/signin' });

    await submit('operator@cloudops.test', 'correct-horse-battery');

    expect(await screen.findByText('Navigated elsewhere')).toBeInTheDocument();
    expect(calls[0]?.body).toEqual({ email: 'operator@cloudops.test', password: 'correct-horse-battery' });
    expect(loadSession()?.user.role).toBe('OPERATOR');
  });

  it('shows the API error for bad credentials', async () => {
    mockApi({
      'POST /api/v1/auth/token': { status: 401, body: { title: 'Authentication failed', detail: 'Invalid email or password' } },
    });
    renderRoute(<LoginPage />, { path: '/signin', url: '/signin' });

    await submit('someone@cloudops.test', 'wrong-password');

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(loadSession()).toBeNull();
  });
});
