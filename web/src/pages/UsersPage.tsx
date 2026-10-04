import { useState } from 'react';
import { users } from '../api/endpoints';
import type { Role } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner, Loading } from '../components/Feedback';
import { Pager } from '../components/Pager';
import { useAction } from '../hooks/useAction';
import { useApi } from '../hooks/useApi';
import { formatDateTime } from '../lib/format';

const ROLES: Role[] = ['VIEWER', 'OPERATOR', 'ADMIN'];

export function UsersPage() {
  const { user: currentUser } = useAuth();
  const [page, setPage] = useState(0);
  const list = useApi(`users-${page}`, () => users.list(page));
  const changeRole = useAction(users.changeRole);

  async function handleRoleChange(userId: string, role: Role) {
    if (await changeRole.run(userId, role)) {
      list.reload();
    }
  }

  return (
    <section className="panel">
      <h1>Users</h1>
      <ErrorBanner error={list.error ?? changeRole.error} onRetry={list.reload} />
      {list.loading && !list.data && <Loading />}
      {list.data && (
        <table>
          <thead>
            <tr>
              <th>User</th>
              <th>Registered</th>
              <th>Role</th>
            </tr>
          </thead>
          <tbody>
            {list.data.items.map((account) => (
              <tr key={account.id}>
                <td>
                  {account.displayName}
                  <div className="muted small">{account.email}</div>
                </td>
                <td>{formatDateTime(account.createdAt)}</td>
                <td>
                  <select
                    aria-label={`Role for ${account.email}`}
                    value={account.role}
                    disabled={account.id === currentUser?.id || changeRole.pending}
                    onChange={(event) => void handleRoleChange(account.id, event.target.value as Role)}
                  >
                    {ROLES.map((role) => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {list.data && <Pager page={list.data} onChange={setPage} />}
    </section>
  );
}
