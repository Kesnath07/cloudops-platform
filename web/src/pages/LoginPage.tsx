import type { FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/Feedback';
import { useAction } from '../hooks/useAction';
import { requiredField } from '../lib/format';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const destination = (location.state as { from?: string } | null)?.from ?? '/';
  const signIn = useAction(login);

  if (user) {
    return <Navigate to={destination} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    if (await signIn.run(requiredField(form, 'email'), requiredField(form, 'password'))) {
      navigate(destination, { replace: true });
    }
  }

  return (
    <section className="panel narrow">
      <h1>Sign in</h1>
      <ErrorBanner error={signIn.error} />
      <form onSubmit={handleSubmit} className="form">
        <label>
          Email
          <input name="email" type="email" autoComplete="username" required />
        </label>
        <label>
          Password
          <input name="password" type="password" autoComplete="current-password" required />
        </label>
        <button type="submit" disabled={signIn.pending}>
          {signIn.pending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="muted">
        No account yet? <Link to="/register">Create one</Link>
      </p>
    </section>
  );
}
