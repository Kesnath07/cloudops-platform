import type { FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { teams, workloads } from '../api/endpoints';
import type { Criticality } from '../api/types';
import { ErrorBanner, Loading } from '../components/Feedback';
import { useAction } from '../hooks/useAction';
import { useApi } from '../hooks/useApi';
import { optionalField, requiredField } from '../lib/format';

export function NewWorkloadPage() {
  const navigate = useNavigate();
  const teamList = useApi('teams', teams.list);
  const create = useAction(async (input: Parameters<typeof workloads.create>[0]) => {
    const workload = await workloads.create(input);
    navigate(`/workloads/${workload.id}`);
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    void create.run({
      slug: requiredField(form, 'slug'),
      name: requiredField(form, 'name'),
      description: optionalField(form, 'description'),
      teamId: requiredField(form, 'teamId'),
      criticality: requiredField(form, 'criticality') as Criticality,
      repositoryUrl: optionalField(form, 'repositoryUrl'),
      runbookUrl: optionalField(form, 'runbookUrl'),
    });
  }

  if (teamList.loading && !teamList.data) {
    return <Loading />;
  }
  if (!teamList.data) {
    // Without the team list the owning-team field would be empty and every submission rejected.
    return (
      <section className="panel narrow">
        <h1>Register workload</h1>
        <ErrorBanner error={teamList.error} onRetry={teamList.reload} />
      </section>
    );
  }

  return (
    <section className="panel narrow">
      <h1>Register workload</h1>
      <ErrorBanner error={create.error} />
      {teamList.data.length === 0 ? (
        <p>An administrator must create a team before workloads can be registered.</p>
      ) : (
        <form onSubmit={handleSubmit} className="form">
          <label>
            Slug
            <input name="slug" required pattern="[a-z0-9]([a-z0-9-]*[a-z0-9])?" maxLength={63} placeholder="orders-api" />
          </label>
          <label>
            Name
            <input name="name" required maxLength={100} />
          </label>
          <label>
            Owning team
            <select name="teamId" required>
              {teamList.data.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Criticality
            <select name="criticality" defaultValue="MEDIUM">
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </label>
          <label>
            Description
            <textarea name="description" maxLength={2000} rows={3} />
          </label>
          <label>
            Repository URL
            <input name="repositoryUrl" type="url" pattern="https://.*" maxLength={500} />
          </label>
          <label>
            Runbook URL
            <input name="runbookUrl" type="url" pattern="https://.*" maxLength={500} />
          </label>
          <button type="submit" disabled={create.pending}>
            Register
          </button>
        </form>
      )}
    </section>
  );
}
