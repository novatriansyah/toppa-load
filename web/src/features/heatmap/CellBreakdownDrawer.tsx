import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';
import {
  CapacityCellData,
  DayBreakdownData,
  AllocationSummaryData,
  LeaveSummaryData,
} from './HeatmapTypes';
import { useCurrentUser } from '../auth/useCurrentUser';

export interface CellBreakdownDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  userId: string;
  userName: string;
  userRole: string;
  cell: CapacityCellData | null;
  onAddAllocation: () => void;
  onEditAllocation: (allocation: AllocationSummaryData) => void;
}

const DAY_NAMES = ['Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu', 'Minggu'];

export const CellBreakdownDrawer: React.FC<CellBreakdownDrawerProps> = ({
  isOpen,
  onClose,
  userId,
  userName,
  userRole,
  cell,
  onAddAllocation,
  onEditAllocation,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();

  const isSupervisorOrAdmin = currentUser?.role === 'ADMIN' || currentUser?.role === 'SUPERVISOR';

  const { data: breakdown = [], isLoading } = useQuery<DayBreakdownData[]>({
    queryKey: ['capacity', 'breakdown', userId, cell?.bucketStart, cell?.bucketEnd],
    queryFn: () =>
      apiClient.get<DayBreakdownData[]>(`/api/v1/capacity/users/${userId}/breakdown`, {
        params: {
          from: cell?.bucketStart,
          to: cell?.bucketEnd,
        },
      }),
    enabled: isOpen && Boolean(cell && userId),
  });

  // Delete allocation mutation
  const deleteAllocationMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/allocations/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
      queryClient.invalidateQueries({ queryKey: ['allocations'] });
    },
  });

  if (!isOpen || !cell) return null;

  // Collect distinct active allocations and leaves from days
  const activeAllocationsMap = new Map<string, AllocationSummaryData>();
  const activeLeavesMap = new Map<string, LeaveSummaryData>();

  breakdown.forEach((day) => {
    day.allocations?.forEach((a) => activeAllocationsMap.set(a.id, a));
    day.leaves?.forEach((l) => activeLeavesMap.set(l.id, l));
  });

  const uniqueAllocations = Array.from(activeAllocationsMap.values());
  const uniqueLeaves = Array.from(activeLeavesMap.values());

  const canEditAllocation = (alloc: AllocationSummaryData) => {
    if (isSupervisorOrAdmin) return true;
    if (currentUser?.userId === alloc.userId && alloc.allocationType === 'TENTATIVE') return true;
    return false;
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-black/40 backdrop-blur-xs flex justify-end">
      <div className="w-full max-w-xl bg-white h-full shadow-2xl flex flex-col animate-in slide-in-from-right duration-200 border-l border-slate-200">
        {/* Header */}
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h2 className="text-sm font-bold text-slate-900">{userName}</h2>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-200 text-slate-700">
                {userRole}
              </span>
            </div>
            <p className="text-xs text-slate-500 font-mono">
              {cell.bucketStart} – {cell.bucketEnd}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded text-slate-400 hover:text-slate-700 hover:bg-slate-200 focus:outline-none"
            aria-label={t('common.close', 'Tutup')}
          >
            ✕
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-5 text-xs">
          {/* KPI Summary Cards */}
          <div className="grid grid-cols-4 gap-2">
            <div className="p-2.5 rounded border border-slate-200 bg-slate-50">
              <span className="text-[10px] text-slate-500 font-medium block">
                {t('capacity.netCapacity', 'Kapasitas')}
              </span>
              <span className="text-sm font-mono font-bold text-slate-900 tabular-nums">
                {cell.netCapacityHours}j
              </span>
            </div>

            <div className="p-2.5 rounded border border-slate-200 bg-slate-50">
              <span className="text-[10px] text-slate-500 font-medium block">
                {t('capacity.confirmedWorkload', 'Beban Pasti')}
              </span>
              <span className="text-sm font-mono font-bold text-slate-900 tabular-nums">
                {cell.confirmedHours}j
              </span>
            </div>

            <div className="p-2.5 rounded border border-slate-200 bg-slate-50">
              <span className="text-[10px] text-slate-500 font-medium block">
                {t('capacity.tentativeWorkload', 'Tentatif')}
              </span>
              <span className="text-sm font-mono font-bold text-purple-700 tabular-nums">
                {cell.tentativeHours}j
              </span>
            </div>

            <div
              className={`p-2.5 rounded border ${cell.status === 'OVERLOADED'
                  ? 'border-rose-300 bg-rose-50 text-rose-950'
                  : cell.status === 'OPTIMAL'
                    ? 'border-emerald-300 bg-emerald-50 text-emerald-950'
                    : 'border-slate-200 bg-slate-50 text-slate-900'
                }`}
            >
              <span className="text-[10px] font-medium block">
                {t('capacity.utilization', 'Utilisasi')}
              </span>
              <span className="text-sm font-mono font-bold tabular-nums">
                {cell.utilizationPct !== null ? `${cell.utilizationPct.toFixed(1)}%` : 'Konflik'}
              </span>
            </div>
          </div>

          {/* Conflict Warning Banner if present */}
          {cell.hasLeaveConflict && (
            <div className="p-3 rounded border border-rose-300 bg-rose-50 text-rose-900 text-xs flex items-center gap-2">
              <span className="text-base shrink-0">⚠️</span>
              <div>
                <strong>{t('capacity.leaveConflictDetected', 'Konflik Cuti Terdeteksi!')}</strong>{' '}
                {t(
                  'capacity.leaveConflictExplanation',
                  'Terdapat alokasi proyek aktif pada hari ketika anggota tim mengambil cuti.'
                )}
              </div>
            </div>
          )}

          {/* Daily Breakdown Table */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="font-bold text-slate-900 text-xs">
                {t('capacity.dailyBreakdown', 'Rincian Hari per Hari')}
              </h3>
              <span className="text-[10px] text-slate-500 font-mono">
                {breakdown.length} {t('common.days', 'hari')}
              </span>
            </div>

            {isLoading ? (
              <div className="text-slate-400 py-4 text-center">{t('common.loading', 'Memuat rincian...')}</div>
            ) : (
              <div className="border border-slate-200 rounded overflow-hidden">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-mono text-[10px]">
                    <tr>
                      <th className="p-1.5 pl-2">{t('capacity.date', 'Tanggal')}</th>
                      <th className="p-1.5">{t('capacity.day', 'Hari')}</th>
                      <th className="p-1.5 text-right">{t('capacity.net', 'Bersih')}</th>
                      <th className="p-1.5 text-right">{t('capacity.alloc', 'Alokasi')}</th>
                      <th className="p-1.5">{t('capacity.status', 'Keterangan')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-mono text-[11px]">
                    {breakdown.map((day) => {
                      const dayName = DAY_NAMES[(day.dayOfWeek - 1) % 7];
                      return (
                        <tr
                          key={day.date}
                          className={`${!day.isWorkday || day.isHoliday
                              ? 'bg-slate-50/70 text-slate-400'
                              : day.hasLeaveConflict
                                ? 'bg-rose-50/80 text-rose-950'
                                : 'hover:bg-slate-50'
                            }`}
                        >
                          <td className="p-1.5 pl-2 font-medium">{day.date}</td>
                          <td className="p-1.5">{dayName}</td>
                          <td className="p-1.5 text-right tabular-nums">{day.netCapacityHours}j</td>
                          <td className="p-1.5 text-right tabular-nums">
                            {day.confirmedHours}j
                            {day.tentativeHours > 0 && (
                              <span className="text-purple-600 ml-1">+{day.tentativeHours}</span>
                            )}
                          </td>
                          <td className="p-1.5 font-sans text-[10px]">
                            {day.isHoliday ? (
                              <span className="text-rose-600 font-medium">Libur ({day.holidayName || 'Nasional'})</span>
                            ) : !day.isWorkday ? (
                              <span className="text-slate-400">Akhir Pekan</span>
                            ) : day.leaveHours > 0 ? (
                              <span className="text-amber-700 font-medium">
                                Cuti {day.leaveHours}j {day.hasLeaveConflict ? '⚠️' : ''}
                              </span>
                            ) : (
                              <span className="text-slate-500">Normal</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Active Allocations Section */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="font-bold text-slate-900 text-xs">
                {t('capacity.activeAllocations', 'Daftar Alokasi Aktif pada Periode Ini')}
              </h3>
              <button
                type="button"
                onClick={onAddAllocation}
                className="px-2 py-1 bg-slate-900 text-white rounded text-[11px] font-medium hover:bg-slate-800 transition-colors focus:outline-none"
              >
                + {t('allocations.add', 'Tambah Alokasi')}
              </button>
            </div>

            {uniqueAllocations.length === 0 ? (
              <div className="p-4 border border-dashed border-slate-200 rounded text-center text-slate-400 text-xs">
                {t('allocations.emptyPeriod', 'Belum ada alokasi pada periode minggu ini.')}
              </div>
            ) : (
              <div className="space-y-2">
                {uniqueAllocations.map((alloc) => (
                  <div
                    key={alloc.id}
                    className="p-3 border border-slate-200 rounded bg-white hover:border-slate-300 transition-colors flex items-center justify-between gap-3"
                  >
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-slate-900 text-xs">
                          [{alloc.projectCode}] {alloc.projectName}
                        </span>
                        <span
                          className={`text-[9px] font-mono px-1 py-0.2 rounded border font-semibold ${alloc.allocationType === 'CONFIRMED'
                              ? 'bg-slate-100 text-slate-700 border-slate-300'
                              : 'bg-purple-100 text-purple-700 border-purple-300'
                            }`}
                        >
                          {alloc.allocationType}
                        </span>
                      </div>
                      {alloc.taskTitle && (
                        <p className="text-[11px] text-slate-600">
                          ↳ Task: {alloc.taskTitle}
                        </p>
                      )}
                      <p className="text-[10px] text-slate-400 font-mono">
                        {alloc.startDate} s/d {alloc.endDate} ·{' '}
                        <strong className="text-slate-700">{alloc.hoursPerDay} jam/hari</strong>
                      </p>
                    </div>

                    {canEditAllocation(alloc) && (
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          type="button"
                          onClick={() => onEditAllocation(alloc)}
                          className="px-2 py-1 border border-slate-300 rounded text-slate-700 hover:bg-slate-100 font-medium text-[11px]"
                        >
                          {t('common.edit', 'Ubah')}
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            if (confirm(t('allocations.confirmDelete', 'Hapus alokasi ini?'))) {
                              deleteAllocationMutation.mutate(alloc.id);
                            }
                          }}
                          className="p-1 text-rose-600 hover:bg-rose-50 rounded"
                          title={t('common.delete', 'Hapus')}
                        >
                          ✕
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Active Leaves Section (if any) */}
          {uniqueLeaves.length > 0 && (
            <div>
              <h3 className="font-bold text-slate-900 text-xs mb-2">
                {t('capacity.registeredLeaves', 'Cuti Terdaftar pada Periode Ini')}
              </h3>
              <div className="space-y-1.5">
                {uniqueLeaves.map((l) => (
                  <div
                    key={l.id}
                    className="p-2 border border-amber-200 bg-amber-50 rounded text-[11px] flex items-center justify-between"
                  >
                    <div>
                      <span className="font-semibold text-amber-900">
                        {l.leaveType} ({l.portion})
                      </span>
                      <span className="text-amber-700 ml-2 font-mono">
                        {l.startDate} s/d {l.endDate}
                      </span>
                      {l.notes && <p className="text-[10px] text-amber-800 mt-0.5">Alasan: {l.notes}</p>}
                    </div>
                    <span className="text-[10px] font-mono font-medium px-1.5 py-0.5 rounded bg-amber-200 text-amber-900">
                      {l.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

