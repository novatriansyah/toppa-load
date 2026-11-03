import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';

interface MemberUtilization {
  userId: string;
  name: string;
  email: string;
  role: string;
  weeklyCapacityHours: number;
  netCapacityHours: number;
  allocatedHours: number;
  timesheetHours: number;
  utilizationPct: number;
  overloadWeekCount: number;
  status: 'OVERLOADED' | 'OPTIMAL' | 'UNDERUTILIZED';
}

interface UtilizationSummary {
  startDate: string;
  endDate: string;
  overallUtilizationPct: number;
  totalCapacityHours: number;
  totalAllocatedHours: number;
  totalActualHours: number;
  overloadedMembersCount: number;
  members: MemberUtilization[];
}

export const ReportsPage: React.FC = () => {
  const { t } = useTranslation();

  // Default range: 4 weeks ago to today + 2 weeks
  const today = new Date();
  const defaultStart = new Date(today);
  defaultStart.setDate(today.getDate() - 28);
  const defaultEnd = new Date(today);
  defaultEnd.setDate(today.getDate() + 14);

  const [startDate, setStartDate] = useState(defaultStart.toISOString().slice(0, 10));
  const [endDate, setEndDate] = useState(defaultEnd.toISOString().slice(0, 10));
  const [feedbackMsg, setFeedbackMsg] = useState<string | null>(null);

  const { data: summary, isLoading, refetch } = useQuery<UtilizationSummary>({
    queryKey: ['utilization-report', startDate, endDate],
    queryFn: () =>
      apiClient.get<UtilizationSummary>('/api/v1/reports/utilization', {
        params: { startDate, endDate },
      }),
  });

  const sendEmailMutation = useMutation({
    mutationFn: () =>
      apiClient.post<{ message?: string }>('/api/v1/reports/send-summary', {}),
    onSuccess: (data: { message?: string }) => {
      setFeedbackMsg(data.message || 'Email rekap berhasil dikirim atau dijadwalkan!');
      setTimeout(() => setFeedbackMsg(null), 5000);
    },
    onError: (err: Error) => {
      setFeedbackMsg(err.message);
      setTimeout(() => setFeedbackMsg(null), 5000);
    },
  });

  const handleExport = (format: 'csv' | 'xlsx') => {
    window.open(`/api/v1/reports/export?format=${format}&startDate=${startDate}&endDate=${endDate}`, '_blank');
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'OVERLOADED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-rose-100 text-rose-800">
            {t('reports.status.overloaded', 'Overload')}
          </span>
        );
      case 'UNDERUTILIZED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-sky-100 text-sky-800">
            {t('reports.status.underutilized', 'Kurang Beban')}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800">
            {t('reports.status.optimal', 'Optimal')}
          </span>
        );
    }
  };

  return (
    <div className="p-6 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            {t('reports.title', 'Laporan Utilisasi & Kapasitas')}
          </h1>
          <p className="text-sm text-slate-500">
            {t('reports.subtitle', 'Analisis pemanfaatan jam kerja tim, komparasi alokasi vs aktual, dan ekspor multi-format.')}
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => handleExport('csv')}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 transition-colors shadow-xs"
          >
            <svg className="w-4 h-4 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            {t('reports.exportCsv', 'Ekspor CSV')}
          </button>

          <button
            onClick={() => handleExport('xlsx')}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 transition-colors shadow-xs"
          >
            <svg className="w-4 h-4 text-emerald-600" fill="currentColor" viewBox="0 0 24 24">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm1.8 14-1.8-3-1.8 3H10l2.7-4.2L10.2 8h2.1l1.7 3 1.7-3h2.1l-2.6 3.8 2.8 4.2h-2.2zM13 9V3.5L18.5 9H13z" />
            </svg>
            {t('reports.exportExcel', 'Ekspor Excel (.xlsx)')}
          </button>

          <button
            onClick={() => sendEmailMutation.mutate()}
            disabled={sendEmailMutation.isPending}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 transition-colors shadow-xs"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
            {sendEmailMutation.isPending ? t('common.loading', 'Mengirim...') : t('reports.sendEmail', 'Kirim Rekap Email')}
          </button>
        </div>
      </div>

      {feedbackMsg && (
        <div className="p-3 text-sm rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-900">
          {feedbackMsg}
        </div>
      )}

      {/* Date Filter Bar */}
      <div className="flex flex-wrap items-center gap-3 p-4 bg-white border border-slate-200 rounded-xl shadow-xs">
        <label className="text-xs font-semibold text-slate-600">
          {t('reports.dateRange', 'Rentang Tanggal')}:
        </label>
        <input
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          className="px-2.5 py-1 text-xs border border-slate-300 rounded-md bg-white text-slate-800"
        />
        <span className="text-xs text-slate-400">s/d</span>
        <input
          type="date"
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
          className="px-2.5 py-1 text-xs border border-slate-300 rounded-md bg-white text-slate-800"
        />
        <button
          onClick={() => refetch()}
          className="px-3 py-1 text-xs font-medium rounded-md bg-slate-100 text-slate-700 hover:bg-slate-200 transition-colors"
        >
          {t('common.filter', 'Terapkan')}
        </button>
      </div>

      {/* KPI Summary Cards */}
      {summary && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
            <p className="text-xs font-medium text-slate-500">{t('reports.kpi.teamUtilization', 'Rata-rata Utilisasi Tim')}</p>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-2xl font-bold text-slate-900">
                {summary.overallUtilizationPct}%
              </span>
            </div>
            <div className="w-full bg-slate-100 h-1.5 rounded-full mt-3 overflow-hidden">
              <div
                className={`h-full rounded-full ${summary.overallUtilizationPct > 100
                    ? 'bg-rose-500'
                    : summary.overallUtilizationPct < 70
                      ? 'bg-sky-500'
                      : 'bg-emerald-500'
                  }`}
                style={{ width: `${Math.min(summary.overallUtilizationPct, 100)}%` }}
              />
            </div>
          </div>

          <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
            <p className="text-xs font-medium text-slate-500">{t('reports.kpi.totalCapacity', 'Kapasitas Bersih')}</p>
            <p className="mt-2 text-2xl font-bold text-slate-900">
              {summary.totalCapacityHours} <span className="text-xs font-normal text-slate-500">{t('common.hoursUnit', 'jam')}</span>
            </p>
          </div>

          <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
            <p className="text-xs font-medium text-slate-500">{t('reports.kpi.totalAllocated', 'Total Alokasi')}</p>
            <p className="mt-2 text-2xl font-bold text-indigo-600">
              {summary.totalAllocatedHours} <span className="text-xs font-normal text-slate-500">{t('common.hoursUnit', 'jam')}</span>
            </p>
          </div>

          <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
            <p className="text-xs font-medium text-slate-500">{t('reports.kpi.totalActual', 'Total Timesheet')}</p>
            <p className="mt-2 text-2xl font-bold text-emerald-600">
              {summary.totalActualHours} <span className="text-xs font-normal text-slate-500">{t('common.hoursUnit', 'jam')}</span>
            </p>
          </div>

          <div className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs">
            <p className="text-xs font-medium text-slate-500">{t('reports.kpi.overloadedMembers', 'Anggota Overload')}</p>
            <p className="mt-2 text-2xl font-bold text-rose-600">
              {summary.overloadedMembersCount} <span className="text-xs font-normal text-slate-500">{t('common.peopleUnit', 'orang')}</span>
            </p>
          </div>
        </div>
      )}

      {/* Utilization Breakdown Table */}
      <div className="rounded-xl border border-slate-200 bg-white shadow-xs overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
          <h2 className="text-base font-semibold text-slate-900">
            {t('reports.table.title', 'Rincian Beban Kerja per Anggota')}
          </h2>
          <span className="text-xs text-slate-500">
            {summary?.members.length ?? 0} {t('reports.table.totalMembers', 'anggota terdaftar')}
          </span>
        </div>

        {isLoading ? (
          <div className="p-8 text-center text-sm text-slate-500">
            {t('common.loading', 'Memuat data laporan...')}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 border-b border-slate-200 font-semibold">
                <tr>
                  <th className="px-4 py-3">{t('reports.table.member', 'Anggota')}</th>
                  <th className="px-4 py-3">{t('reports.table.role', 'Role')}</th>
                  <th className="px-4 py-3 text-right">{t('reports.table.capacity', 'Kapasitas (Jam)')}</th>
                  <th className="px-4 py-3 text-right">{t('reports.table.allocated', 'Alokasi (Jam)')}</th>
                  <th className="px-4 py-3 text-right">{t('reports.table.timesheet', 'Timesheet (Jam)')}</th>
                  <th className="px-4 py-3">{t('reports.table.utilization', 'Utilisasi')}</th>
                  <th className="px-4 py-3 text-center">{t('reports.table.status', 'Status')}</th>
                  <th className="px-4 py-3 text-center">{t('reports.table.overloadWeeks', 'Minggu Overload')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {summary?.members.map((m) => (
                  <tr key={m.userId} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-900">{m.name}</div>
                      <div className="text-slate-400 text-2xs">{m.email}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-600 font-mono text-2xs">
                      {m.role}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-700 font-mono">
                      {m.netCapacityHours}
                    </td>
                    <td className="px-4 py-3 text-right font-mono font-medium text-indigo-600">
                      {m.allocatedHours}
                    </td>
                    <td className="px-4 py-3 text-right font-mono font-medium text-emerald-600">
                      {m.timesheetHours}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className="w-10 font-mono font-semibold text-slate-800">
                          {m.utilizationPct}%
                        </span>
                        <div className="w-24 bg-slate-100 h-2 rounded-full overflow-hidden">
                          <div
                            className={`h-full rounded-full ${m.utilizationPct > 100
                                ? 'bg-rose-500'
                                : m.utilizationPct < 70
                                  ? 'bg-sky-500'
                                  : 'bg-emerald-500'
                              }`}
                            style={{ width: `${Math.min(m.utilizationPct, 100)}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-center">
                      {getStatusBadge(m.status)}
                    </td>
                    <td className="px-4 py-3 text-center font-mono">
                      {m.overloadWeekCount > 0 ? (
                        <span className="px-2 py-0.5 rounded text-xs font-semibold bg-rose-50 text-rose-700">
                          {m.overloadWeekCount}
                        </span>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
export default ReportsPage;

