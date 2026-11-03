import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';

interface CalendarTokenData {
  id: string;
  userId: string;
  feedUrl: string;
  token: string;
  scope: string;
  createdAt: string;
  lastAccessedAt: string | null;
}

interface CalendarSyncModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CalendarSyncModal: React.FC<CalendarSyncModalProps> = ({ isOpen, onClose }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [activeTab, setActiveTab] = useState<'outlook' | 'google'>('outlook');

  const { data: tokenData, isLoading } = useQuery<CalendarTokenData>({
    queryKey: ['calendar-feed-token'],
    queryFn: () => apiClient.get<CalendarTokenData>('/api/v1/calendar/token'),
    enabled: isOpen,
  });

  const rotateMutation = useMutation({
    mutationFn: () => apiClient.post<CalendarTokenData>('/api/v1/calendar/token/rotate', {}),
    onSuccess: (newData: CalendarTokenData) => {
      queryClient.setQueryData(['calendar-feed-token'], newData);
      setCopied(false);
    },
  });

  if (!isOpen) return null;

  const fullFeedUrl = tokenData?.feedUrl
    ? (tokenData.feedUrl.startsWith('http') ? tokenData.feedUrl : window.location.origin + tokenData.feedUrl)
    : '';

  const handleCopy = () => {
    if (!fullFeedUrl) return;
    navigator.clipboard.writeText(fullFeedUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-xl w-full p-6 space-y-5">
        {/* Modal Header */}
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-indigo-50 text-indigo-600">
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {t('calendar.syncTitle', 'Sinkronisasi Kalender Outlook & Google')}
              </h2>
              <p className="text-xs text-slate-500">
                {t('calendar.syncSubtitle', 'Langganan jadwal proyek & cuti Anda langsung di Microsoft Outlook atau Google Calendar via feed .ics standar RFC 5545.')}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1"
          >
            ✕
          </button>
        </div>

        {/* Feed URL Box */}
        <div className="space-y-2">
          <label className="block text-xs font-semibold text-slate-700">
            {t('calendar.feedUrlLabel', 'Tautan Langganan Kalender Privat (.ics)')}
          </label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              readOnly
              value={isLoading ? t('calendar.loadingUrl', 'Memuat tautan...') : fullFeedUrl}
              className="flex-1 px-3 py-2 text-xs font-mono bg-slate-50 border border-slate-300 rounded-lg text-slate-800 select-all"
            />
            <button
              onClick={handleCopy}
              disabled={isLoading || !fullFeedUrl}
              className="px-3.5 py-2 text-xs font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 transition-colors shadow-xs"
            >
              {copied ? t('calendar.copied', '✓ Tersalin!') : t('calendar.copyUrl', 'Salin URL')}
            </button>
          </div>
          <div className="flex items-center justify-between text-2xs text-slate-400 pt-1">
            <span>
              {tokenData?.lastAccessedAt
                ? t('calendar.lastAccessed', {
                  time: new Date(tokenData.lastAccessedAt).toLocaleString(),
                  defaultValue: `Terakhir diakses: ${new Date(tokenData.lastAccessedAt).toLocaleString()}`,
                })
                : t('calendar.neverAccessed', 'Belum pernah diakses')}
            </span>
            <button
              onClick={() => {
                if (confirm(t('calendar.rotateConfirm', 'Yakin ingin merotasi tautan? Kalender Outlook/Google lama Anda harus diperbarui dengan tautan baru.'))) {
                  rotateMutation.mutate();
                }
              }}
              disabled={rotateMutation.isPending}
              className="text-rose-600 hover:underline disabled:opacity-50"
            >
              {rotateMutation.isPending ? t('calendar.rotating', 'Merotasi...') : t('calendar.rotateUrl', 'Rotasi Tautan (Reset)')}
            </button>
          </div>
        </div>

        {/* Step-by-Step Platform Tabs */}
        <div className="border border-slate-200 rounded-xl overflow-hidden">
          <div className="flex border-b border-slate-200 bg-slate-50">
            <button
              onClick={() => setActiveTab('outlook')}
              className={`flex-1 py-2.5 text-xs font-semibold flex items-center justify-center gap-2 border-b-2 transition-all ${activeTab === 'outlook'
                ? 'border-indigo-600 text-indigo-600 bg-white'
                : 'border-transparent text-slate-500 hover:text-slate-700'
                }`}
            >
              <svg className="w-4 h-4 text-sky-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 14H6v-2h6v2zm4-4H6v-2h10v2zm0-4H6V7h10v2z" />
              </svg>
              Microsoft Outlook / 365
            </button>
            <button
              onClick={() => setActiveTab('google')}
              className={`flex-1 py-2.5 text-xs font-semibold flex items-center justify-center gap-2 border-b-2 transition-all ${activeTab === 'google'
                ? 'border-indigo-600 text-indigo-600 bg-white'
                : 'border-transparent text-slate-500 hover:text-slate-700'
                }`}
            >
              <svg className="w-4 h-4 text-emerald-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2z" />
              </svg>
              Google Calendar
            </button>
          </div>

          <div className="p-4 text-xs text-slate-600 space-y-2 bg-white">
            {activeTab === 'outlook' ? (
              <ol className="list-decimal list-inside space-y-1.5 leading-relaxed">
                <li>{t('calendar.outlookStep1', 'Buka Outlook Calendar (aplikasi desktop atau outlook.office.com).')}</li>
                <li>{t('calendar.outlookStep2', 'Di panel kiri, klik tombol "Add calendar" (Tambah kalender).')}</li>
                <li>{t('calendar.outlookStep3', 'Pilih opsi "Subscribe from web" (Langganan dari web).')}</li>
                <li>{t('calendar.outlookStep4', 'Tempelkan tautan URL di atas, beri nama kalender "Toppa Load", dan klik Import.')}</li>
              </ol>
            ) : (
              <ol className="list-decimal list-inside space-y-1.5 leading-relaxed">
                <li>{t('calendar.googleStep1', 'Buka calendar.google.com di browser Anda.')}</li>
                <li>{t('calendar.googleStep2', 'Pada bagian "Other calendars" (Kalender lainnya) di samping kiri, klik ikon tanda +.')}</li>
                <li>{t('calendar.googleStep3', 'Pilih "From URL" (Dari URL).')}</li>
                <li>{t('calendar.googleStep4', 'Tempelkan tautan URL di atas dan klik Add calendar.')}</li>
              </ol>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="flex justify-end pt-1">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold rounded-lg bg-slate-100 text-slate-700 hover:bg-slate-200 transition-colors"
          >
            {t('common.close', 'Tutup')}
          </button>
        </div>
      </div>
    </div>
  );
};
export default CalendarSyncModal;

