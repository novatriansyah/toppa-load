import React, { useState, useEffect, useMemo } from 'react';
import {
  WeeklyTimesheetData,
  TimeEntryInput,
} from './TimesheetTypes';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/httpClient';
import { ProjectSummary } from '../projects/ProjectTypes';

interface TimesheetWeeklyGridProps {
  timesheetData: WeeklyTimesheetData;
  weekStartDate: string;
  onSave: (entries: TimeEntryInput[]) => Promise<void>;
  onSubmitTimesheet: () => Promise<void>;
  onApprove?: () => Promise<void>;
  onReturn?: (note: string) => Promise<void>;
  readOnly: boolean;
  isSupervisorOrAdmin: boolean;
}

interface GridRow {
  rowKey: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  taskId: string;
  taskTitle: string;
  dailyHours: { [date: string]: number };
}

export const TimesheetWeeklyGrid: React.FC<TimesheetWeeklyGridProps> = ({
  timesheetData,
  weekStartDate,
  onSave,
  onSubmitTimesheet,
  onApprove,
  onReturn,
  readOnly,
  isSupervisorOrAdmin,
}) => {
  // Generate 7 days of the week starting Monday
  const weekDays = useMemo(() => {
    const monday = new Date(weekStartDate);
    return Array.from({ length: 7 }).map((_, i) => {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      const dateStr = d.toISOString().split('T')[0];
      const dayNames = ['Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab', 'Min'];
      return {
        dateStr,
        dayName: dayNames[i],
        dayNum: d.getDate(),
        isWeekend: i >= 5,
      };
    });
  }, [weekStartDate]);

  // Fetch available projects for adding new rows
  const { data: projects = [] } = useQuery<ProjectSummary[]>({
    queryKey: ['projects'],
    queryFn: () => apiClient.get<ProjectSummary[]>('/api/v1/projects'),
    enabled: !readOnly,
  });

  // Local grid rows state for editing
  const [rows, setRows] = useState<GridRow[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [returnNote, setReturnNote] = useState('');
  const [isReturnModalOpen, setIsReturnModalOpen] = useState(false);

  // Initialize rows from timesheetData.entries
  useEffect(() => {
    const rowMap = new Map<string, GridRow>();

    timesheetData.entries.forEach((e) => {
      const key = `${e.projectId}__${e.taskId || 'general'}`;
      if (!rowMap.has(key)) {
        rowMap.set(key, {
          rowKey: key,
          projectId: e.projectId,
          projectCode: e.projectCode,
          projectName: e.projectName,
          taskId: e.taskId || '',
          taskTitle: e.taskTitle || '— Tanpa Task Spesifik —',
          dailyHours: {},
        });
      }
      rowMap.get(key)!.dailyHours[e.entryDate] = Number(e.hours);
    });

    // If comparisons have tasks not yet in rows, add them as empty rows for easy input!
    timesheetData.comparisons.forEach((c) => {
      const key = `${c.projectId}__${c.taskId}`;
      if (!rowMap.has(key)) {
        rowMap.set(key, {
          rowKey: key,
          projectId: c.projectId,
          projectCode: c.projectCode,
          projectName: c.projectCode,
          taskId: c.taskId,
          taskTitle: c.taskTitle,
          dailyHours: {},
        });
      }
    });

    setRows(Array.from(rowMap.values()));
  }, [timesheetData]);

  const handleCellChange = (rowKey: string, dateStr: string, value: string) => {
    const num = Math.max(0, Math.min(24, parseFloat(value) || 0));
    setRows((prev) =>
      prev.map((r) => {
        if (r.rowKey === rowKey) {
          return {
            ...r,
            dailyHours: {
              ...r.dailyHours,
              [dateStr]: num,
            },
          };
        }
        return r;
      })
    );
  };

  const handleAddRow = () => {
    if (projects.length === 0) return;
    const p = projects[0];
    const newKey = `new__${Date.now()}`;
    setRows((prev) => [
      ...prev,
      {
        rowKey: newKey,
        projectId: p.id,
        projectCode: p.code,
        projectName: p.name,
        taskId: '',
        taskTitle: '— Alokasi Umum —',
        dailyHours: {},
      },
    ]);
  };

  const handleDeleteRow = (rowKey: string) => {
    setRows((prev) => prev.filter((r) => r.rowKey !== rowKey));
  };

  // Convert current grid rows back to TimeEntryInput list
  const getEntryInputs = (): TimeEntryInput[] => {
    const list: TimeEntryInput[] = [];
    rows.forEach((r) => {
      Object.entries(r.dailyHours).forEach(([dateStr, hours]) => {
        if (hours > 0) {
          list.push({
            projectId: r.projectId,
            taskId: r.taskId || undefined,
            entryDate: dateStr,
            hours,
          });
        }
      });
    });
    return list;
  };

  const handleSaveClick = async () => {
    setIsSaving(true);
    try {
      await onSave(getEntryInputs());
    } finally {
      setIsSaving(false);
    }
  };

  const handleSubmitClick = async () => {
    if (confirm('Ajukan timesheet minggu ini untuk persetujuan supervisor?')) {
      setIsSubmitting(true);
      try {
        await onSave(getEntryInputs());
        await onSubmitTimesheet();
      } finally {
        setIsSubmitting(false);
      }
    }
  };

  // Compute daily totals
  const dailyTotals = useMemo(() => {
    const totals: { [dateStr: string]: number } = {};
    weekDays.forEach((d) => {
      totals[d.dateStr] = rows.reduce((sum, r) => sum + (r.dailyHours[d.dateStr] || 0), 0);
    });
    return totals;
  }, [rows, weekDays]);

  const grandTotal = Object.values(dailyTotals).reduce((a, b) => a + b, 0);

  const submission = timesheetData.submission;
  const isDraftOrReturned = submission.status === 'DRAFT' || submission.status === 'RETURNED';

  const getStatusBadge = () => {
    switch (submission.status) {
      case 'APPROVED':
        return (
          <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 border border-emerald-300 font-semibold text-xs">
            ✓ Disetujui (Approved)
          </span>
        );
      case 'SUBMITTED':
        return (
          <span className="px-2 py-0.5 rounded bg-blue-100 text-blue-800 border border-blue-300 font-semibold text-xs">
            ⏳ Diajukan (Menunggu Persetujuan)
          </span>
        );
      case 'RETURNED':
        return (
          <span className="px-2 py-0.5 rounded bg-rose-100 text-rose-800 border border-rose-300 font-semibold text-xs">
            ↩ Dikembalikan (Perlu Revisi)
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-300 font-medium text-xs">
            ✎ Draf (Belum Diajukan)
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* 1. Header Card: Status & Actions */}
      <div className="bg-white border border-slate-200 rounded-lg p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-3 shadow-xs">
        <div>
          <div className="flex items-center space-x-2">
            <h2 className="text-sm font-semibold text-slate-900">
              Timesheet Mingguan: {submission.userFullName}
            </h2>
            {getStatusBadge()}
          </div>
          {submission.reviewerNote && (
            <p className="text-xs text-rose-700 mt-1 font-medium bg-rose-50 p-1.5 rounded border border-rose-200">
              Catatan Supervisor: &quot;{submission.reviewerNote}&quot;
            </p>
          )}
        </div>

        <div className="flex items-center space-x-2">
          {!readOnly && isDraftOrReturned && (
            <>
              <button
                type="button"
                onClick={handleAddRow}
                className="px-2.5 py-1.5 rounded border border-slate-300 text-slate-700 hover:bg-slate-50 text-xs font-medium transition-colors cursor-pointer"
              >
                + Tambah Task
              </button>
              <button
                type="button"
                onClick={handleSaveClick}
                disabled={isSaving}
                className="px-3 py-1.5 rounded bg-white border border-slate-300 text-slate-800 hover:bg-slate-50 text-xs font-medium transition-colors cursor-pointer disabled:opacity-50"
              >
                {isSaving ? 'Menyimpan...' : 'Simpan Draf'}
              </button>
              <button
                type="button"
                onClick={handleSubmitClick}
                disabled={isSubmitting || grandTotal === 0}
                className="px-3 py-1.5 rounded bg-blue-600 hover:bg-blue-700 text-white text-xs font-medium transition-colors cursor-pointer disabled:opacity-50"
              >
                {isSubmitting ? 'Mengajukan...' : 'Ajukan Timesheet'}
              </button>
            </>
          )}

          {isSupervisorOrAdmin && submission.status === 'SUBMITTED' && onApprove && onReturn && (
            <>
              <button
                type="button"
                onClick={() => setIsReturnModalOpen(true)}
                className="px-3 py-1.5 rounded bg-white border border-rose-300 text-rose-700 hover:bg-rose-50 text-xs font-medium transition-colors cursor-pointer"
              >
                Kembalikan
              </button>
              <button
                type="button"
                onClick={onApprove}
                className="px-3 py-1.5 rounded bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-medium transition-colors cursor-pointer"
              >
                ✓ Setujui
              </button>
            </>
          )}
        </div>
      </div>

      {/* 2. Timesheet Grid Table */}
      <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto shadow-xs">
        <table className="w-full text-xs text-left border-collapse">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-slate-700">
              <th className="py-2.5 px-3 font-semibold min-w-[220px]">Proyek & Task</th>
              {weekDays.map((d) => (
                <th
                  key={d.dateStr}
                  className={`py-2 px-2 text-center font-semibold w-16 min-w-[64px] ${
                    d.isWeekend ? 'bg-slate-100/60 text-slate-500' : ''
                  }`}
                >
                  <div className="text-[10px] uppercase font-mono">{d.dayName}</div>
                  <div className="text-xs font-mono">{d.dayNum}</div>
                </th>
              ))}
              <th className="py-2.5 px-3 text-right font-semibold w-16">Total</th>
              {!readOnly && isDraftOrReturned && <th className="py-2.5 px-2 text-center w-10">Aksi</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.length === 0 ? (
              <tr>
                <td colSpan={weekDays.length + 3} className="py-8 text-center text-slate-400 italic">
                  Belum ada baris entri task. Klik &quot;+ Tambah Task&quot; di atas untuk mulai mengisi jam kerja.
                </td>
              </tr>
            ) : (
              rows.map((r) => {
                const rowTotal = weekDays.reduce((sum, d) => sum + (r.dailyHours[d.dateStr] || 0), 0);
                return (
                  <tr key={r.rowKey} className="hover:bg-slate-50/60">
                    <td className="py-2 px-3">
                      <div className="font-medium text-slate-900">{r.taskTitle}</div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        {r.projectCode} · {r.projectName}
                      </div>
                    </td>
                    {weekDays.map((d) => {
                      const val = r.dailyHours[d.dateStr] || '';
                      return (
                        <td
                          key={d.dateStr}
                          className={`p-1 text-center ${d.isWeekend ? 'bg-slate-50/50' : ''}`}
                        >
                          {!readOnly && isDraftOrReturned ? (
                            <input
                              type="number"
                              step="0.5"
                              min="0"
                              max="24"
                              value={val}
                              onChange={(e) => handleCellChange(r.rowKey, d.dateStr, e.target.value)}
                              className="w-full text-center font-mono text-xs border border-slate-200 rounded py-1 px-0.5 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-400"
                              placeholder="0"
                            />
                          ) : (
                            <span className="font-mono text-xs text-slate-800">
                              {val ? `${val}j` : '-'}
                            </span>
                          )}
                        </td>
                      );
                    })}
                    <td className="py-2 px-3 text-right font-mono font-semibold text-slate-900">
                      {rowTotal.toFixed(1)}j
                    </td>
                    {!readOnly && isDraftOrReturned && (
                      <td className="py-2 px-2 text-center">
                        <button
                          type="button"
                          onClick={() => handleDeleteRow(r.rowKey)}
                          className="text-slate-400 hover:text-rose-600 transition-colors cursor-pointer text-xs"
                          title="Hapus baris"
                        >
                          ✕
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })
            )}
          </tbody>
          <tfoot>
            <tr className="bg-slate-50/80 border-t border-slate-200 font-semibold text-slate-900">
              <td className="py-2.5 px-3">Total Jam per Hari</td>
              {weekDays.map((d) => {
                const dayTotal = dailyTotals[d.dateStr] || 0;
                const isOverDailyLimit = dayTotal > 24;
                const isOvertime = dayTotal > 8;
                return (
                  <td
                    key={d.dateStr}
                    className={`py-2.5 px-2 text-center font-mono text-xs ${
                      isOverDailyLimit
                        ? 'text-rose-700 bg-rose-50 font-bold'
                        : isOvertime
                        ? 'text-amber-700 bg-amber-50'
                        : 'text-slate-900'
                    }`}
                  >
                    {dayTotal.toFixed(1)}j
                  </td>
                );
              })}
              <td className="py-2.5 px-3 text-right font-mono text-sm text-blue-700 font-bold">
                {grandTotal.toFixed(1)}j
              </td>
              {!readOnly && isDraftOrReturned && <td />}
            </tr>
          </tfoot>
        </table>
      </div>

      {/* 3. Komparasi Beban Kerja: Estimasi vs Alokasi vs Aktual */}
      {timesheetData.comparisons.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg p-4 space-y-3 shadow-xs">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-semibold text-slate-900">
              Komparasi Beban Kerja: Estimasi vs Alokasi vs Aktual
            </h3>
            <span className="text-[11px] text-slate-500 font-mono">
              Minggu {weekStartDate}
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-slate-600">
                  <th className="py-2 px-3 font-semibold">Proyek</th>
                  <th className="py-2 px-3 font-semibold">Judul Task</th>
                  <th className="py-2 px-3 text-right font-semibold">Estimasi Total</th>
                  <th className="py-2 px-3 text-right font-semibold">Alokasi Minggu Ini</th>
                  <th className="py-2 px-3 text-right font-semibold">Aktual Diisi</th>
                  <th className="py-2 px-3 text-right font-semibold">Variansi</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {timesheetData.comparisons.map((c) => {
                  const est = Number(c.estimatedHours);
                  const alloc = Number(c.allocatedHours);
                  const act = Number(c.actualLoggedHours);
                  const diff = act - alloc;
                  return (
                    <tr key={c.taskId} className="hover:bg-slate-50/50">
                      <td className="py-2 px-3 font-mono font-medium text-slate-800">
                        {c.projectCode}
                      </td>
                      <td className="py-2 px-3 text-slate-900 font-medium">{c.taskTitle}</td>
                      <td className="py-2 px-3 text-right font-mono text-slate-600">
                        {est > 0 ? `${est.toFixed(1)}j` : '-'}
                      </td>
                      <td className="py-2 px-3 text-right font-mono text-blue-700 font-medium">
                        {alloc > 0 ? `${alloc.toFixed(1)}j` : '-'}
                      </td>
                      <td className="py-2 px-3 text-right font-mono text-emerald-700 font-bold">
                        {act.toFixed(1)}j
                      </td>
                      <td
                        className={`py-2 px-3 text-right font-mono font-medium ${
                          diff > 0
                            ? 'text-rose-600'
                            : diff < 0
                            ? 'text-amber-600'
                            : 'text-slate-500'
                        }`}
                      >
                        {diff > 0 ? `+${diff.toFixed(1)}j` : `${diff.toFixed(1)}j`}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Return Modal */}
      {isReturnModalOpen && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg border border-slate-200 shadow-xl max-w-md w-full p-4 space-y-3">
            <h3 className="text-sm font-semibold text-slate-900">
              Kembalikan Timesheet untuk Revisi
            </h3>
            <p className="text-xs text-slate-500">
              Tuliskan alasan pengembalian agar anggota tim dapat memperbaiki entri timesheet.
            </p>
            <textarea
              rows={3}
              value={returnNote}
              onChange={(e) => setReturnNote(e.target.value)}
              placeholder="Contoh: Harap periksa kembali jam kerja hari Selasa pada proyek..."
              className="w-full text-xs border border-slate-300 rounded p-2 focus:outline-none focus:ring-1 focus:ring-blue-400 font-sans"
              required
            />
            <div className="flex justify-end space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setIsReturnModalOpen(false)}
                className="px-3 py-1.5 rounded border border-slate-300 text-xs text-slate-700 hover:bg-slate-50 transition-colors"
              >
                Batal
              </button>
              <button
                type="button"
                onClick={async () => {
                  if (onReturn && returnNote.trim()) {
                    await onReturn(returnNote.trim());
                    setIsReturnModalOpen(false);
                    setReturnNote('');
                  }
                }}
                disabled={!returnNote.trim()}
                className="px-3 py-1.5 rounded bg-rose-600 text-white text-xs font-medium hover:bg-rose-700 transition-colors disabled:opacity-50"
              >
                Kembalikan Timesheet
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

