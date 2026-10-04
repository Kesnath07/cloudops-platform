import type { FormEvent } from 'react';
import { teams } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { hasRole } from '../auth/roles';
import { ErrorBanner, Loading } from '../components/Feedback';
import { useAction } from '../hooks/useAction';
import { useApi } from '../hooks/useApi';
import { optionalField, requiredField } from '../lib/format';

export function TeamsPage() {
  const { user } = useAuth();
  const list = useApi('teams', teams.list);
  const create = useAction(teams.create);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const ok = await create.run({
      slug: requiredField(form, 'slug'),
      name: requiredField(form, 'name'),
      description: optionalField(form, 'description'),
      contactEmail: optionalField(form, 'contactEmail'),
    });
    if (ok) {
      formElement.reset();
      list.reload();
    }
  }

  return (
    <section className="panel">
      <h1>Teams</h1>
      <ErrorBanner error={list.error} onRetry={list.reload} />
      {list.loading && !list.data && <Loading />}
      {list.data?.length === 0 && <p className="muted">No teams yet.</p>}
      {list.data && list.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Team</th>
              <th>Description</th>
              <th>Contact</th>
            </tr>
          </thead>
          <tbody>
            {list.data.map((team) => (
              <tr key={team.id}>
                <td>
                  {team.name}
                  <div className="muted small">{team.slug}</div>
                </td>
                <td>{team.description}</td>
                <td>{team.contactEmail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {hasRole(user?.role, 'ADMIN') && (
        <details className="inline-form">
          <summary>Create a team</summary>
          <ErrorBanner error={create.error} />
          <form onSubmit={handleCreate} className="form grid">
            <label>
              Slug
              <input name="slug" required pattern="[a-z0-9]([a-z0-9-]*[a-z0-9])?" maxLength={63} />
            </label>
            <label>
              Name
              <input name="name" required maxLength={100} />
            </label>
            <label>
              Contact email
              <input name="contactEmail" type="email" maxLength={254} />
            </label>
            <label className="wide">
              Description
              <input name="description" maxLength={1000} />
            </label>
            <button type="submit" disabled={create.pending}>
              Create team
            </button>
          </form>
        </details>
      )}
    </section>
  );
}
