import type { FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { incidents, workloads } from '../api/endpoints';
import type { Severity } from '../api/types';
import { ErrorBanner, Loading } from '../components/Feedback';
import { useAction } from '../hooks/useAction';
import { useApi } from '../hooks/useApi';
import { optionalField, requiredField } from '../lib/format';
import { SEVERITIES, SEVERITY_DESCRIPTIONS } from '../lib/options';

export function NewIncidentPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const overview = useApi('overview', workloads.overview);
  const open = useAction(async (input: Parameters<typeof incidents.open>[0]) => {
    const incident = await incidents.open(input);
    navigate(`/incidents/${incident.id}`);
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    void open.run({
      workloadId: requiredField(form, 'workloadId'),
      title: requiredField(form, 'title'),
      summary: optionalField(form, 'summary'),
      severity: requiredField(form, 'severity') as Severity,
    });
  }

  if (overview.loading && !overview.data) {
    return <Loading />;
  }
  if (!overview.data) {
    // Without the workload list the affected-workload field would be empty and unusable.
    return (
      <section className="panel narrow">
        <h1>Open incident</h1>
        <ErrorBanner error={overview.error} onRetry={overview.reload} />
      </section>
    );
  }

  return (
    <section className="panel narrow">
      <h1>Open incident</h1>
      <ErrorBanner error={open.error} />
      <form onSubmit={handleSubmit} className="form">
        <label>
          Affected workload
          <select name="workloadId" required defaultValue={params.get('workloadId') ?? ''}>
            <option value="" disabled>
              Select a workload
            </option>
            {overview.data.map((row) => (
              <option key={row.workload.id} value={row.workload.id}>
                {row.workload.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Title
          <input name="title" required maxLength={200} placeholder="Checkout requests timing out" />
        </label>
        <label>
          Severity
          <select name="severity" defaultValue="SEV3">
            {SEVERITIES.map((severity) => (
              <option key={severity} value={severity}>
                {severity} · {SEVERITY_DESCRIPTIONS[severity]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Summary
          <textarea name="summary" rows={4} maxLength={4000} />
        </label>
        <button type="submit" className="danger" disabled={open.pending}>
          Open incident
        </button>
      </form>
    </section>
  );
}
