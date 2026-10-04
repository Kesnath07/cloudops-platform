import { Navigate, Outlet, useLocation } from 'react-router';
import type { Role } from '../api/types';
import { useAuth } from './AuthContext';
import { hasRole } from './roles';

/** Route guard: unauthenticated users go to the login page, under-privileged users see a notice. */
export function RequireAuth({ role = 'VIEWER' }: { role?: Role }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!hasRole(user.role, role)) {
    return (
      <section className="panel">
        <h1>Not permitted</h1>
        <p>This page requires the {role} role. Ask an administrator if you need access.</p>
      </section>
    );
  }
  return <Outlet />;
}
