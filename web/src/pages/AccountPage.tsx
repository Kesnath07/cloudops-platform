import type { FormEvent } from 'react';
import { useState } from 'react';
import { platform, users } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/Feedback';
import { useAction } from '../hooks/useAction';
import { useApi } from '../hooks/useApi';

export function AccountPage() {
  const { user } = useAuth();
  const info = useApi('platform-info', platform.info);
  const change = useAction(users.changePassword);
  const [changed, setChanged] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setChanged(false);
    const ok = await change.run({
      currentPassword: String(form.get('currentPassword') ?? ''),
      newPassword: String(form.get('newPassword') ?? ''),
    });
    if (ok) {
      formElement.reset();
      setChanged(true);
    }
  }

  return (
    <>
      <section className="panel">
        <h1>Account</h1>
        <dl className="facts">
          <dt>Name</dt>
          <dd>{user?.displayName}</dd>
          <dt>Email</dt>
          <dd>{user?.email}</dd>
          <dt>Role</dt>
          <dd>{user?.role}</dd>
        </dl>
      </section>
      <section className="panel">
        <h2>Change password</h2>
        <ErrorBanner error={change.error} />
        {changed && <p role="status">Password updated.</p>}
        <form onSubmit={handleSubmit} className="form">
          <label>
            Current password
            <input name="currentPassword" type="password" autoComplete="current-password" required />
          </label>
          <label>
            New password
            <input name="newPassword" type="password" autoComplete="new-password" required minLength={12} maxLength={72} />
          </label>
          <button type="submit" disabled={change.pending}>
            Update password
          </button>
        </form>
      </section>
      {info.data && (
        <p className="muted small">
          API {info.data.version} · release {info.data.release}
        </p>
      )}
    </>
  );
}
