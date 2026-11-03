import { createBrowserRouter, Navigate } from 'react-router';
import { AppShell } from './AppShell';
import { AuthGuard } from '../features/auth/AuthGuard';
import { LoginPage } from '../features/auth/LoginPage';
import { ProjectsPage } from '../features/projects/ProjectsPage';
import { SettingsPage } from '../features/settings/SettingsPage';
import { HeatmapPage } from '../features/heatmap/HeatmapPage';
import { LeavesAndHolidaysPage } from '../features/leaves/LeavesAndHolidaysPage';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <AuthGuard>
        <AppShell />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/beban-kerja" replace /> },
      { path: 'beban-kerja', element: <HeatmapPage /> },
      { path: 'proyek', element: <ProjectsPage /> },
      { path: 'alokasi', element: <Navigate to="/beban-kerja" replace /> },
      { path: 'cuti', element: <LeavesAndHolidaysPage /> },
      { path: 'pengaturan', element: <SettingsPage /> },
      { path: '*', element: <Navigate to="/beban-kerja" replace /> },
    ],
  },
]);

export default router;
