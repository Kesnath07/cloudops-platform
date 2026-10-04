import type { FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { auth } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/Feedback';
import { useAction } from '../hooks/useAction';
import { requiredField } from '../lib/format';

async function registerAndSignIn(
  login: (email: string, password: string) => Promise<void>,
  input: { email: string; displayName: string; password: string },
) {
  await auth.register(input);
  await login(input.email, input.password);
}

export function RegisterPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const register = useAction(registerAndSignIn);

  if (user) {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = {
      email: requiredField(form, 'email'),
      displayName: requiredField(form, 'displayName'),
      password: String(form.get('password') ?? ''),
    };
    if (await register.run(login, input)) {
      navigate('/', { replace: true });
    }
  }

  return (
    <section className="panel narrow">
      <h1>Create account</h1>
      <p className="muted">New accounts have read-only access until an administrator grants a role.</p>
      <ErrorBanner error={register.error} />
      <form onSubmit={handleSubmit} className="form">
        <label>
          Email
          <input name="email" type="email" autoComplete="username" required maxLength={254} />
        </label>
        <label>
          Display name
          <input name="displayName" required maxLength={100} />
        </label>
        <label>
          Password
          <input name="password" type="password" autoComplete="new-password" required minLength={12} maxLength={72} />
          <small className="muted">12 to 72 characters.</small>
        </label>
        <button type="submit" disabled={register.pending}>
          {register.pending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="muted">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </section>
  );
}
