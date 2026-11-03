import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';
import { LeaveSummaryData, HolidaySummaryData } from '../heatmap/HeatmapTypes';
import { useCurrentUser } from '../auth/useCurrentUser';
import { LeaveFormModal } from './LeaveFormModal';
import { HolidayImportModal } from './HolidayImportModal';
import { UserSkillsModal } from './UserSkillsModal';

interface LeaveBalanceData {
  id: string;
  userId: string;
  userFullName: string;
  year: number;
  totalAllowanceDays: number;
  usedDays: number;
  remainingDays: number;
}

interface UserSkillData {
  id: string;
  userId: string;
  userFullName: string;
  userEmail: string;
  skillName: string;
  proficiencyLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
}

export const LeavesAndHolidaysPage: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();

  const isSupervisorOrAdmin = currentUser?.role === 'ADMIN' || currentUser?.role === 'SUPERVISOR';

  const [activeTab, setActiveTab] = useState<'leaves' | 'holidays' | 'skills'>('leaves');
  const [selectedYear, setSelectedYear] = useState<number>(new Date().getFullYear());
  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);
  const [isHolidayModalOpen, setIsHolidayModalOpen] = useState(false);
  const [isSkillsModalOpen, setIsSkillsModalOpen] = useState(false);
  const [skillSearchQuery, setSkillSearchQuery] = useState('');

  // Fetch leaves
  const { data: leaves = [], isLoading: isLoadingLeaves } = useQuery<LeaveSummaryData[]>({
    queryKey: ['leaves'],
    queryFn: () => apiClient.get<LeaveSummaryData[]>('/api/v1/leaves'),
  });

  // Fetch leave balance for current user and year
  const { data: leaveBalance } = useQuery<LeaveBalanceData>({
    queryKey: ['leaveBalance', selectedYear, currentUser?.userId],
    queryFn: () =>
      apiClient.get<LeaveBalanceData>('/api/v1/leaves/balances', {
        params: { year: selectedYear, userId: currentUser?.userId },
      }),
    enabled: !!currentUser?.userId,
  });

  // Fetch holidays for selected year
  const { data: holidays = [], isLoading: isLoadingHolidays } = useQuery<HolidaySummaryData[]>({
    queryKey: ['holidays', selectedYear],
    queryFn: () =>
      apiClient.get<HolidaySummaryData[]>('/api/v1/holidays', {
        params: { year: selectedYear },
      }),
  });

  // Fetch skills directory
  const { data: skillsDirectory = [], isLoading: isLoadingSkills } = useQuery<UserSkillData[]>({
    queryKey: ['skillsDirectory'],
    queryFn: () => apiClient.get<UserSkillData[]>('/api/v1/skills/directory'),
  });

  // Approve leave mutation
  const approveLeaveMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/leaves/${id}/approve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      queryClient.invalidateQueries({ queryKey: ['leaveBalance'] });
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
    },
  });

  // Reject leave mutation
  const rejectLeaveMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/leaves/${id}/reject`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      queryClient.invalidateQueries({ queryKey: ['leaveBalance'] });
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
    },
  });

  // Delete leave mutation
  const deleteLeaveMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/leaves/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      queryClient.invalidateQueries({ queryKey: ['leaveBalance'] });
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
    },
  });

  // Delete holiday mutation
  const deleteHolidayMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/holidays/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
    },
  });

  const handleDeleteLeave = (id: string) => {
    if (confirm(t('leaves.confirmDeleteLeave', 'Apakah Anda yakin ingin membatalkan cuti ini?'))) {
      deleteLeaveMutation.mutate(id);
    }
  };

  const handleApproveLeave = (id: string) => {
    approveLeaveMutation.mutate(id);
  };

  const handleRejectLeave = (id: string) => {
    if (confirm(t('leaves.confirmRejectLeave', 'Tolak pengajuan cuti ini?'))) {
      rejectLeaveMutation.mutate(id);
    }
  };

  const handleDeleteHoliday = (id: string) => {
    if (confirm(t('leaves.confirmDeleteHoliday', 'Hapus entri hari libur ini?'))) {
      deleteHolidayMutation.mutate(id);
    }
  };

  // Filter skills directory
  const filteredSkills = skillsDirectory.filter((s) => {
    const q = skillSearchQuery.toLowerCase();
    return (
      s.skillName.toLowerCase().includes(q) ||
      s.userFullName.toLowerCase().includes(q) ||
      s.userEmail.toLowerCase().includes(q)
    );
  });

  const mySkills = skillsDirectory
    .filter((s) => s.userId === currentUser?.userId)
    .map((s) => ({
      skillName: s.skillName,
      proficiencyLevel: s.proficiencyLevel,
    }));

  return (
    <div className="space-y-4">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-900 tracking-tight">
            {t('leaves.pageTitle', 'Manajemen Cuti & Hari Libur')}
          </h1>
          <p className="text-xs text-slate-500">
            {t('leaves.pageSubtitle', 'Kalender libur nasional, saldo cuti, dan persetujuan yang memengaruhi kapasitas kerja.')}
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2">
          {activeTab === 'leaves' && (
            <button
              type="button"
              onClick={() => setIsLeaveModalOpen(true)}
              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-medium shadow-xs transition-colors flex items-center gap-1.5"
            >
              <span>+</span>
              <span>{t('leaves.addLeave', 'Ajukan Cuti')}</span>
            </button>
          )}

          {activeTab === 'holidays' && isSupervisorOrAdmin && (
            <button
              type="button"
              onClick={() => setIsHolidayModalOpen(true)}
              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-medium shadow-xs transition-colors flex items-center gap-1.5"
            >
              <span>📅</span>
              <span>{t('leaves.importHolidays', 'Impor Libur Nasional')}</span>
            </button>
          )}

          {activeTab === 'skills' && (
            <button
              type="button"
              onClick={() => setIsSkillsModalOpen(true)}
              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-medium shadow-xs transition-colors flex items-center gap-1.5"
            >
              <span>⭐</span>
              <span>{t('skills.manageMySkills', 'Kelola Keahlian Saya')}</span>
            </button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 gap-6 text-xs">
        <button
          type="button"
          onClick={() => setActiveTab('leaves')}
          className={`pb-2.5 font-bold transition-colors flex items-center gap-2 ${
            activeTab === 'leaves'
              ? 'border-b-2 border-slate-900 text-slate-900'
              : 'text-slate-500 hover:text-slate-900'
          }`}
        >
          <span>{t('leaves.tabLeaves', 'Cuti Anggota Tim')}</span>
          <span className="text-[10px] font-mono px-1.5 py-0.2 rounded-full bg-slate-100 text-slate-600">
            {leaves.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('holidays')}
          className={`pb-2.5 font-bold transition-colors flex items-center gap-2 ${
            activeTab === 'holidays'
              ? 'border-b-2 border-slate-900 text-slate-900'
              : 'text-slate-500 hover:text-slate-900'
          }`}
        >
          <span>{t('leaves.tabHolidays', 'Libur Nasional & Perusahaan')}</span>
          <span className="text-[10px] font-mono px-1.5 py-0.2 rounded-full bg-slate-100 text-slate-600">
            {holidays.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('skills')}
          className={`pb-2.5 font-bold transition-colors flex items-center gap-2 ${
            activeTab === 'skills'
              ? 'border-b-2 border-slate-900 text-slate-900'
              : 'text-slate-500 hover:text-slate-900'
          }`}
        >
          <span>{t('leaves.tabSkills', 'Matriks Keahlian (Skill Matrix)')}</span>
          <span className="text-[10px] font-mono px-1.5 py-0.2 rounded-full bg-slate-100 text-slate-600">
            {skillsDirectory.length}
          </span>
        </button>
      </div>

      {/* Leaves View */}
      {activeTab === 'leaves' && (
        <div className="space-y-4">
          {/* Leave Balances Banner */}
          {leaveBalance && (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="bg-white border border-slate-200 rounded-lg p-3 shadow-xs">
                <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                  {t('leaves.totalAllowance', 'Jatah Cuti Tahunan')} ({leaveBalance.year})
                </span>
                <div className="text-xl font-mono font-bold text-slate-900 mt-1">
                  {leaveBalance.totalAllowanceDays} <span className="text-xs font-normal text-slate-500">{t('leaves.days', 'hari')}</span>
                </div>
              </div>
              <div className="bg-white border border-slate-200 rounded-lg p-3 shadow-xs">
                <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                  {t('leaves.usedDays', 'Cuti Disetujui / Terpakai')}
                </span>
                <div className="text-xl font-mono font-bold text-amber-600 mt-1">
                  {leaveBalance.usedDays} <span className="text-xs font-normal text-slate-500">{t('leaves.days', 'hari')}</span>
                </div>
              </div>
              <div className="bg-white border border-slate-200 rounded-lg p-3 shadow-xs">
                <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                  {t('leaves.remainingDays', 'Sisa Cuti Tersedia')}
                </span>
                <div className="text-xl font-mono font-bold text-emerald-600 mt-1">
                  {leaveBalance.remainingDays} <span className="text-xs font-normal text-slate-500">{t('leaves.days', 'hari')}</span>
                </div>
              </div>
            </div>
          )}

          <div className="bg-white border border-slate-200 rounded-lg shadow-xs overflow-hidden">
            {isLoadingLeaves ? (
              <div className="p-12 text-center text-slate-400 text-xs">{t('common.loading', 'Memuat data cuti...')}</div>
            ) : leaves.length === 0 ? (
              <div className="p-12 text-center text-slate-500 text-xs">
                {t('leaves.noLeavesRecorded', 'Belum ada cuti yang dicatat.')}
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-semibold">
                    <tr>
                      <th className="p-3 pl-4">{t('leaves.nameCol', 'Nama Anggota')}</th>
                      <th className="p-3">{t('leaves.typeCol', 'Tipe Cuti')}</th>
                      <th className="p-3">{t('leaves.portionCol', 'Porsi')}</th>
                      <th className="p-3">{t('leaves.rangeCol', 'Rentang Tanggal')}</th>
                      <th className="p-3">{t('leaves.statusCol', 'Status')}</th>
                      <th className="p-3">{t('leaves.notesCol', 'Keterangan')}</th>
                      <th className="p-3 pr-4 text-right">{t('common.actions', 'Aksi')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-mono text-xs">
                    {leaves.map((l) => {
                      const canDelete = isSupervisorOrAdmin || currentUser?.userId === l.userId;
                      const isPending = l.status === 'PENDING';
                      return (
                        <tr key={l.id} className="hover:bg-slate-50/60 transition-colors">
                          <td className="p-3 pl-4 font-sans font-medium text-slate-900">
                            {l.fullName || l.userEmail}
                          </td>
                          <td className="p-3">
                            <span className="font-semibold text-slate-800">{l.leaveType}</span>
                          </td>
                          <td className="p-3 font-sans">
                            {l.portion === 'FULL' ? (
                              <span className="text-slate-700">{t('leaves.portionFullLabel', '1 Hari Penuh')}</span>
                            ) : (
                              <span className="text-amber-700 font-medium">
                                {t('leaves.portionHalfLabel', 'Setengah Hari ({{portion}})', { portion: l.portion })}
                              </span>
                            )}
                          </td>
                          <td className="p-3">
                            {l.startDate === l.endDate ? (
                              <span>{l.startDate}</span>
                            ) : (
                              <span>
                                {l.startDate} s/d {l.endDate}
                              </span>
                            )}
                          </td>
                          <td className="p-3 font-sans">
                            {l.status === 'APPROVED' && (
                              <span className="px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800 text-[10px] font-semibold">
                                {t('leaves.statusApproved', 'Disetujui')}
                              </span>
                            )}
                            {l.status === 'PENDING' && (
                              <span className="px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 text-[10px] font-semibold">
                                {t('leaves.statusPending', 'Menunggu Persetujuan')}
                              </span>
                            )}
                            {l.status === 'REJECTED' && (
                              <span className="px-1.5 py-0.5 rounded bg-rose-100 text-rose-800 text-[10px] font-semibold">
                                {t('leaves.statusRejected', 'Ditolak')}
                              </span>
                            )}
                          </td>
                          <td className="p-3 font-sans text-slate-500 max-w-xs truncate">
                            {l.notes || '—'}
                          </td>
                          <td className="p-3 pr-4 text-right font-sans">
                            <div className="flex items-center justify-end gap-1.5">
                              {isPending && isSupervisorOrAdmin && (
                                <>
                                  <button
                                    type="button"
                                    onClick={() => handleApproveLeave(l.id)}
                                    disabled={approveLeaveMutation.isPending}
                                    className="px-2 py-0.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded text-[11px] font-medium"
                                  >
                                    {t('leaves.approve', 'Setujui')}
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => handleRejectLeave(l.id)}
                                    disabled={rejectLeaveMutation.isPending}
                                    className="px-2 py-0.5 bg-rose-600 hover:bg-rose-700 text-white rounded text-[11px] font-medium"
                                  >
                                    {t('leaves.reject', 'Tolak')}
                                  </button>
                                </>
                              )}
                              {canDelete && (
                                <button
                                  type="button"
                                  onClick={() => handleDeleteLeave(l.id)}
                                  className="text-rose-600 hover:text-rose-800 hover:bg-rose-50 px-2 py-1 rounded text-xs"
                                >
                                  {t('common.delete', 'Hapus')}
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Holidays View */}
      {activeTab === 'holidays' && (
        <div className="space-y-3">
          {/* Year Filter */}
          <div className="flex items-center gap-2 text-xs">
            <span className="font-semibold text-slate-700">{t('leaves.filterYear', 'Tahun Kalender:')}</span>
            <div className="flex gap-1">
              {[2025, 2026, 2027].map((year) => (
                <button
                  key={year}
                  type="button"
                  onClick={() => setSelectedYear(year)}
                  className={`px-3 py-1 rounded border text-xs font-mono font-medium ${
                    selectedYear === year
                      ? 'bg-slate-900 text-white border-slate-900'
                      : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  {year}
                </button>
              ))}
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-lg shadow-xs overflow-hidden">
            {isLoadingHolidays ? (
              <div className="p-12 text-center text-slate-400 text-xs">
                {t('common.loading', 'Memuat daftar hari libur...')}
              </div>
            ) : holidays.length === 0 ? (
              <div className="p-12 text-center text-slate-500 text-xs">
                {t('leaves.noHolidays', 'Belum ada hari libur untuk tahun ini.')}{' '}
                {isSupervisorOrAdmin && (
                  <button
                    type="button"
                    onClick={() => setIsHolidayModalOpen(true)}
                    className="text-blue-600 underline font-semibold ml-1"
                  >
                    {t('leaves.importNow', 'Impor hari libur 2026 sekarang')}
                  </button>
                )}
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-semibold">
                    <tr>
                      <th className="p-3 pl-4">{t('leaves.dateCol', 'Tanggal')}</th>
                      <th className="p-3">{t('leaves.holidayNameCol', 'Nama Hari Libur')}</th>
                      <th className="p-3">{t('leaves.scopeCol', 'Cakupan')}</th>
                      {isSupervisorOrAdmin && (
                        <th className="p-3 pr-4 text-right">{t('common.actions', 'Aksi')}</th>
                      )}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-mono text-xs">
                    {holidays.map((h) => (
                      <tr key={h.id} className="hover:bg-slate-50/60 transition-colors">
                        <td className="p-3 pl-4 font-bold text-slate-900">{h.holidayDate}</td>
                        <td className="p-3 font-sans font-medium text-slate-800">{h.name}</td>
                        <td className="p-3">
                          <span
                            className={`px-1.5 py-0.5 rounded text-[10px] font-semibold ${
                              h.scope === 'NATIONAL'
                                ? 'bg-rose-100 text-rose-800'
                                : 'bg-blue-100 text-blue-800'
                            }`}
                          >
                            {h.scope}
                          </span>
                        </td>
                        {isSupervisorOrAdmin && (
                          <td className="p-3 pr-4 text-right font-sans">
                            <button
                              type="button"
                              onClick={() => handleDeleteHoliday(h.id)}
                              className="text-rose-600 hover:text-rose-800 hover:bg-rose-50 px-2 py-1 rounded text-xs"
                            >
                              {t('common.delete', 'Hapus')}
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Skills Directory View */}
      {activeTab === 'skills' && (
        <div className="space-y-3">
          <div className="flex items-center gap-3">
            <input
              type="text"
              placeholder={t('skills.searchPlaceholder', 'Cari keahlian atau anggota tim...')}
              value={skillSearchQuery}
              onChange={(e) => setSkillSearchQuery(e.target.value)}
              className="px-3 py-1.5 border border-slate-300 rounded text-xs w-72 focus:ring-1 focus:ring-slate-900 focus:outline-hidden"
            />
          </div>

          <div className="bg-white border border-slate-200 rounded-lg shadow-xs overflow-hidden">
            {isLoadingSkills ? (
              <div className="p-12 text-center text-slate-400 text-xs">
                {t('common.loading', 'Memuat data matriks keahlian...')}
              </div>
            ) : filteredSkills.length === 0 ? (
              <div className="p-12 text-center text-slate-500 text-xs">
                {t('skills.noSkillsFound', 'Tidak ada data keahlian yang ditemukan.')}
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-semibold">
                    <tr>
                      <th className="p-3 pl-4">{t('skills.colMember', 'Nama Anggota')}</th>
                      <th className="p-3">{t('skills.colEmail', 'Email')}</th>
                      <th className="p-3">{t('skills.colSkill', 'Keahlian')}</th>
                      <th className="p-3">{t('skills.colLevel', 'Tingkat Kemahiran')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-xs">
                    {filteredSkills.map((s) => (
                      <tr key={s.id} className="hover:bg-slate-50/60 transition-colors">
                        <td className="p-3 pl-4 font-medium text-slate-900">{s.userFullName}</td>
                        <td className="p-3 font-mono text-slate-500">{s.userEmail}</td>
                        <td className="p-3 font-bold text-slate-800">{s.skillName}</td>
                        <td className="p-3">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                              s.proficiencyLevel === 'EXPERT'
                                ? 'bg-purple-100 text-purple-800'
                                : s.proficiencyLevel === 'ADVANCED'
                                ? 'bg-blue-100 text-blue-800'
                                : s.proficiencyLevel === 'INTERMEDIATE'
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {s.proficiencyLevel}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Modals */}
      <LeaveFormModal isOpen={isLeaveModalOpen} onClose={() => setIsLeaveModalOpen(false)} />
      <HolidayImportModal isOpen={isHolidayModalOpen} onClose={() => setIsHolidayModalOpen(false)} />
      {currentUser && (
        <UserSkillsModal
          isOpen={isSkillsModalOpen}
          onClose={() => setIsSkillsModalOpen(false)}
          userId={currentUser.userId}
          currentSkills={mySkills}
        />
      )}
    </div>
  );
};
