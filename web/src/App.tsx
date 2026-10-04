import { Route, Routes } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { AccountPage } from './pages/AccountPage';
import { IncidentPage } from './pages/IncidentPage';
import { IncidentsPage } from './pages/IncidentsPage';
import { LoginPage } from './pages/LoginPage';
import { NewIncidentPage } from './pages/NewIncidentPage';
import { NewWorkloadPage } from './pages/NewWorkloadPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { OverviewPage } from './pages/OverviewPage';
import { RegisterPage } from './pages/RegisterPage';
import { TeamsPage } from './pages/TeamsPage';
import { UsersPage } from './pages/UsersPage';
import { WorkloadPage } from './pages/WorkloadPage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route element={<RequireAuth />}>
          <Route index element={<OverviewPage />} />
          <Route path="workloads/:workloadId" element={<WorkloadPage />} />
          <Route path="incidents" element={<IncidentsPage />} />
          <Route path="incidents/:incidentId" element={<IncidentPage />} />
          <Route path="teams" element={<TeamsPage />} />
          <Route path="account" element={<AccountPage />} />
        </Route>
        <Route element={<RequireAuth role="OPERATOR" />}>
          <Route path="workloads/new" element={<NewWorkloadPage />} />
          <Route path="incidents/new" element={<NewIncidentPage />} />
        </Route>
        <Route element={<RequireAuth role="ADMIN" />}>
          <Route path="admin/users" element={<UsersPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
