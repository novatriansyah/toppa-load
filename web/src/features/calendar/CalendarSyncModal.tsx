import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

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
    queryFn: async () => {
      const res = await fetch('/api/v1/calendar/token', { credentials: 'include' });
      if (!res.ok) throw new Error('Gagal memuat token kalender');
      return res.json();
    },
    enabled: isOpen,
  });

  const rotateMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/v1/calendar/token/rotate', {
        method: 'POST',
        credentials: 'include',
      });
      if (!res.ok) throw new Error('Gagal merotasi tautan kalender');
      return res.json();
    },
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
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-2xl max-w-xl w-full p-6 space-y-5">
        {/* Modal Header */}
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white">
                {t('calendar.syncTitle', 'Sinkronisasi Kalender Outlook & Google')}
              </h2>
              <p className="text-xs text-slate-500">
                {t('calendar.syncSubtitle', 'Langganan jadwal proyek & cuti Anda langsung di Microsoft Outlook atau Google Calendar via feed .ics standar RFC 5545.')}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1"
          >
            ✕
          </button>
        </div>

        {/* Feed URL Box */}
        <div className="space-y-2">
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            {t('calendar.feedUrlLabel', 'Tautan Langganan Kalender Privat (.ics)')}
          </label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              readOnly
              value={isLoading ? 'Memuat tautan...' : fullFeedUrl}
              className="flex-1 px-3 py-2 text-xs font-mono bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg text-slate-800 dark:text-slate-200 select-all"
            />
            <button
              onClick={handleCopy}
              disabled={isLoading || !fullFeedUrl}
              className="px-3.5 py-2 text-xs font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 transition-colors shadow-xs"
            >
              {copied ? '✓ Tersalin!' : 'Salin URL'}
            </button>
          </div>
          <div className="flex items-center justify-between text-2xs text-slate-400 pt-1">
            <span>
              {tokenData?.lastAccessedAt
                ? `Terakhir diakses oleh kalender: ${new Date(tokenData.lastAccessedAt).toLocaleString('id-ID')}`
                : 'Belum pernah diakses'}
            </span>
            <button
              onClick={() => {
                if (confirm('Yakin ingin merotasi tautan? Kalender Outlook/Google lama Anda harus diperbarui dengan tautan baru.')) {
                  rotateMutation.mutate();
                }
              }}
              disabled={rotateMutation.isPending}
              className="text-rose-600 hover:underline disabled:opacity-50"
            >
              {rotateMutation.isPending ? 'Merotasi...' : 'Rotasi Tautan (Reset)'}
            </button>
          </div>
        </div>

        {/* Step-by-Step Platform Tabs */}
        <div className="border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden">
          <div className="flex border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50">
            <button
              onClick={() => setActiveTab('outlook')}
              className={`flex-1 py-2.5 text-xs font-semibold flex items-center justify-center gap-2 border-b-2 transition-all ${
                activeTab === 'outlook'
                  ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400 bg-white dark:bg-slate-900'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              <svg className="w-4 h-4 text-sky-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 14H6v-2h6v2zm4-4H6v-2h10v2zm0-4H6V7h10v2z"/>
              </svg>
              Microsoft Outlook / 365
            </button>
            <button
              onClick={() => setActiveTab('google')}
              className={`flex-1 py-2.5 text-xs font-semibold flex items-center justify-center gap-2 border-b-2 transition-all ${
                activeTab === 'google'
                  ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400 bg-white dark:bg-slate-900'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              <svg className="w-4 h-4 text-emerald-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2z"/>
              </svg>
              Google Calendar
            </button>
          </div>

          <div className="p-4 text-xs text-slate-600 dark:text-slate-300 space-y-2 bg-white dark:bg-slate-900">
            {activeTab === 'outlook' ? (
              <ol className="list-decimal list-inside space-y-1.5 leading-relaxed">
                <li>Buka <strong>Outlook Calendar</strong> (aplikasi desktop atau <a href="https://outlook.office.com/calendar" target="_blank" rel="noreferrer" className="text-indigo-600 underline">outlook.office.com</a>).</li>
                <li>Di panel kiri, klik tombol <strong>"Add calendar" (Tambah kalender)</strong>.</li>
                <li>Pilih opsi <strong>"Subscribe from web" (Langganan dari web)</strong>.</li>
                <li>Tempelkan tautan URL di atas, beri nama kalender <em>"Toppa Load"</em>, dan klik <strong>Import</strong>.</li>
              </ol>
            ) : (
              <ol className="list-decimal list-inside space-y-1.5 leading-relaxed">
                <li>Buka <a href="https://calendar.google.com" target="_blank" rel="noreferrer" className="text-indigo-600 underline">calendar.google.com</a> di browser Anda.</li>
                <li>Pada bagian <strong>"Other calendars" (Kalender lainnya)</strong> di samping kiri, klik ikon tanda <strong>+</strong>.</li>
                <li>Pilih <strong>"From URL" (Dari URL)</strong>.</li>
                <li>Tempelkan tautan URL di atas dan klik <strong>Add calendar</strong>.</li>
              </ol>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="flex justify-end pt-1">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold rounded-lg bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-750 transition-colors"
          >
            {t('common.close', 'Tutup')}
          </button>
        </div>
      </div>
    </div>
  );
};
export default CalendarSyncModal;

