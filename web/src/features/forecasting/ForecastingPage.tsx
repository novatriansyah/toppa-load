import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';

interface Scenario {
  id: string;
  name: string;
  description: string;
  createdBy: string;
  createdByName: string;
  status: 'DRAFT' | 'SIMULATED' | 'PROMOTED' | 'ARCHIVED';
  allocationsCount: number;
  createdAt: string;
  updatedAt: string;
}

interface ScenarioAllocation {
  id: string;
  scenarioId: string;
  userId: string;
  userName: string;
  projectId: string | null;
  projectName: string;
  startDate: string;
  endDate: string;
  hoursPerDay: number;
  notes: string | null;
}

interface UserSimulationDelta {
  userId: string;
  userName: string;
  capacityHours: number;
  baselineAllocatedHours: number;
  simulatedDeltaHours: number;
  totalSimulatedHours: number;
  baselineUtilPct: number;
  simulatedUtilPct: number;
  baselineStatus: string;
  simulatedStatus: string;
  isNewlyOverloaded: boolean;
}

interface SimulationResult {
  scenarioId: string;
  scenarioName: string;
  startDate: string;
  endDate: string;
  baselineOverallUtilPct: number;
  simulatedOverallUtilPct: number;
  baselineOverloadedCount: number;
  simulatedOverloadedCount: number;
  userDeltas: UserSimulationDelta[];
}

interface MemberOption {
  id: string;
  name: string;
  role: string;
}

