import React from 'react';
import { createBrowserRouter, Navigate } from 'react-router';
import { AppShell } from './AppShell';

const WorkloadView: React.FC = () => (
  <div className="space-y-3">
    <div className="flex items-center justify-between pb-2 border-b border-slate-200">
      <div>
        <h1 className="text-base font-semibold text-slate-900">Beban Kerja Tim (Orang × Minggu)</h1>
        <p className="text-xs text-slate-500">Matriks perencanaan kapasitas bersih dan utilisasi kerja</p>
      </div>
      <div className="flex items-center space-x-2">
        <span className="text-xs font-mono px-2 py-1 rounded bg-slate-100 border border-slate-200 text-slate-700">
          ISO 8601 · Sen-Jum
        </span>
      </div>
    </div>
    <div className="p-4 bg-white rounded border border-slate-200 text-xs text-slate-600">
      <div className="flex items-center space-x-2 text-slate-500">
        <span className="w-2 h-2 rounded-full bg-slate-400" />
        <span>Heatmap akan aktif setelah data alokasi dan jam kerja dimuat (Fase 1 Minggu 4–5).</span>
      </div>
    </div>
  </div>
);

const ProjectsView: React.FC = () => (
  <div className="space-y-3">
    <div className="flex items-center justify-between pb-2 border-b border-slate-200">
      <div>
        <h1 className="text-base font-semibold text-slate-900">Proyek & Task</h1>
        <p className="text-xs text-slate-500">Daftar proyek aktif dan input task cepat</p>
      </div>
    </div>
    <div className="p-4 bg-white rounded border border-slate-200 text-xs text-slate-600">
      <span>Daftar proyek dan laci tugas (Fase 1 Minggu 2–3).</span>
    </div>
  </div>
);

const AllocationsView: React.FC = () => (
  <div className="space-y-3">
    <div className="flex items-center justify-between pb-2 border-b border-slate-200">
      <div>
        <h1 className="text-base font-semibold text-slate-900">Alokasi Tim</h1>
        <p className="text-xs text-slate-500">Penugasan waktu dan validasi batas beban</p>
      </div>
    </div>
    <div className="p-4 bg-white rounded border border-slate-200 text-xs text-slate-600">
      <span>Form alokasi dan pratinjau overlap kapasitas (Fase 1 Minggu 4).</span>
    </div>
  </div>
);

const LeavesView: React.FC = () => (
  <div className="space-y-3">
    <div className="flex items-center justify-between pb-2 border-b border-slate-200">
      <div>
        <h1 className="text-base font-semibold text-slate-900">Cuti & Libur Nasional</h1>
        <p className="text-xs text-slate-500">Pengurangan kapasitas dan kalender hari libur</p>
      </div>
    </div>
    <div className="p-4 bg-white rounded border border-slate-200 text-xs text-slate-600">
      <span>Pencatatan cuti dan impor kalender libur nasional (Fase 1 Minggu 5).</span>
    </div>
  </div>
);

const SettingsView: React.FC = () => (
  <div className="space-y-3">
    <div className="flex items-center justify-between pb-2 border-b border-slate-200">
      <div>
        <h1 className="text-base font-semibold text-slate-900">Pengaturan Sistem</h1>
        <p className="text-xs text-slate-500">Konfigurasi ambang beban kerja, pengguna, dan audit</p>
      </div>
    </div>
    <div className="p-4 bg-white rounded border border-slate-200 text-xs text-slate-600">
      <span>Pengaturan ambang utilisasi dan hak akses tim (Fase 1 Minggu 2 & 6).</span>
    </div>
  </div>
);

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/beban-kerja" replace /> },
      { path: 'beban-kerja', element: <WorkloadView /> },
      { path: 'proyek', element: <ProjectsView /> },
      { path: 'alokasi', element: <AllocationsView /> },
      { path: 'cuti', element: <LeavesView /> },
      { path: 'pengaturan', element: <SettingsView /> },
      { path: '*', element: <Navigate to="/beban-kerja" replace /> },
    ],
  },
]);

export default router;
