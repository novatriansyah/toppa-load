import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient, HttpError } from '../../shared/api/httpClient';
import { AllocationSummaryData, AllocationPreviewResponseData } from '../heatmap/HeatmapTypes';
import { useCurrentUser } from '../auth/useCurrentUser';

export interface AllocationFormDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  initialUserId?: string;
  initialStartDate?: string;
  initialEndDate?: string;
  initialProjectId?: string;
  initialTaskId?: string;
  initialHoursPerDay?: string;
  initialNotes?: string;
  editingAllocation?: AllocationSummaryData | null;
  onSuccess?: () => void;
}

interface UserOption {
  id: string;
  fullName: string;
  email: string;
  role: string;
}

interface ProjectOption {
  id: string;
  code: string;
  name: string;
}

interface TaskOption {
  id: string;
  title: string;
  status: string;
}

export const AllocationFormDrawer: React.FC<AllocationFormDrawerProps> = ({
  isOpen,
  onClose,
  initialUserId,
  initialStartDate,
  initialEndDate,
  initialProjectId,
  initialTaskId,
  initialHoursPerDay,
  initialNotes,
  editingAllocation,
  onSuccess,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();

  const isMember = currentUser?.role === 'MEMBER';

  const [userId, setUserId] = useState<string>('');
  const [projectId, setProjectId] = useState<string>('');
  const [taskId, setTaskId] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');
  const [hoursPerDay, setHoursPerDay] = useState<string>('8.0');
  const [allocationType, setAllocationType] = useState<'CONFIRMED' | 'TENTATIVE'>('CONFIRMED');
  const [notes, setNotes] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string>('');

  // Fetch users for selector
  const { data: users = [] } = useQuery<UserOption[]>({
    queryKey: ['users'],
    queryFn: () => apiClient.get<UserOption[]>('/api/v1/users'),
    enabled: isOpen && !isMember,
  });

  // Fetch projects for selector
  const { data: projects = [] } = useQuery<ProjectOption[]>({
    queryKey: ['projects'],
    queryFn: () => apiClient.get<ProjectOption[]>('/api/v1/projects'),
    enabled: isOpen,
  });

  // Fetch tasks for chosen project
  const { data: tasks = [] } = useQuery<TaskOption[]>({
    queryKey: ['projects', projectId, 'tasks'],
    queryFn: () => apiClient.get<TaskOption[]>(`/api/v1/projects/${projectId}/tasks`),
    enabled: isOpen && Boolean(projectId),
  });

  // Set initial form state
  useEffect(() => {
    if (editingAllocation) {
      setUserId(editingAllocation.userId);
      setProjectId(editingAllocation.projectId);
      setTaskId(editingAllocation.taskId || '');
      setStartDate(editingAllocation.startDate);
      setEndDate(editingAllocation.endDate);
      setHoursPerDay(String(editingAllocation.hoursPerDay));
      setAllocationType(editingAllocation.allocationType);
      setNotes(editingAllocation.notes || '');
    } else {
      setUserId(initialUserId || currentUser?.userId || '');
      setProjectId(initialProjectId || '');
      setTaskId(initialTaskId || '');
      setStartDate(initialStartDate || new Date().toISOString().split('T')[0]);
      setEndDate(initialEndDate || initialStartDate || new Date().toISOString().split('T')[0]);
      setHoursPerDay(initialHoursPerDay || '8.0');
      setAllocationType(isMember ? 'TENTATIVE' : 'CONFIRMED');
      setNotes(initialNotes || '');
    }
    setErrorMessage('');
  }, [
    editingAllocation,
    initialUserId,
    initialStartDate,
    initialEndDate,
    initialProjectId,
    initialTaskId,
    initialHoursPerDay,
    initialNotes,
    currentUser,
    isMember,
    isOpen,
  ]);

  // Sync taskId when tasks finishes loading if initialTaskId was requested
  useEffect(() => {
    if (initialTaskId && tasks.length > 0 && !taskId) {
      const match = tasks.find((t) => t.id === initialTaskId);
      if (match) {
        setTaskId(match.id);
      }
    }
  }, [tasks, initialTaskId, taskId]);

  // Live preview query
  const canPreview = Boolean(
    userId &&
    startDate &&
    endDate &&
    parseFloat(hoursPerDay) > 0 &&
    startDate <= endDate
  );

  const { data: previewData } = useQuery<AllocationPreviewResponseData>({
    queryKey: ['allocationPreview', userId, startDate, endDate, hoursPerDay, allocationType, editingAllocation?.id],
    queryFn: () =>
      apiClient.post<AllocationPreviewResponseData>('/api/v1/allocations/preview', {
        userId,
        startDate,
        endDate,
        hoursPerDay: parseFloat(hoursPerDay),
        allocationType,
        excludeAllocationId: editingAllocation?.id,
      }),
    enabled: isOpen && canPreview,
  });

  // Save mutation
  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        userId,
        projectId,
        taskId: taskId || null,
        startDate,
        endDate,
        hoursPerDay: parseFloat(hoursPerDay),
        allocationType,
        notes: notes.trim() || null,
      };

      if (editingAllocation) {
        return apiClient.put(`/api/v1/allocations/${editingAllocation.id}`, payload);
      }
      return apiClient.post('/api/v1/allocations', payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
      queryClient.invalidateQueries({ queryKey: ['allocations'] });
      onSuccess?.();
      onClose();
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage(t('common.errorSave', 'Gagal menyimpan alokasi. Silakan periksa formulir.'));
      }
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');

    if (!projectId) {
      setErrorMessage(t('allocations.selectProjectRequired', 'Silakan pilih proyek terlebih dahulu.'));
      return;
    }
    if (startDate > endDate) {
      setErrorMessage(t('allocations.invalidDateRange', 'Tanggal selesai tidak boleh sebelum tanggal mulai.'));
      return;
    }
    const parsedHours = parseFloat(hoursPerDay);
    if (isNaN(parsedHours) || parsedHours <= 0 || parsedHours > 24) {
      setErrorMessage(t('allocations.invalidHours', 'Jam per hari harus antara 0.1 dan 24.'));
      return;
    }

    saveMutation.mutate();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-black/40 backdrop-blur-xs flex justify-end">
      <div className="w-full max-w-lg bg-white h-full shadow-2xl flex flex-col animate-in slide-in-from-right duration-200 border-l border-slate-200">
        {/* Header */}
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50">
          <div>
            <h2 className="text-sm font-bold text-slate-900">
              {editingAllocation
                ? t('allocations.editTitle', 'Ubah Alokasi')
                : t('allocations.newTitle', 'Tambah Alokasi Beban Kerja')}
            </h2>
            <p className="text-xs text-slate-500">
              {t('allocations.subtitle', 'Tetapkan jam alokasi per hari pada proyek atau tiket task.')}
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

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-4 space-y-4 text-xs">
          {errorMessage && (
            <div className="p-2.5 rounded bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2">
              <span className="shrink-0">⚠️</span>
              <div>{errorMessage}</div>
            </div>
          )}

          {/* User Selector */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">
              {t('allocations.assignee', 'Anggota Tim')}
            </label>
            {isMember ? (
              <input
                type="text"
                disabled
                value={currentUser?.fullName || currentUser?.email || ''}
                className="w-full px-2.5 py-1.5 border border-slate-200 rounded bg-slate-100 text-slate-600 font-medium cursor-not-allowed"
              />
            ) : (
              <select
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                required
                className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none"
              >
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName} ({u.role})
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Project Selector */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">
              {t('allocations.project', 'Proyek')} <span className="text-rose-500">*</span>
            </label>
            <select
              value={projectId}
              onChange={(e) => {
                setProjectId(e.target.value);
                setTaskId('');
              }}
              required
              className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none"
            >
              <option value="">{t('allocations.chooseProject', '-- Pilih Proyek --')}</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  [{p.code}] {p.name}
                </option>
              ))}
            </select>
          </div>

          {/* Task Selector (Optional) */}
          {projectId && tasks.length > 0 && (
            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('allocations.taskOptional', 'Tiket Task (Opsional)')}
              </label>
              <select
                value={taskId}
                onChange={(e) => setTaskId(e.target.value)}
                className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none"
              >
                <option value="">{t('allocations.allProjectTasks', '-- Alokasi Level Proyek Penuh --')}</option>
                {tasks.map((task) => (
                  <option key={task.id} value={task.id}>
                    {task.title} ({task.status})
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Date Range */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('allocations.startDate', 'Tanggal Mulai')} <span className="text-rose-500">*</span>
              </label>
              <input
                type="date"
                required
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full px-2.5 py-1.5 border border-slate-300 rounded font-mono text-xs focus:ring-2 focus:ring-slate-900 focus:outline-none"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('allocations.endDate', 'Tanggal Selesai')} <span className="text-rose-500">*</span>
              </label>
              <input
                type="date"
                required
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-2.5 py-1.5 border border-slate-300 rounded font-mono text-xs focus:ring-2 focus:ring-slate-900 focus:outline-none"
              />
            </div>
          </div>

          {/* Hours per Day & Allocation Type */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('allocations.hoursPerDay', 'Jam per Hari')} <span className="text-rose-500">*</span>
              </label>
              <div className="relative">
                <input
                  type="number"
                  step="0.25"
                  min="0.1"
                  max="24"
                  required
                  value={hoursPerDay}
                  onChange={(e) => setHoursPerDay(e.target.value)}
                  className="w-full px-2.5 py-1.5 pr-8 border border-slate-300 rounded font-mono text-xs focus:ring-2 focus:ring-slate-900 focus:outline-none"
                />
                <span className="absolute right-2.5 top-1.5 text-slate-400 font-mono">jam</span>
              </div>
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('allocations.type', 'Tipe Alokasi')}
              </label>
              {isMember ? (
                <div className="px-2.5 py-1.5 border border-slate-200 rounded bg-slate-100 text-slate-600 font-mono">
                  TENTATIVE ({t('allocations.tentativeOnly', 'Tentatif')})
                </div>
              ) : (
                <select
                  value={allocationType}
                  onChange={(e) => setAllocationType(e.target.value as 'CONFIRMED' | 'TENTATIVE')}
                  className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white text-slate-900 font-medium focus:ring-2 focus:ring-slate-900 focus:outline-none"
                >
                  <option value="CONFIRMED">{t('allocations.confirmed', 'CONFIRMED (Pasti)')}</option>
                  <option value="TENTATIVE">{t('allocations.tentative', 'TENTATIVE (Tentatif / Draft)')}</option>
                </select>
              )}
            </div>
          </div>

          {/* Live Preview Impact Section */}
          {previewData && (
            <div className="p-3 rounded border border-slate-200 bg-slate-50 space-y-2">
              <div className="flex items-center justify-between text-[11px] font-semibold text-slate-700">
                <span>{t('allocations.previewImpact', 'Dampak Langsung ke Heatmap:')}</span>
                {previewData.introducesOverload ? (
                  <span className="text-rose-700 font-bold bg-rose-100 px-1.5 py-0.5 rounded border border-rose-300">
                    ⚠️ {t('capacity.statusOverloaded', 'Kelebihan Beban')}
                  </span>
                ) : (
                  <span className="text-emerald-700 font-bold bg-emerald-100 px-1.5 py-0.5 rounded border border-emerald-300">
                    ✓ {t('capacity.statusOptimal', 'Aman / Optimal')}
                  </span>
                )}
              </div>

              {previewData.introducesOverload && (
                <p className="text-[11px] text-rose-700 font-medium">
                  {t('allocations.overloadWarning', '⚠️ Alokasi ini akan menyebabkan utilisasi pengguna melampaui 100% pada minggu terkait!')}
                </p>
              )}

              {previewData.introducesLeaveConflict && (
                <p className="text-[11px] text-amber-700 font-medium">
                  {t('allocations.leaveConflictWarning', '⚠️ Perhatian: Anggota memiliki Cuti aktif pada rentang tanggal ini (Konflik Cuti terdeteksi).')}
                </p>
              )}
            </div>
          )}

          {/* Notes */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">
              {t('allocations.notes', 'Catatan Penugasan (Opsional)')}
            </label>
            <textarea
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={t('allocations.notesPlaceholder', 'Misal: Fokus refactoring modul autentikasi')}
              className="w-full px-2.5 py-1.5 border border-slate-300 rounded text-xs focus:ring-2 focus:ring-slate-900 focus:outline-none"
            />
          </div>

          {/* Actions */}
          <div className="pt-3 border-t border-slate-200 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 border border-slate-300 rounded text-slate-700 hover:bg-slate-100 font-medium"
            >
              {t('common.cancel', 'Batal')}
            </button>
            <button
              type="submit"
              disabled={saveMutation.isPending}
              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded font-medium focus:ring-2 focus:ring-slate-400 focus:outline-none disabled:opacity-50"
            >
              {saveMutation.isPending
                ? t('common.saving', 'Menyimpan...')
                : editingAllocation
                ? t('common.saveChanges', 'Simpan Perubahan')
                : t('allocations.createButton', 'Simpan Alokasi')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