export const ForecastingPage: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [selectedScenarioId, setSelectedScenarioId] = useState<string | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isAddAllocOpen, setIsAddAllocOpen] = useState(false);
  const [feedbackMsg, setFeedbackMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Form states
  const [newScenarioName, setNewScenarioName] = useState('');
  const [newScenarioDesc, setNewScenarioDesc] = useState('');

  // Add allocation form states
  const [allocUserId, setAllocUserId] = useState('');
  const [allocProjectName, setAllocProjectName] = useState('');
  const [allocStartDate, setAllocStartDate] = useState('');
  const [allocEndDate, setAllocEndDate] = useState('');
  const [allocHoursPerDay, setAllocHoursPerDay] = useState('4.0');
  const [allocNotes, setAllocNotes] = useState('');

  // Fetch scenarios list
  const { data: scenarios = [], isLoading: isLoadingScenarios } = useQuery<Scenario[]>({
    queryKey: ['scenarios'],
    queryFn: () => apiClient.get<Scenario[]>('/api/v1/scenarios'),
  });

  // Default select first scenario if none selected
  const activeScenarioId = selectedScenarioId || (scenarios.length > 0 ? scenarios[0].id : null);
  const currentScenario = scenarios.find((s) => s.id === activeScenarioId);

  // Fetch allocations for active scenario
  const { data: draftAllocations = [] } = useQuery<ScenarioAllocation[]>({
    queryKey: ['scenario-allocations', activeScenarioId],
    queryFn: () =>
      activeScenarioId
        ? apiClient.get<ScenarioAllocation[]>(`/api/v1/scenarios/${activeScenarioId}/allocations`)
        : Promise.resolve([]),
    enabled: !!activeScenarioId,
  });

  // Fetch members for dropdown
  const { data: members = [] } = useQuery<MemberOption[]>({
    queryKey: ['members-list'],
    queryFn: async () => {
      const data = await apiClient.get<Array<{ id: string; fullName: string; role: string }>>('/api/v1/users');
      return data.map((m) => ({
        id: m.id,
        name: m.fullName,
        role: m.role,
      }));
    },
  });

  // Fetch simulation result for active scenario
  const { data: simulation, isLoading: isSimulating, refetch: runSimulation } = useQuery<SimulationResult>({
    queryKey: ['scenario-simulation', activeScenarioId],
    queryFn: () =>
      activeScenarioId
        ? apiClient.post<SimulationResult>(`/api/v1/scenarios/${activeScenarioId}/simulate`, {})
        : Promise.resolve(null as any),
    enabled: !!activeScenarioId,
  });

  // Create scenario mutation
  const createScenarioMutation = useMutation({
    mutationFn: () =>
      apiClient.post<Scenario>('/api/v1/scenarios', {
        name: newScenarioName,
        description: newScenarioDesc,
      }),
    onSuccess: (data: Scenario) => {
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      setSelectedScenarioId(data.id);
      setIsCreateOpen(false);
      setNewScenarioName('');
      setNewScenarioDesc('');
      setFeedbackMsg({ text: 'Skenario simulasi berhasil dibuat.', type: 'success' });
      setTimeout(() => setFeedbackMsg(null), 4000);
    },
    onError: (err: Error) => {
      setFeedbackMsg({ text: err.message, type: 'error' });
      setTimeout(() => setFeedbackMsg(null), 4000);
    },
  });

  // Add allocation mutation
  const addAllocMutation = useMutation({
    mutationFn: () => {
      if (!activeScenarioId) return Promise.reject(new Error('Pilih skenario terlebih dahulu'));
      return apiClient.post<ScenarioAllocation>(`/api/v1/scenarios/${activeScenarioId}/allocations`, {
        userId: allocUserId,
        projectName: allocProjectName,
        startDate: allocStartDate,
        endDate: allocEndDate,
        hoursPerDay: parseFloat(allocHoursPerDay.replace(',', '.')),
        notes: allocNotes,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scenario-allocations', activeScenarioId] });
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      queryClient.invalidateQueries({ queryKey: ['scenario-simulation', activeScenarioId] });
      setIsAddAllocOpen(false);
      setAllocProjectName('');
      setAllocNotes('');
      setFeedbackMsg({ text: 'Alokasi percobaan berhasil ditambahkan.', type: 'success' });
      setTimeout(() => setFeedbackMsg(null), 4000);
    },
    onError: (err: Error) => {
      setFeedbackMsg({ text: err.message, type: 'error' });
      setTimeout(() => setFeedbackMsg(null), 4000);
    },
  });

  // Delete allocation mutation
  const deleteAllocMutation = useMutation({
    mutationFn: (allocId: string) => {
      if (!activeScenarioId) return Promise.reject(new Error('Pilih skenario terlebih dahulu'));
      return apiClient.delete(`/api/v1/scenarios/${activeScenarioId}/allocations/${allocId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scenario-allocations', activeScenarioId] });
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      queryClient.invalidateQueries({ queryKey: ['scenario-simulation', activeScenarioId] });
    },
  });

  // Promote scenario mutation
  const promoteMutation = useMutation({
    mutationFn: () => {
      if (!activeScenarioId) return Promise.reject(new Error('Pilih skenario terlebih dahulu'));
      return apiClient.post<{ message?: string; promotedAllocationsCount?: number }>(
        `/api/v1/scenarios/${activeScenarioId}/promote`,
        {}
      );
    },
    onSuccess: (data: { message?: string; promotedAllocationsCount?: number }) => {
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      queryClient.invalidateQueries({ queryKey: ['scenario-allocations', activeScenarioId] });
      setFeedbackMsg({
        text: `Berhasil! ${data.promotedAllocationsCount} alokasi telah dipromosikan ke jadwal produksi riil.`,
        type: 'success',
      });
      setTimeout(() => setFeedbackMsg(null), 6000);
    },
    onError: (err: Error) => {
      setFeedbackMsg({ text: err.message, type: 'error' });
      setTimeout(() => setFeedbackMsg(null), 5000);
    },
  });

  // Delete scenario mutation
  const deleteScenarioMutation = useMutation({
    mutationFn: (scId: string) => apiClient.delete(`/api/v1/scenarios/${scId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scenarios'] });
      setSelectedScenarioId(null);
      setFeedbackMsg({
        text: t('forecasting.scenarioDeleted', 'Skenario simulasi berhasil dihapus.'),
        type: 'success',
      });
      setTimeout(() => setFeedbackMsg(null), 4000);
    },
    onError: (err: Error) => {
      setFeedbackMsg({ text: err.message, type: 'error' });
      setTimeout(() => setFeedbackMsg(null), 4000);
    },
  });

  const handleDeleteScenario = (id: string, name: string) => {
    const confirmText = t(
      'forecasting.confirmDeleteScenario',
      `Apakah Anda yakin ingin menghapus skenario "${name}" beserta seluruh alokasi percobaannya?`,
      { name }
    );
    if (confirm(confirmText)) {
      deleteScenarioMutation.mutate(id);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PROMOTED':
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800">Dipromosikan</span>;
      case 'SIMULATED':
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-indigo-100 text-indigo-800">Tersimulasi</span>;
      default:
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700">Draft</span>;
    }
  };

  return (
    <div className="p-6 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            {t('forecasting.title', 'Simulasi Forecasting Kapasitas (What-If)')}
          </h1>
          <p className="text-sm text-slate-500">
            {t('forecasting.subtitle', 'Rencanakan proyek dan alokasi tambahan secara hipotetis tanpa mengganggu jadwal kerja riil tim.')}
          </p>
        </div>

        <button
          onClick={() => setIsCreateOpen(true)}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-medium rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 transition-colors shadow-xs"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          {t('forecasting.newScenario', 'Buat Skenario Simulasi')}
        </button>
      </div>

      {feedbackMsg && (
        <div
          className={`p-3 text-sm rounded-lg border ${feedbackMsg.type === 'success'
            ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
            : 'bg-rose-50 border-rose-200 text-rose-900'
            }`}
        >
          {feedbackMsg.text}
        </div>
      )}

      {/* Skenario Selection Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2 border-b border-slate-200">
        {scenarios.map((sc) => (
          <button
            key={sc.id}
            onClick={() => setSelectedScenarioId(sc.id)}
            className={`px-3.5 py-2 text-xs font-medium rounded-lg transition-all flex items-center gap-2 whitespace-nowrap ${sc.id === activeScenarioId
              ? 'bg-indigo-50 text-indigo-700 border border-indigo-200 shadow-xs'
              : 'text-slate-600 hover:bg-slate-100'
              }`}
          >
            <span>{sc.name}</span>
            <span className="text-2xs opacity-75">{t('forecasting.allocationsCount', { count: sc.allocationsCount, defaultValue: `(${sc.allocationsCount} alokasi)` })}</span>
            {getStatusBadge(sc.status)}
          </button>
        ))}
        {scenarios.length === 0 && !isLoadingScenarios && (
          <div className="text-xs text-slate-400 py-2">{t('forecasting.emptyScenarios', 'Belum ada skenario simulasi. Klik tombol di atas untuk membuat.')}</div>
        )}
      </div>

      {/* Workspace Area for Active Scenario */}
      {currentScenario && (
        <div className="space-y-6">
          {/* Scenario Info & Promotion Banner */}
          <div className="p-5 rounded-xl border border-slate-200 bg-white shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-bold text-slate-900">{currentScenario.name}</h2>
                {getStatusBadge(currentScenario.status)}
              </div>
              <p className="text-xs text-slate-500 mt-1">{currentScenario.description || t('common.noDescription', 'Tidak ada deskripsi')}</p>
              <div className="text-2xs text-slate-400 mt-2">
                {t('common.createdBy', 'Dibuat oleh')} <span className="font-medium text-slate-600">{currentScenario.createdByName}</span>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => setIsAddAllocOpen(true)}
                disabled={currentScenario.status === 'PROMOTED'}
                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 disabled:opacity-40 transition-colors shadow-xs"
              >
                <svg className="w-4 h-4 text-indigo-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                + {t('forecasting.addDraftAllocation', 'Tambah Alokasi Percobaan')}
              </button>

              <button
                onClick={() => runSimulation()}
                disabled={isSimulating}
                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg bg-indigo-50 text-indigo-700 border border-indigo-200 hover:bg-indigo-100 transition-colors shadow-xs"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                </svg>
                {isSimulating ? t('forecasting.simulating', 'Menghitung...') : t('forecasting.simulate', 'Hitung Simulasi')}
              </button>

              <button
                onClick={() => {
                  if (confirm(t('forecasting.confirmPromote', 'Yakin ingin mempromosikan skenario ini? Seluruh alokasi percobaan akan dijadikan jadwal alokasi riil di modul produksi.'))) {
                    promoteMutation.mutate();
                  }
                }}
                disabled={currentScenario.status === 'PROMOTED' || draftAllocations.length === 0 || promoteMutation.isPending}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-40 transition-colors shadow-xs"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
                {currentScenario.status === 'PROMOTED' ? t('forecasting.promoted', 'Sudah Dipromosikan') : t('forecasting.promote', 'Promosikan ke Produksi')}
              </button>

              <button
                onClick={() => handleDeleteScenario(currentScenario.id, currentScenario.name)}
                disabled={deleteScenarioMutation.isPending}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-rose-200 bg-white text-rose-700 hover:bg-rose-50 hover:border-rose-300 disabled:opacity-40 transition-colors shadow-xs"
                title={t('forecasting.deleteScenarioTooltip', 'Hapus skenario simulasi ini')}
              >
                <svg className="w-4 h-4 text-rose-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
                {deleteScenarioMutation.isPending ? t('common.deleting', 'Menghapus...') : t('forecasting.deleteScenario', 'Hapus Skenario')}
              </button>
            </div>
          </div>

          {/* KPI Comparison Cards (Baseline vs Simulated) */}
          {simulation && (
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
                <p className="text-xs font-medium text-slate-500">Utilisasi Tim (Baseline)</p>
                <p className="mt-2 text-2xl font-bold text-slate-800">
                  {simulation.baselineOverallUtilPct}%
                </p>
                <span className="text-2xs text-slate-400">Jadwal riil saat ini</span>
              </div>

              <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
                <p className="text-xs font-medium text-slate-500">{t('forecasting.simulatedUtilization', 'Utilisasi Tim (Hasil Simulasi)')}</p>
                <div className="mt-2 flex items-baseline gap-2">
                  <p className="text-2xl font-bold text-indigo-600">
                    {simulation.simulatedOverallUtilPct}%
                  </p>
                  <span className="text-xs font-semibold text-indigo-500">
                    (+{(simulation.simulatedOverallUtilPct - simulation.baselineOverallUtilPct).toFixed(1)}%)
                  </span>
                </div>
                <span className="text-2xs text-slate-400">{t('forecasting.scenarioIncluded', 'Termasuk alokasi skenario')}</span>
              </div>

              <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
                <p className="text-xs font-medium text-slate-500">{t('forecasting.baselineOverload', 'Overload (Baseline)')}</p>
                <p className="mt-2 text-2xl font-bold text-slate-700">
                  {simulation.baselineOverloadedCount} <span className="text-xs font-normal text-slate-400">{t('common.members', 'anggota')}</span>
                </p>
              </div>

              <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
                <p className="text-xs font-medium text-slate-500">{t('forecasting.simulatedOverload', 'Overload (Hasil Simulasi)')}</p>
                <div className="mt-2 flex items-baseline gap-2">
                  <p className="text-2xl font-bold text-rose-600">
                    {simulation.simulatedOverloadedCount} <span className="text-xs font-normal text-slate-400">{t('common.members', 'anggota')}</span>
                  </p>
                  {simulation.simulatedOverloadedCount > simulation.baselineOverloadedCount && (
                    <span className="px-1.5 py-0.5 rounded text-2xs font-semibold bg-rose-100 text-rose-800">
                      {t('forecasting.newOverloaded', { count: simulation.simulatedOverloadedCount - simulation.baselineOverloadedCount, defaultValue: `+${simulation.simulatedOverloadedCount - simulation.baselineOverloadedCount} baru` })}
                    </span>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Section: Draft Allocations Table */}
          <div className="rounded-xl border border-slate-200 bg-white shadow-xs overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-900">
                {t('forecasting.draftAllocationsTitle', { count: draftAllocations.length, defaultValue: `Daftar Alokasi Percobaan (${draftAllocations.length})` })}
              </h3>
            </div>

            {draftAllocations.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-400">
                {t('forecasting.emptyDraftAllocations', 'Belum ada alokasi percobaan pada skenario ini. Klik "+ Tambah Alokasi Percobaan" untuk mulai merancang.')}
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-600 border-b border-slate-200 font-semibold">
                    <tr>
                      <th className="px-4 py-3">{t('forecasting.memberName', 'Nama Anggota')}</th>
                      <th className="px-4 py-3">{t('forecasting.hypotheticalProject', 'Proyek Hipotetis')}</th>
                      <th className="px-4 py-3">{t('forecasting.startDate', 'Tanggal Mulai')}</th>
                      <th className="px-4 py-3">{t('forecasting.endDate', 'Tanggal Selesai')}</th>
                      <th className="px-4 py-3 text-right">{t('forecasting.hoursPerDayHeader', 'Jam / Hari')}</th>
                      <th className="px-4 py-3">{t('forecasting.notes', 'Catatan')}</th>
                      <th className="px-4 py-3 text-center">{t('forecasting.actions', 'Aksi')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200">
                    {draftAllocations.map((a) => (
                      <tr key={a.id} className="hover:bg-slate-50">
                        <td className="px-4 py-3 font-medium text-slate-900">{a.userName}</td>
                        <td className="px-4 py-3 text-indigo-600 font-medium">{a.projectName}</td>
                        <td className="px-4 py-3 font-mono">{a.startDate}</td>
                        <td className="px-4 py-3 font-mono">{a.endDate}</td>
                        <td className="px-4 py-3 text-right font-mono font-semibold">{a.hoursPerDay} {t('common.hoursUnit', 'jam')}</td>
                        <td className="px-4 py-3 text-slate-500">{a.notes || '-'}</td>
                        <td className="px-4 py-3 text-center">
                          <button
                            onClick={() => deleteAllocMutation.mutate(a.id)}
                            disabled={currentScenario.status === 'PROMOTED'}
                            className="text-rose-600 hover:text-rose-700 disabled:opacity-30"
                            title={t('forecasting.deleteTooltip', 'Hapus')}
                          >
                            {t('common.delete', 'Hapus')}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Section: Simulation Comparison Table */}
          {simulation && (
            <div className="rounded-xl border border-slate-200 bg-white shadow-xs overflow-hidden">
              <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-semibold text-slate-900">
                    {t('forecasting.comparisonTitle', 'Komparasi Proyeksi Beban Kerja per Anggota')}
                  </h3>
                  <p className="text-2xs text-slate-400 mt-0.5">
                    {t('forecasting.periodLabel', { start: simulation.startDate, end: simulation.endDate, defaultValue: `Periode: ${simulation.startDate} s/d ${simulation.endDate}` })}
                  </p>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-600 border-b border-slate-200 font-semibold">
                    <tr>
                      <th className="px-4 py-3">{t('forecasting.memberName', 'Nama Anggota')}</th>
                      <th className="px-4 py-3 text-right">{t('forecasting.netCapacity', 'Kapasitas Bersih')}</th>
                      <th className="px-4 py-3 text-right">{t('forecasting.baselineHours', 'Jam Baseline')}</th>
                      <th className="px-4 py-3 text-right">{t('forecasting.deltaHours', 'Jam Delta')}</th>
                      <th className="px-4 py-3 text-right">{t('forecasting.totalSimulatedHours', 'Total Jam Simulasi')}</th>
                      <th className="px-4 py-3">{t('forecasting.utilizationProgression', 'Utilisasi (Baseline → Simulasi)')}</th>
                      <th className="px-4 py-3 text-center">{t('forecasting.overloadImpact', 'Dampak Overload')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200">
                    {simulation.userDeltas.map((u) => (
                      <tr key={u.userId} className={u.isNewlyOverloaded ? 'bg-rose-50/50' : ''}>
                        <td className="px-4 py-3 font-medium text-slate-900">
                          {u.userName}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-slate-600">
                          {u.capacityHours} {t('common.hoursUnit', 'jam')}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-slate-600">
                          {u.baselineAllocatedHours} {t('common.hoursUnit', 'jam')}
                        </td>
                        <td className="px-4 py-3 text-right font-mono font-semibold text-indigo-600">
                          +{u.simulatedDeltaHours} {t('common.hoursUnit', 'jam')}
                        </td>
                        <td className="px-4 py-3 text-right font-mono font-bold text-slate-900">
                          {u.totalSimulatedHours} {t('common.hoursUnit', 'jam')}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-slate-500">{u.baselineUtilPct}%</span>
                            <span className="text-slate-400">→</span>
                            <span
                              className={`font-mono font-bold ${u.simulatedUtilPct > 100
                                ? 'text-rose-600'
                                : u.simulatedUtilPct < 70
                                  ? 'text-sky-600'
                                  : 'text-emerald-600'
                                }`}
                            >
                              {u.simulatedUtilPct}%
                            </span>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-center">
                          {u.isNewlyOverloaded ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-2xs font-bold bg-rose-100 text-rose-800 animate-pulse">
                              🚨 Baru Overload
                            </span>
                          ) : u.simulatedStatus === 'OVERLOADED' ? (
                            <span className="text-2xs text-rose-600 font-medium">Tetap Overload</span>
                          ) : (
                            <span className="text-2xs text-emerald-600 font-medium">Aman</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Modal: Buat Skenario Baru */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-bold text-slate-900">{t('forecasting.newScenario', 'Buat Skenario Simulasi Baru')}</h3>
            <div className="space-y-3 text-xs">
              <div>
                <label className="block font-medium text-slate-700 mb-1">{t('forecasting.hypotheticalProjectName', 'Nama Skenario *')}</label>
                <input
                  type="text"
                  placeholder={t('forecasting.scenarioNamePlaceholder', 'Contoh: Ekspansi Proyek Retail Q1')}
                  value={newScenarioName}
                  onChange={(e) => setNewScenarioName(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900"
                />
              </div>
              <div>
                <label className="block font-medium text-slate-700 mb-1">Deskripsi & Tujuan</label>
                <textarea
                  rows={3}
                  placeholder="Catatan latar belakang skenario..."
                  value={newScenarioDesc}
                  onChange={(e) => setNewScenarioDesc(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setIsCreateOpen(false)}
                className="px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-300 hover:bg-slate-100 text-slate-700"
              >
                Batal
              </button>
              <button
                onClick={() => createScenarioMutation.mutate()}
                disabled={!newScenarioName.trim() || createScenarioMutation.isPending}
                className="px-3 py-1.5 text-xs font-medium rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                {createScenarioMutation.isPending ? 'Menyimpan...' : 'Buat Skenario'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Tambah Alokasi Percobaan */}
      {isAddAllocOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-bold text-slate-900">{t('forecasting.addDraftAllocation', 'Tambah Alokasi Percobaan')}</h3>
            <div className="space-y-3 text-xs">
              <div>
                <label className="block font-medium text-slate-700 mb-1">{t('forecasting.selectMember', 'Pilih Anggota Tim *')}</label>
                <select
                  value={allocUserId}
                  onChange={(e) => setAllocUserId(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900"
                >
                  <option value="">{t('forecasting.chooseMember', '-- Pilih Anggota --')}</option>
                  {members.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} ({m.role})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-medium text-slate-700 mb-1">{t('forecasting.hypotheticalProjectName', 'Nama Proyek Hipotetis *')}</label>
                <input
                  type="text"
                  placeholder="Contoh: Migration to Cloud / New App"
                  value={allocProjectName}
                  onChange={(e) => setAllocProjectName(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block font-medium text-slate-700 mb-1">{t('forecasting.startDate', 'Tanggal Mulai *')}</label>
                  <input
                    type="date"
                    value={allocStartDate}
                    onChange={(e) => setAllocStartDate(e.target.value)}
                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg bg-white text-slate-900"
                  />
                </div>
                <div>
                  <label className="block font-medium text-slate-700 mb-1">{t('forecasting.endDate', 'Tanggal Selesai *')}</label>
                  <input
                    type="date"
                    value={allocEndDate}
                    onChange={(e) => setAllocEndDate(e.target.value)}
                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg bg-white text-slate-900"
                  />
                </div>
              </div>

              <div>
                <label className="block font-medium text-slate-700 mb-1">{t('forecasting.hoursPerDayField', 'Jam Alokasi / Hari (0.1-24) *')}</label>
                <input
                  type="number"
                  step="any"
                  min="0.1"
                  max="24"
                  value={allocHoursPerDay}
                  onChange={(e) => setAllocHoursPerDay(e.target.value.replace(',', '.'))}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900 font-mono"
                />
              </div>

              <div>
                <label className="block font-medium text-slate-700 mb-1">Catatan Tambahan</label>
                <input
                  type="text"
                  placeholder="Catatan opsional..."
                  value={allocNotes}
                  onChange={(e) => setAllocNotes(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setIsAddAllocOpen(false)}
                className="px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-300 hover:bg-slate-100 text-slate-700"
              >
                Batal
              </button>
              <button
                onClick={() => addAllocMutation.mutate()}
                disabled={!allocUserId || !allocProjectName.trim() || !allocStartDate || !allocEndDate || addAllocMutation.isPending}
                className="px-3 py-1.5 text-xs font-medium rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                {addAllocMutation.isPending ? 'Menyimpan...' : 'Simpan Alokasi'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ForecastingPage;
