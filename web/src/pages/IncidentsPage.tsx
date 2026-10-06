import { Link, useSearchParams } from 'react-router';
import { incidents } from '../api/endpoints';
import type { IncidentStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { hasRole } from '../auth/roles';
import { SeverityBadge, StatusBadge } from '../components/Badge';
import { ErrorBanner, Loading } from '../components/Feedback';
import { Pager } from '../components/Pager';
import { useApi } from '../hooks/useApi';
import { formatDateTime } from '../lib/format';

const FILTERS: { label: string; value: IncidentStatus | '' }[] = [
  { label: 'All', value: '' },
  { label: 'Open', value: 'OPEN' },
  { label: 'Mitigated', value: 'MITIGATED' },
  { label: 'Resolved', value: 'RESOLVED' },
];

/** The URL can be edited by hand; unknown values fall back to the defaults instead of failing the request. */
function parseStatus(value: string | null): IncidentStatus | '' {
  return FILTERS.find((filter) => filter.value === value)?.value ?? '';
}

function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isSafeInteger(page) && page > 0 ? page : 0;
}

export function IncidentsPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const status = parseStatus(params.get('status'));
  const page = parsePage(params.get('page'));
  const list = useApi(`incidents-${status}-${page}`, () =>
    incidents.list({ status: status || undefined, page }),
  );

  function update(next: { status?: string; page?: number }) {
    const search = new URLSearchParams();
    const nextStatus = next.status ?? status;
    if (nextStatus) {
      search.set('status', nextStatus);
    }
    if (next.page) {
      search.set('page', String(next.page));
    }
    setParams(search);
  }

  return (
    <section className="panel">
      <div className="heading">
        <h1>Incidents</h1>
        {hasRole(user?.role, 'OPERATOR') && (
          <Link className="button danger" to="/incidents/new">
            Open incident
          </Link>
        )}
      </div>
      <div className="filters" role="group" aria-label="Filter by status">
        {FILTERS.map((filter) => (
          <button
            key={filter.label}
            type="button"
            className={filter.value === status ? 'chip active' : 'chip'}
            aria-pressed={filter.value === status}
            onClick={() => update({ status: filter.value, page: 0 })}
          >
            {filter.label}
          </button>
        ))}
      </div>
      <ErrorBanner error={list.error} onRetry={list.reload} />
      {list.loading && !list.data && <Loading />}
      {list.data?.items.length === 0 && <p className="muted">No incidents match this filter.</p>}
      {list.data && list.data.items.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Severity</th>
              <th>Incident</th>
              <th>Workload</th>
              <th>Status</th>
              <th>Opened</th>
            </tr>
          </thead>
          <tbody>
            {list.data.items.map((incident) => (
              <tr key={incident.id}>
                <td>
                  <SeverityBadge severity={incident.severity} />
                </td>
                <td>
                  <Link to={`/incidents/${incident.id}`}>{incident.title}</Link>
                </td>
                <td>
                  <Link to={`/workloads/${incident.workload.id}`}>{incident.workload.name}</Link>
                </td>
                <td>
                  <StatusBadge status={incident.status} />
                </td>
                <td>
                  {formatDateTime(incident.openedAt)}
                  <div className="muted small">by {incident.openedByName}</div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {list.data && <Pager page={list.data} onChange={(next) => update({ page: next })} />}
    </section>
  );
}
