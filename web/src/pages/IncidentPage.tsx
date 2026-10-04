import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { incidents } from '../api/endpoints';
import type { IncidentStatus, Severity } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { hasRole } from '../auth/roles';
import { SeverityBadge, StatusBadge } from '../components/Badge';
import { ErrorBanner, Loading } from '../components/Feedback';
import { useAction } from '../hooks/useAction';
import { useApi } from '../hooks/useApi';
import { formatDateTime, humanize, optionalField, requiredField } from '../lib/format';
import { nextStatuses } from '../lib/incidents';

export function IncidentPage() {
  const { incidentId = '' } = useParams();
  const { user } = useAuth();
  const incident = useApi(`incident-${incidentId}`, () => incidents.get(incidentId));
  const post = useAction((input: Parameters<typeof incidents.postUpdate>[1]) => incidents.postUpdate(incidentId, input));

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const ok = await post.run({
      message: requiredField(form, 'message'),
      status: optionalField(form, 'status') as IncidentStatus | undefined,
      severity: optionalField(form, 'severity') as Severity | undefined,
    });
    if (ok) {
      formElement.reset();
      incident.reload();
    }
  }

  if (incident.error) {
    return <ErrorBanner error={incident.error} onRetry={incident.reload} />;
  }
  if (!incident.data) {
    return <Loading />;
  }
  const details = incident.data;
  const transitions = nextStatuses(details.status);
  const canUpdate = hasRole(user?.role, 'OPERATOR') && details.status !== 'RESOLVED';

  return (
    <>
      <section className="panel">
        <p className="muted">
          <Link to={`/workloads/${details.workload.id}`}>{details.workload.name}</Link>
        </p>
        <h1>{details.title}</h1>
        <p>
          <SeverityBadge severity={details.severity} /> <StatusBadge status={details.status} />
        </p>
        {details.summary && <p>{details.summary}</p>}
        <dl className="facts">
          <dt>Opened</dt>
          <dd>
            {formatDateTime(details.openedAt)} by {details.openedByName}
          </dd>
          <dt>Mitigated</dt>
          <dd>{formatDateTime(details.mitigatedAt)}</dd>
          <dt>Resolved</dt>
          <dd>{formatDateTime(details.resolvedAt)}</dd>
        </dl>
      </section>

      <section className="panel">
        <h2>Timeline</h2>
        <ol className="timeline">
          {details.timeline.map((entry) => (
            <li key={entry.id}>
              <div className="muted small">
                {formatDateTime(entry.postedAt)} · {entry.authorName} · {humanize(entry.status)} · {entry.severity}
              </div>
              <p>{entry.message}</p>
            </li>
          ))}
        </ol>

        {canUpdate && (
          <form onSubmit={handleUpdate} className="form" aria-label="Post update">
            <h3>Post an update</h3>
            <ErrorBanner error={post.error} />
            <label>
              Update
              <textarea name="message" required rows={3} maxLength={4000} />
            </label>
            <div className="grid">
              <label>
                Change status
                <select name="status" defaultValue="">
                  <option value="">Keep {humanize(details.status).toLowerCase()}</option>
                  {transitions.map((status) => (
                    <option key={status} value={status}>
                      {humanize(status)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Change severity
                <select name="severity" defaultValue="">
                  <option value="">Keep {details.severity}</option>
                  {(['SEV1', 'SEV2', 'SEV3', 'SEV4'] as const)
                    .filter((severity) => severity !== details.severity)
                    .map((severity) => (
                      <option key={severity} value={severity}>
                        {severity}
                      </option>
                    ))}
                </select>
              </label>
            </div>
            <button type="submit" disabled={post.pending}>
              Post update
            </button>
          </form>
        )}
      </section>
    </>
  );
}
