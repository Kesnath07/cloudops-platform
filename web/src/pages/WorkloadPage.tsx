import type { FormEvent } from 'react';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { deployments, incidents, workloads } from '../api/endpoints';
import type { DeploymentEnvironment, DeploymentOutcome } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { hasRole } from '../auth/roles';
import { SeverityBadge, StatusBadge } from '../components/Badge';
import { ErrorBanner, Loading } from '../components/Feedback';
import { Pager } from '../components/Pager';
import { useAction } from '../hooks/useAction';
import { useApi } from '../hooks/useApi';
import { formatDateTime, humanize, optionalField, requiredField } from '../lib/format';
import { ENVIRONMENT_LABELS, OUTCOME_LABELS, optionsOf } from '../lib/options';

export function WorkloadPage() {
  const { workloadId = '' } = useParams();
  const { user } = useAuth();
  const canOperate = hasRole(user?.role, 'OPERATOR');
  const [deploymentPage, setDeploymentPage] = useState(0);

  const workload = useApi(`workload-${workloadId}`, () => workloads.get(workloadId));
  const history = useApi(`deployments-${workloadId}-${deploymentPage}`, () =>
    deployments.history(workloadId, deploymentPage),
  );
  const activeIncidents = useApi(`incidents-${workloadId}`, () => incidents.list({ workloadId }));
  const record = useAction((input: Parameters<typeof deployments.record>[1]) => deployments.record(workloadId, input));

  async function handleRecord(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const ok = await record.run({
      environment: requiredField(form, 'environment') as DeploymentEnvironment,
      version: requiredField(form, 'version'),
      commitSha: optionalField(form, 'commitSha'),
      outcome: requiredField(form, 'outcome') as DeploymentOutcome,
      notes: optionalField(form, 'notes'),
    });
    if (ok) {
      formElement.reset();
      setDeploymentPage(0);
      history.reload();
    }
  }

  if (workload.error) {
    return <ErrorBanner error={workload.error} onRetry={workload.reload} />;
  }
  if (!workload.data) {
    return <Loading />;
  }
  const details = workload.data;

  return (
    <>
      <section className="panel">
        <div className="heading">
          <div>
            <h1>{details.name}</h1>
            <p className="muted">
              {details.slug} · owned by {details.team.name} · {humanize(details.criticality)} criticality
            </p>
          </div>
          {canOperate && (
            <Link className="button danger" to={`/incidents/new?workloadId=${details.id}`}>
              Open incident
            </Link>
          )}
        </div>
        {details.description && <p>{details.description}</p>}
        <p className="links">
          {details.repositoryUrl && (
            <a href={details.repositoryUrl} target="_blank" rel="noreferrer noopener">
              Repository
            </a>
          )}
          {details.runbookUrl && (
            <a href={details.runbookUrl} target="_blank" rel="noreferrer noopener">
              Runbook
            </a>
          )}
        </p>
      </section>

      <section className="panel">
        <h2>Incidents</h2>
        <ErrorBanner error={activeIncidents.error} onRetry={activeIncidents.reload} />
        {activeIncidents.loading && !activeIncidents.data && <Loading label="Loading incidents…" />}
        {activeIncidents.data?.items.length === 0 && <p className="muted">No incidents recorded.</p>}
        <ul className="list">
          {activeIncidents.data?.items.map((incident) => (
            <li key={incident.id}>
              <SeverityBadge severity={incident.severity} /> <StatusBadge status={incident.status} />{' '}
              <Link to={`/incidents/${incident.id}`}>{incident.title}</Link>
              <span className="muted small"> · {formatDateTime(incident.openedAt)}</span>
            </li>
          ))}
        </ul>
      </section>

      <section className="panel">
        <h2>Deployment history</h2>
        <ErrorBanner error={history.error} onRetry={history.reload} />
        {history.loading && !history.data && <Loading label="Loading deployments…" />}
        {history.data && history.data.items.length === 0 && <p className="muted">No deployments recorded.</p>}
        {history.data && history.data.items.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Version</th>
                <th>Environment</th>
                <th>Outcome</th>
                <th>Deployed</th>
                <th>By</th>
              </tr>
            </thead>
            <tbody>
              {history.data.items.map((deployment) => (
                <tr key={deployment.id}>
                  <td>
                    {deployment.version}
                    {deployment.commitSha && <div className="muted small mono">{deployment.commitSha.slice(0, 12)}</div>}
                  </td>
                  <td>{ENVIRONMENT_LABELS[deployment.environment]}</td>
                  <td>{OUTCOME_LABELS[deployment.outcome]}</td>
                  <td>{formatDateTime(deployment.deployedAt)}</td>
                  <td>{deployment.deployedByName}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {history.data && <Pager page={history.data} onChange={setDeploymentPage} />}

        {canOperate && (
          <details className="inline-form">
            <summary>Record a deployment</summary>
            <ErrorBanner error={record.error} />
            <form onSubmit={handleRecord} className="form grid">
              <label>
                Environment
                <select name="environment" defaultValue="PRODUCTION">
                  {optionsOf(ENVIRONMENT_LABELS).map((environment) => (
                    <option key={environment} value={environment}>
                      {ENVIRONMENT_LABELS[environment]}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Version
                <input name="version" required maxLength={100} placeholder="1.8.0" />
              </label>
              <label>
                Commit SHA
                <input name="commitSha" pattern="[0-9a-f]{7,40}" maxLength={40} />
              </label>
              <label>
                Outcome
                <select name="outcome" defaultValue="SUCCEEDED">
                  {optionsOf(OUTCOME_LABELS).map((outcome) => (
                    <option key={outcome} value={outcome}>
                      {OUTCOME_LABELS[outcome]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="wide">
                Notes
                <input name="notes" maxLength={2000} />
              </label>
              <button type="submit" disabled={record.pending}>
                Record deployment
              </button>
            </form>
          </details>
        )}
      </section>
    </>
  );
}
