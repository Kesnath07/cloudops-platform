import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { hasRole } from '../auth/roles';

export function Layout() {
  const { user, logout } = useAuth();

  return (
    <div className="shell">
      <header className="topbar">
        <NavLink to="/" className="brand">
          CloudOps Platform
        </NavLink>
        {user && (
          <nav aria-label="Main">
            <NavLink to="/" end>
              Overview
            </NavLink>
            <NavLink to="/incidents">Incidents</NavLink>
            <NavLink to="/teams">Teams</NavLink>
            {hasRole(user.role, 'ADMIN') && <NavLink to="/admin/users">Users</NavLink>}
          </nav>
        )}
        {user && (
          <div className="account">
            <NavLink to="/account" title="Account settings">
              {user.displayName} · {user.role.toLowerCase()}
            </NavLink>
            <button type="button" className="link" onClick={logout}>
              Sign out
            </button>
          </div>
        )}
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
