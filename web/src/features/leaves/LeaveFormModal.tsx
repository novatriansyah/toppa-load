import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient, HttpError } from '../../shared/api/httpClient';
import { useCurrentUser } from '../auth/useCurrentUser';

export interface LeaveFormModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface UserOption {
  id: string;
  fullName: string;
  email: string;
  role: string;
}

export const LeaveFormModal: React.FC<LeaveFormModalProps> = ({ isOpen, onClose }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();

  const isMember = currentUser?.role === 'MEMBER';

  const [userId, setUserId] = useState('');
  const [leaveType, setLeaveType] = useState<'ANNUAL' | 'SICK' | 'UNPAID' | 'OTHER'>('ANNUAL');
  const [portion, setPortion] = useState<'FULL' | 'HALF_AM' | 'HALF_PM'>('FULL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [notes, setNotes] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  const { data: users = [] } = useQuery<UserOption[]>({
    queryKey: ['users'],
    queryFn: () => apiClient.get<UserOption[]>('/api/v1/users'),
    enabled: isOpen && !isMember,
  });

  useEffect(() => {
    if (isOpen) {
      setUserId(currentUser?.userId || '');
      setLeaveType('ANNUAL');
      setPortion('FULL');
      const today = new Date().toISOString().split('T')[0];
      setStartDate(today);
      setEndDate(today);
      setNotes('');
      setErrorMessage('');
    }
  }, [isOpen, currentUser]);

  const saveMutation = useMutation({
    mutationFn: (payload: unknown) => apiClient.post('/api/v1/leaves', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
      onClose();
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage(t('leaves.errorSave', 'Gagal mencatat cuti. Periksa tanggal.'));
      }
    },
  });

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');

    if (portion !== 'FULL' && startDate !== endDate) {
      setErrorMessage(
        t('leaves.halfDayDateMismatch', 'Cuti setengah hari hanya berlaku untuk 1 tanggal spesifik (tanggal mulai dan selesai harus sama).')
      );
      return;
    }
    if (startDate > endDate) {
      setErrorMessage(t('allocations.invalidDateRange', 'Tanggal selesai tidak boleh sebelum tanggal mulai.'));
      return;
    }

    saveMutation.mutate({
      userId,
      leaveType,
      portion,
      startDate,
      endDate,
      notes: notes.trim() || null,
    });
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-white rounded-lg shadow-xl border border-slate-200 overflow-hidden animate-in fade-in duration-150">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50">
          <div>
            <h2 className="text-sm font-bold text-slate-900">
              {t('leaves.createTitle', 'Catat / Ajukan Cuti')}
            </h2>
            <p className="text-xs text-slate-500">
              {t('leaves.createSubtitle', 'Cuti akan otomatis mengurangi kapasitas bersih pada heatmap.')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded text-slate-400 hover:text-slate-700 hover:bg-slate-200 focus:outline-none"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-4 space-y-4 text-xs">
          {errorMessage && (
            <div className="p-2.5 rounded bg-rose-50 border border-rose-200 text-rose-800">
              ⚠️ {errorMessage}
            </div>
          )}

          {/* User selection */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">
              {t('leaves.user', 'Anggota Tim')}
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

          {/* Leave Type and Portion */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('leaves.type', 'Tipe Cuti')}
              </label>
              <select
                value={leaveType}
                onChange={(e) => setLeaveType(e.target.value as any)}
                className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none"
              >
                <option value="ANNUAL">Cuti Tahunan (ANNUAL)</option>
                <option value="SICK">Cuti Sakit (SICK)</option>
                <option value="UNPAID">Cuti Tanpa Gaji (UNPAID)</option>
                <option value="OTHER">Lainnya (OTHER)</option>
              </select>
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('leaves.portion', 'Porsi')}
              </label>
              <select
                value={portion}
                onChange={(e) => {
                  const newPortion = e.target.value as any;
                  setPortion(newPortion);
                  if (newPortion !== 'FULL') {
                    setEndDate(startDate); // Auto match for half day
                  }
                }}
                className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none"
              >
                <option value="FULL">Cuti Penuh (1 Hari / 8j)</option>
                <option value="HALF_AM">Setengah Hari Pagi (4j)</option>
                <option value="HALF_PM">Setengah Hari Siang (4j)</option>
              </select>
            </div>
          </div>

          {/* Dates */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('leaves.startDate', 'Mulai')} <span className="text-rose-500">*</span>
              </label>
              <input
                type="date"
                required
                value={startDate}
                onChange={(e) => {
                  setStartDate(e.target.value);
                  if (portion !== 'FULL') {
                    setEndDate(e.target.value);
                  }
                }}
                className="w-full px-2.5 py-1.5 border border-slate-300 rounded font-mono text-xs focus:ring-2 focus:ring-slate-900 focus:outline-none"
              />
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">
                {t('leaves.endDate', 'Selesai')} <span className="text-rose-500">*</span>
              </label>
              <input
                type="date"
                required
                disabled={portion !== 'FULL'}
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className={`w-full px-2.5 py-1.5 border border-slate-300 rounded font-mono text-xs focus:ring-2 focus:ring-slate-900 focus:outline-none ${portion !== 'FULL' ? 'bg-slate-100 cursor-not-allowed' : ''
                  }`}
              />
            </div>
          </div>

          {/* Reason */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">
              {t('leaves.reason', 'Alasan Cuti (Opsional)')}
            </label>
            <input
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Misal: Keperluan keluarga"
              className="w-full px-2.5 py-1.5 border border-slate-300 rounded text-xs focus:ring-2 focus:ring-slate-900 focus:outline-none"
            />
          </div>

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
              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded font-medium disabled:opacity-50"
            >
              {saveMutation.isPending ? 'Menyimpan...' : 'Simpan Cuti'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

