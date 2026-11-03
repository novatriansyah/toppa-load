import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient, HttpError } from '../../shared/api/httpClient';
import { UserRole } from '../auth/useCurrentUser';

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  active: boolean;
  workHoursPerDay: number;
  workDays: number[];
}

interface CreateUserPayload {
  email: string;
  fullName: string;
  role: UserRole;
  workHoursPerDay: number;
  workDays: number[];
}

interface PatchUserPayload {
  fullName?: string;
  role?: UserRole;
  active?: boolean;
  workHoursPerDay?: number;
  workDays?: number[];
}

const DAYS_OF_WEEK = [
  { value: 1, labelKey: 'days.mon' },
  { value: 2, labelKey: 'days.tue' },
  { value: 3, labelKey: 'days.wed' },
  { value: 4, labelKey: 'days.thu' },
  { value: 5, labelKey: 'days.fri' },
  { value: 6, labelKey: 'days.sat' },
  { value: 7, labelKey: 'days.sun' },
];

export const UsersSettingsSection: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserSummary | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // Form states for Add/Edit
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<UserRole>('MEMBER');
  const [active, setActive] = useState(true);
  const [workHours, setWorkHours] = useState('8');
  const [selectedDays, setSelectedDays] = useState<number[]>([1, 2, 3, 4, 5]);

  const { data: users, isLoading, error } = useQuery<UserSummary[]>({
    queryKey: ['users'],
    queryFn: () => apiClient.get<UserSummary[]>('/api/v1/users'),
  });

  const createMutation = useMutation({
    mutationFn: (payload: CreateUserPayload) => apiClient.post<UserSummary>('/api/v1/users', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setFormError(err.message);
      } else {
        setFormError(t('common.error'));
      }
    },
  });

  const patchMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: PatchUserPayload }) =>
      apiClient.patch<UserSummary>(`/api/v1/users/${id}`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['currentUser'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setFormError(err.message);
      } else {
        setFormError(t('common.error'));
      }
    },
  });

  const openAddModal = () => {
    setEditingUser(null);
    setEmail('');
    setFullName('');
    setRole('MEMBER');
    setActive(true);
    setWorkHours('8');
    setSelectedDays([1, 2, 3, 4, 5]);
    setFormError(null);
    setIsAddModalOpen(true);
  };

  const openEditModal = (u: UserSummary) => {
    setEditingUser(u);
    setEmail(u.email);
    setFullName(u.fullName);
    setRole(u.role);
    setActive(u.active);
    setWorkHours(String(u.workHoursPerDay));
    setSelectedDays(u.workDays || [1, 2, 3, 4, 5]);
    setFormError(null);
    setIsAddModalOpen(true);
  };

  const closeModal = () => {
    setIsAddModalOpen(false);
    setEditingUser(null);
    setFormError(null);
  };

  const toggleDay = (day: number) => {
    setSelectedDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day].sort()
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const hoursNum = parseFloat(workHours);
    if (isNaN(hoursNum) || hoursNum <= 0 || hoursNum > 24) {
      setFormError(t('settings.users.hoursInvalid'));
      return;
    }

    if (selectedDays.length === 0) {
      setFormError(t('settings.users.daysInvalid'));
      return;
    }

    if (editingUser) {
      patchMutation.mutate({
        id: editingUser.id,
        payload: {
          fullName,
          role,
          active,
          workHoursPerDay: hoursNum,
          workDays: selectedDays,
        },
      });
    } else {
      if (!email.trim() || !fullName.trim()) {
        setFormError(t('settings.users.requiredFields'));
        return;
      }
      createMutation.mutate({
        email: email.trim().toLowerCase(),
        fullName: fullName.trim(),
        role,
        workHoursPerDay: hoursNum,
        workDays: selectedDays,
      });
    }
  };

  const formatDaysSummary = (days: number[]) => {
    if (!days || days.length === 0) return '-';
    if (days.length === 5 && [1, 2, 3, 4, 5].every((d) => days.includes(d))) {
      return t('settings.users.monFri');
    }
    const dayNames = days.map((d) => {
      const match = DAYS_OF_WEEK.find((item) => item.value === d);
      return match ? t(match.labelKey) : String(d);
    });
    return dayNames.join(', ');
  };

  return (
    <div className="space-y-4">
      {/* Header and Add button */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">
            {t('settings.users.title')}
          </h2>
          <p className="text-xs text-slate-500">
            {t('settings.users.subtitle')}
          </p>
        </div>
        <button
          type="button"
          onClick={openAddModal}
          className="inline-flex items-center space-x-1 px-2.5 py-1.5 bg-slate-900 text-white rounded text-xs font-medium hover:bg-slate-800 transition-colors"
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          <span>{t('settings.users.addUser')}</span>
        </button>
      </div>

      {/* Users Table */}
      <div className="bg-white border border-slate-200 rounded overflow-hidden">
        {isLoading ? (
          <div className="p-4 text-xs text-slate-500 text-center">{t('app.loading')}</div>
        ) : error ? (
          <div className="p-4 text-xs text-rose-600 text-center">{t('common.error')}</div>
        ) : !users || users.length === 0 ? (
          <div className="p-4 text-xs text-slate-400 text-center">{t('settings.users.empty')}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 font-medium select-none">
                  <th className="py-2 px-3">{t('settings.users.colUser')}</th>
                  <th className="py-2 px-3">{t('settings.users.colRole')}</th>
                  <th className="py-2 px-3">{t('settings.users.colStatus')}</th>
                  <th className="py-2 px-3 text-right">{t('settings.users.colWorkHours')}</th>
                  <th className="py-2 px-3">{t('settings.users.colWorkDays')}</th>
                  <th className="py-2 px-3 text-right">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-50/75 transition-colors">
                    <td className="py-2 px-3">
                      <div className="font-medium text-slate-900">{u.fullName}</div>
                      <div className="text-[11px] text-slate-400 font-mono">{u.email}</div>
                    </td>
                    <td className="py-2 px-3">
                      <span
                        className={`inline-block px-1.5 py-0.5 rounded text-[11px] font-medium border ${
                          u.role === 'ADMIN'
                            ? 'bg-purple-50 text-purple-700 border-purple-200'
                            : u.role === 'SUPERVISOR'
                            ? 'bg-blue-50 text-blue-700 border-blue-200'
                            : 'bg-slate-100 text-slate-700 border-slate-200'
                        }`}
                      >
                        {u.role}
                      </span>
                    </td>
                    <td className="py-2 px-3">
                      <span
                        className={`inline-flex items-center space-x-1 px-1.5 py-0.5 rounded text-[11px] font-medium ${
                          u.active
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : 'bg-rose-50 text-rose-700 border border-rose-200'
                        }`}
                      >
                        <span className={`w-1.5 h-1.5 rounded-full ${u.active ? 'bg-emerald-500' : 'bg-rose-500'}`} />
                        <span>{u.active ? t('settings.users.statusActive') : t('settings.users.statusInactive')}</span>
                      </span>
                    </td>
                    <td className="py-2 px-3 text-right font-medium text-slate-800">
                      <span className="num-tabular">{Number(u.workHoursPerDay).toFixed(1)}</span>
                      <span className="text-slate-400 ml-0.5">{t('common.hoursShort')}</span>
                    </td>
                    <td className="py-2 px-3 text-slate-600 text-[11px]">
                      {formatDaysSummary(u.workDays)}
                    </td>
                    <td className="py-2 px-3 text-right">
                      <button
                        type="button"
                        onClick={() => openEditModal(u)}
                        className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium border border-slate-200 transition-colors"
                      >
                        {t('common.edit')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add / Edit User Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div
            className="w-full max-w-md bg-white border border-slate-200 rounded-md shadow-lg p-5 space-y-4"
            role="dialog"
            aria-modal="true"
          >
            <div className="flex items-center justify-between pb-2 border-b border-slate-200">
              <h3 className="text-sm font-semibold text-slate-900">
                {editingUser ? t('settings.users.editUser') : t('settings.users.addUser')}
              </h3>
              <button
                type="button"
                onClick={closeModal}
                className="text-slate-400 hover:text-slate-600"
                aria-label={t('common.close')}
              >
                ✕
              </button>
            </div>

            {formError && (
              <div className="p-2.5 rounded bg-rose-50 border border-rose-200 text-xs text-rose-700">
                {formError}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              {/* Email */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('settings.users.email')}
                </label>
                <input
                  type="email"
                  value={email}
                  disabled={!!editingUser}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="nama@kantor.id"
                  required
                  className={`w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none ${
                    editingUser ? 'bg-slate-100 text-slate-500 cursor-not-allowed' : 'bg-white'
                  }`}
                />
              </div>

              {/* Full Name */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('settings.users.fullName')}
                </label>
                <input
                  type="text"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Nama Lengkap"
                  required
                  className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none"
                />
              </div>

              {/* Role */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('settings.users.role')}
                </label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value as UserRole)}
                  className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none bg-white"
                >
                  <option value="MEMBER">MEMBER</option>
                  <option value="SUPERVISOR">SUPERVISOR</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </div>

              {/* Active Toggle (Edit mode only) */}
              {editingUser && (
                <div className="flex items-center space-x-2 pt-1">
                  <input
                    type="checkbox"
                    id="user-active-toggle"
                    checked={active}
                    onChange={(e) => setActive(e.target.checked)}
                    className="rounded border-slate-300 text-slate-900 focus:ring-slate-400"
                  />
                  <label htmlFor="user-active-toggle" className="font-medium text-slate-700 select-none">
                    {t('settings.users.activeStatusLabel')}
                  </label>
                </div>
              )}

              {/* Daily Work Hours */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('settings.users.workHoursLabel')} ({t('common.hoursShort')})
                </label>
                <div className="flex items-center space-x-2">
                  <input
                    type="number"
                    step="0.5"
                    min="1"
                    max="24"
                    value={workHours}
                    onChange={(e) => setWorkHours(e.target.value)}
                    className="w-24 px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none num-tabular"
                  />
                  <span className="text-slate-500 font-mono text-xs">{t('settings.users.hoursPerDayUnit')}</span>
                </div>
              </div>

              {/* Work Days Checkboxes */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('settings.users.workDaysLabel')}
                </label>
                <div className="grid grid-cols-4 gap-2 pt-1">
                  {DAYS_OF_WEEK.map((d) => {
                    const checked = selectedDays.includes(d.value);
                    return (
                      <label
                        key={d.value}
                        className={`flex items-center space-x-1.5 px-2 py-1 rounded border cursor-pointer select-none text-[11px] ${
                          checked
                            ? 'bg-slate-100 border-slate-400 text-slate-900 font-medium'
                            : 'bg-white border-slate-200 text-slate-500 hover:bg-slate-50'
                        }`}
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleDay(d.value)}
                          className="rounded border-slate-300 text-slate-900 focus:ring-slate-400"
                        />
                        <span>{t(d.labelKey)}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              {/* Actions */}
              <div className="flex items-center justify-end space-x-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={closeModal}
                  className="px-3 py-1.5 rounded bg-slate-100 text-slate-700 font-medium hover:bg-slate-200 transition-colors"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || patchMutation.isPending}
                  className="px-3 py-1.5 rounded bg-slate-900 text-white font-medium hover:bg-slate-800 transition-colors disabled:opacity-50"
                >
                  {createMutation.isPending || patchMutation.isPending
                    ? t('app.loading')
                    : t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default UsersSettingsSection;
