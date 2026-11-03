import React, { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/httpClient';
import { useCurrentUser } from '../auth/useCurrentUser';
import {
  WeeklyTimesheetData,
  TimesheetSubmission,
  TimeEntryInput,
} from './TimesheetTypes';
import { TimesheetWeeklyGrid } from './TimesheetWeeklyGrid';
import { UserSummary } from '../settings/UsersSettingsSection';

function getMondayOfDate(d: Date): string {
  const date = new Date(d);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  date.setDate(date.getDate() + diff);
  return date.toISOString().split('T')[0];
}

export const TimesheetPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();
  const isSupervisorOrAdmin = currentUser?.role === 'ADMIN' || currentUser?.role === 'SUPERVISOR';

  // State: selected week Monday
  const [selectedMonday, setSelectedMonday] = useState<string>(() => getMondayOfDate(new Date()));

  // State: selected user (defaults to current user)
  const [selectedUserId, setSelectedUserId] = useState<string>('');
  const activeUserId = selectedUserId || currentUser?.userId || '';

  // Mode: 'my-timesheet' or 'pending-approvals'
  const [activeTab, setActiveTab] = useState<'timesheet' | 'pending'>('timesheet');

  // Format week range label
  const weekLabel = useMemo(() => {
    const start = new Date(selectedMonday);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    return `${start.toLocaleDateString('id-ID', {
      day: 'numeric',
      month: 'short',
    })} – ${end.toLocaleDateString('id-ID', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    })}`;
  }, [selectedMonday]);

  const shiftWeek = (direction: -1 | 1) => {
    const current = new Date(selectedMonday);
    current.setDate(current.getDate() + direction * 7);
    setSelectedMonday(current.toISOString().split('T')[0]);
  };

  const jumpToCurrentWeek = () => {
    setSelectedMonday(getMondayOfDate(new Date()));
  };

  // Fetch users for supervisor selector
  const { data: users = [] } = useQuery<UserSummary[]>({
    queryKey: ['users'],
    queryFn: () => apiClient.get<UserSummary[]>('/api/v1/users'),
    enabled: isSupervisorOrAdmin,
  });

  // Fetch pending approvals for supervisor
  const { data: pendingApprovals = [] } = useQuery<TimesheetSubmission[]>({
    queryKey: ['timesheets', 'pending-approvals'],
    queryFn: () => apiClient.get<TimesheetSubmission[]>('/api/v1/timesheets/pending-approvals'),
    enabled: isSupervisorOrAdmin,
    refetchInterval: 30000,
  });

  // Fetch weekly timesheet data
  const { data: timesheetData, isLoading, error } = useQuery<WeeklyTimesheetData>({
    queryKey: ['timesheet', activeUserId, selectedMonday],
    queryFn: () =>
      apiClient.get<WeeklyTimesheetData>('/api/v1/timesheets/weekly', {
        params: {
          weekStartDate: selectedMonday,
          userId: activeUserId !== currentUser?.userId ? activeUserId : undefined,
        },
      }),
    enabled: !!activeUserId,
  });

  // Save draft mutation
  const saveDraftMutation = useMutation({
    mutationFn: (entries: TimeEntryInput[]) =>
      apiClient.put<WeeklyTimesheetData>('/api/v1/timesheets/weekly', {
        weekStartDate: selectedMonday,
        entries,
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(['timesheet', activeUserId, selectedMonday], data);
    },
  });

  // Submit timesheet mutation
  const submitMutation = useMutation({
    mutationFn: () =>
      apiClient.post<WeeklyTimesheetData>('/api/v1/timesheets/weekly/submit', {
        weekStartDate: selectedMonday,
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(['timesheet', activeUserId, selectedMonday], data);
      queryClient.invalidateQueries({ queryKey: ['timesheets', 'pending-approvals'] });
    },
  });

  // Approve mutation
  const approveMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/timesheets/${id}/approve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timesheet', activeUserId, selectedMonday] });
      queryClient.invalidateQueries({ queryKey: ['timesheets', 'pending-approvals'] });
    },
  });

  // Return mutation
  const returnMutation = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string }) =>
      apiClient.post(`/api/v1/timesheets/${id}/return`, { reviewerNote: note }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timesheet', activeUserId, selectedMonday] });
      queryClient.invalidateQueries({ queryKey: ['timesheets', 'pending-approvals'] });
    },
  });

  const isViewingSelf = !selectedUserId || selectedUserId === currentUser?.userId;

  return (
    <div className="space-y-4 max-w-6xl mx-auto">
      {/* Top Header */}
      <div className="pb-3 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-base font-semibold text-slate-900">Timesheet Mingguan</h1>
          <p className="text-xs text-slate-500">
            Pencatatan jam kerja aktual per proyek & perbandingan terhadap estimasi dan alokasi.
          </p>
        </div>

        {/* Tab switch for supervisor */}
        {isSupervisorOrAdmin && (
          <div className="flex rounded bg-slate-100 p-0.5 border border-slate-200 text-xs">
            <button
              type="button"
              onClick={() => setActiveTab('timesheet')}
              className={`px-3 py-1 rounded font-medium transition-colors cursor-pointer ${
                activeTab === 'timesheet'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Pengisian & Tinjauan
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('pending')}
              className={`px-3 py-1 rounded font-medium transition-colors cursor-pointer flex items-center space-x-1.5 ${
                activeTab === 'pending'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <span>Antrean Persetujuan</span>
              {pendingApprovals.length > 0 && (
                <span className="px-1.5 py-0.2 rounded-full bg-blue-600 text-white text-[10px] font-bold">
                  {pendingApprovals.length}
                </span>
              )}
            </button>
          </div>
        )}
      </div>

      {activeTab === 'pending' ? (
        /* Pending Approvals List */
        <div className="bg-white border border-slate-200 rounded-lg p-4 space-y-3 shadow-xs">
          <h2 className="text-sm font-semibold text-slate-900">
            Daftar Timesheet Menunggu Persetujuan
          </h2>
          {pendingApprovals.length === 0 ? (
            <div className="py-8 text-center text-slate-400 text-xs italic">
              Tidak ada timesheet yang sedang menunggu persetujuan saat ini.
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {pendingApprovals.map((sub) => (
                <div
                  key={sub.id}
                  className="py-3 flex items-center justify-between hover:bg-slate-50 transition-colors px-2 rounded"
                >
                  <div>
                    <div className="font-semibold text-xs text-slate-900">{sub.userFullName}</div>
                    <div className="text-[11px] text-slate-500 font-mono">
                      Minggu mulai: {sub.weekStartDate} · Diajukan pada:{' '}
                      {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString('id-ID') : '-'}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedUserId(sub.userId);
                      setSelectedMonday(sub.weekStartDate);
                      setActiveTab('timesheet');
                    }}
                    className="px-3 py-1.5 rounded bg-blue-600 text-white text-xs font-medium hover:bg-blue-700 transition-colors cursor-pointer"
                  >
                    Buka untuk Ditinjau →
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        /* Weekly Grid Mode */
        <div className="space-y-4">
          {/* Controls Bar: Week Navigator & Member Switcher */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-3 border border-slate-200 rounded-lg shadow-xs">
            {/* Week Picker */}
            <div className="flex items-center space-x-1.5 text-xs">
              <button
                type="button"
                onClick={() => shiftWeek(-1)}
                className="px-2 py-1 border border-slate-300 rounded hover:bg-slate-50 text-slate-700 font-mono"
                title="Minggu Sebelumnya"
              >
                ‹
              </button>
              <button
                type="button"
                onClick={jumpToCurrentWeek}
                className="px-2.5 py-1 border border-slate-300 rounded hover:bg-slate-50 text-slate-700 font-medium"
              >
                Minggu Ini
              </button>
              <button
                type="button"
                onClick={() => shiftWeek(1)}
                className="px-2 py-1 border border-slate-300 rounded hover:bg-slate-50 text-slate-700 font-mono"
                title="Minggu Berikutnya"
              >
                ›
              </button>
              <span className="font-semibold text-slate-800 ml-2 font-mono">{weekLabel}</span>
            </div>

            {/* Supervisor Member Selector */}
            {isSupervisorOrAdmin && (
              <div className="flex items-center space-x-2 text-xs">
                <span className="text-slate-500">Lihat Pengguna:</span>
                <select
                  value={selectedUserId || currentUser?.userId || ''}
                  onChange={(e) => setSelectedUserId(e.target.value)}
                  className="border border-slate-300 rounded px-2.5 py-1 text-xs font-medium bg-white focus:outline-none focus:ring-1 focus:ring-slate-400"
                >
                  <option value={currentUser?.userId}>
                    {currentUser?.fullName} (Timesheet Saya)
                  </option>
                  {users
                    .filter((u) => u.id !== currentUser?.userId)
                    .map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.fullName} ({u.role})
                      </option>
                    ))}
                </select>
              </div>
            )}
          </div>

          {/* Timesheet Content */}
          {isLoading ? (
            <div className="py-12 text-center text-slate-500 text-xs">Memuat data timesheet...</div>
          ) : error || !timesheetData ? (
            <div className="py-12 text-center text-rose-600 text-xs">
              Gagal memuat timesheet. Silakan coba kembali.
            </div>
          ) : (
            <TimesheetWeeklyGrid
              timesheetData={timesheetData}
              weekStartDate={selectedMonday}
              onSave={async (entries) => {
                await saveDraftMutation.mutateAsync(entries);
              }}
              onSubmitTimesheet={async () => {
                await submitMutation.mutateAsync();
              }}
              onApprove={
                isSupervisorOrAdmin && timesheetData.submission.status === 'SUBMITTED'
                  ? async () => {
                      await approveMutation.mutateAsync(timesheetData.submission.id);
                    }
                  : undefined
              }
              onReturn={
                isSupervisorOrAdmin && timesheetData.submission.status === 'SUBMITTED'
                  ? async (note) => {
                      await returnMutation.mutateAsync({
                        id: timesheetData.submission.id,
                        note,
                      });
                    }
                  : undefined
              }
              readOnly={!isViewingSelf}
              isSupervisorOrAdmin={isSupervisorOrAdmin}
            />
          )}
        </div>
      )}
    </div>
  );
};

export default TimesheetPage;

