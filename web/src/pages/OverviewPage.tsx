import { Link } from 'react-router';
import { workloads } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { hasRole } from '../auth/roles';
import { HealthBadge } from '../components/Badge';
import { ErrorBanner, Loading } from '../components/Feedback';
import { useApi } from '../hooks/useApi';
import { formatDateTime, humanize } from '../lib/format';

export function OverviewPage() {
  const { user } = useAuth();
  const overview = useApi('overview', workloads.overview);
  const rows = overview.data ?? [];
  const impacted = rows.filter((row) => row.health !== 'OPERATIONAL').length;

  return (
    <section className="panel">
      <div className="heading">
        <div>
          <h1>Workload overview</h1>
          {overview.data && (
            <p className="muted">
              {rows.length} workloads · {impacted === 0 ? 'all operational' : `${impacted} impacted`}
            </p>
          )}
        </div>
        {hasRole(user?.role, 'OPERATOR') && (
          <Link className="button" to="/workloads/new">
            Register workload
          </Link>
        )}
      </div>
      <ErrorBanner error={overview.error} onRetry={overview.reload} />
      {overview.loading && !overview.data && <Loading />}
      {overview.data && rows.length === 0 && <p>No workloads registered yet.</p>}
      {rows.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Workload</th>
              <th>Team</th>
              <th>Criticality</th>
              <th>Health</th>
              <th>Active incidents</th>
              <th>Production release</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.workload.id}>
                <td>
                  <Link to={`/workloads/${row.workload.id}`}>{row.workload.name}</Link>
                  <div className="muted small">{row.workload.slug}</div>
                </td>
                <td>{row.workload.team.name}</td>
                <td>{humanize(row.workload.criticality)}</td>
                <td>
                  <HealthBadge health={row.health} />
                </td>
                <td>{row.activeIncidents}</td>
                <td>
                  {row.lastProductionDeployment ? (
                    <>
                      {row.lastProductionDeployment.version}
                      <div className="muted small">{formatDateTime(row.lastProductionDeployment.deployedAt)}</div>
                    </>
                  ) : (
                    <span className="muted">Never deployed</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
