import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/httpClient';
import { useCurrentUser } from '../auth/useCurrentUser';

interface SystemSettings {
  underutilizedThresholdPct: number;
  overloadedThresholdPct: number;
  workTimezone: string;
  teamsWebhookUrl?: string | null;
  aiRecommendationsEnabled?: boolean;
}

interface CsvImportResult {
  importedCount: number;
  projectsCreated: number;
  tasksCreated: number;
}

export const ThresholdsAndImportSection: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();
  const isAdmin = currentUser?.role === 'ADMIN';
  const isSupervisorOrAdmin = currentUser?.role === 'ADMIN' || currentUser?.role === 'SUPERVISOR';

  // --- System Thresholds Section ---
  const { data: settings, isLoading: loadingSettings } = useQuery<SystemSettings>({
    queryKey: ['systemSettings'],
    queryFn: async () => {
      return apiClient.get<SystemSettings>('/api/v1/settings');
    },
  });

  const [underutilized, setUnderutilized] = useState<number>(60);
  const [overloaded, setOverloaded] = useState<number>(100);
  const [timezone, setTimezone] = useState<string>('Asia/Jakarta');
  const [teamsWebhookUrl, setTeamsWebhookUrl] = useState<string>('');
  const [aiEnabled, setAiEnabled] = useState<boolean>(true);
  const [testStatus, setTestStatus] = useState<string | null>(null);
  const [settingsMsg, setSettingsMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    if (settings) {
      setUnderutilized(settings.underutilizedThresholdPct);
      setOverloaded(settings.overloadedThresholdPct);
      setTimezone(settings.workTimezone);
      setTeamsWebhookUrl(settings.teamsWebhookUrl || '');
      setAiEnabled(settings.aiRecommendationsEnabled !== false);
    }
  }, [settings]);

  const updateSettingsMutation = useMutation({
    mutationFn: async (payload: SystemSettings) => {
      return apiClient.put<SystemSettings>('/api/v1/settings', payload);
    },
    onSuccess: (data) => {
      queryClient.setQueryData(['systemSettings'], data);
      queryClient.invalidateQueries({ queryKey: ['heatmap'] });
      setSettingsMsg({ type: 'success', text: t('settings.system.saveSuccess') });
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.detail || err?.message || t('settings.system.saveError');
      setSettingsMsg({ type: 'error', text: msg });
    },
  });

  const testWebhookMutation = useMutation({
    mutationFn: (webhookUrl: string) =>
      apiClient.post<{ success: boolean; message: string }>(
        '/api/v1/notifications/test-teams-webhook',
        { webhookUrl }
      ),
    onSuccess: (data) => {
      setTestStatus(
        data.success
          ? t('settings.teams.testSuccess', 'Berhasil mengirim kartu uji coba ke Microsoft Teams!')
          : t('settings.teams.testFailed', 'Gagal mengirim webhook. Periksa konfigurasi URL.')
      );
    },
    onError: () => {
      setTestStatus(t('settings.teams.testFailed', 'Gagal menghubungi webhook Teams.'));
    },
  });

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault();
    setSettingsMsg(null);
    if (underutilized >= overloaded) {
      setSettingsMsg({
        type: 'error',
        text: 'Ambang batas kurang beban harus lebih kecil dari ambang batas kelebihan beban.',
      });
      return;
    }
    updateSettingsMutation.mutate({
      underutilizedThresholdPct: Number(underutilized),
      overloadedThresholdPct: Number(overloaded),
      workTimezone: timezone,
      teamsWebhookUrl: teamsWebhookUrl.trim() || null,
      aiRecommendationsEnabled: aiEnabled,
    });
  };

  const handleTestWebhook = () => {
    setTestStatus(null);
    testWebhookMutation.mutate(teamsWebhookUrl.trim());
  };

  // --- CSV Onboarding Section ---
  const [csvContent, setCsvContent] = useState<string>('');
  const [csvFileName, setCsvFileName] = useState<string>('');
  const [importResult, setImportResult] = useState<CsvImportResult | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  const importMutation = useMutation({
    mutationFn: async (content: string) => {
      return apiClient.post<CsvImportResult>('/api/v1/allocations/import-csv', content, {
        headers: {
          'Content-Type': 'text/csv',
        },
      });
    },
    onSuccess: (res) => {
      setImportResult(res);
      setImportError(null);
      setCsvContent('');
      setCsvFileName('');
      queryClient.invalidateQueries({ queryKey: ['heatmap'] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      queryClient.invalidateQueries({ queryKey: ['allocations'] });
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.detail || err?.message || t('settings.import.error');
      setImportError(msg);
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setCsvFileName(file.name);
      const reader = new FileReader();
      reader.onload = (event) => {
        const text = event.target?.result as string;
        setCsvContent(text || '');
      };
      reader.readAsText(file);
    }
  };

  const handleProcessImport = (e: React.FormEvent) => {
    e.preventDefault();
    if (!csvContent.trim()) return;
    setImportError(null);
    setImportResult(null);
    importMutation.mutate(csvContent);
  };

  return (
    <div className="space-y-6">
      {/* 1. Thresholds Configuration */}
      <div className="bg-white border border-slate-200 rounded-lg p-4 space-y-4">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">
            {t('settings.system.thresholdTitle')}
          </h2>
          <p className="text-xs text-slate-500">
            {t('settings.system.thresholdSubtitle')}
          </p>
        </div>

        {/* Live Threshold Display Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="p-3 border border-slate-200 rounded bg-slate-50 space-y-1">
            <span className="text-[11px] font-medium text-slate-500">
              {t('settings.system.optimalRange')}
            </span>
            <div className="text-sm font-semibold text-emerald-700 font-mono">
              {underutilized}% – {overloaded}%
            </div>
          </div>
          <div className="p-3 border border-slate-200 rounded bg-slate-50 space-y-1">
            <span className="text-[11px] font-medium text-slate-500">
              {t('settings.system.overloadedThreshold')}
            </span>
            <div className="text-sm font-semibold text-rose-600 font-mono">
              &gt; {overloaded}%
            </div>
          </div>
        </div>

        {/* Threshold Edit Form */}
        <form onSubmit={handleSaveSettings} className="space-y-3 pt-2">
          {!isAdmin && (
            <div className="p-2.5 bg-amber-50 border border-amber-200 rounded text-amber-800 text-xs">
              {t('settings.system.adminOnly')}
            </div>
          )}

          {settingsMsg && (
            <div
              className={`p-2.5 rounded text-xs border ${
                settingsMsg.type === 'success'
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                  : 'bg-rose-50 border-rose-200 text-rose-800'
              }`}
            >
              {settingsMsg.text}
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-[11px] font-medium text-slate-600 mb-1">
                {t('settings.system.underutilizedPctLabel')}
              </label>
              <input
                type="number"
                step="0.1"
                min="0"
                max="100"
                value={underutilized}
                onChange={(e) => setUnderutilized(Number(e.target.value))}
                disabled={!isAdmin || loadingSettings}
                className="w-full text-xs font-mono border border-slate-300 rounded px-2.5 py-1.5 focus:outline-hidden focus:ring-1 focus:ring-slate-400 disabled:bg-slate-100 disabled:text-slate-500"
                required
              />
            </div>

            <div>
              <label className="block text-[11px] font-medium text-slate-600 mb-1">
                {t('settings.system.overloadedPctLabel')}
              </label>
              <input
                type="number"
                step="0.1"
                min="1"
                max="500"
                value={overloaded}
                onChange={(e) => setOverloaded(Number(e.target.value))}
                disabled={!isAdmin || loadingSettings}
                className="w-full text-xs font-mono border border-slate-300 rounded px-2.5 py-1.5 focus:outline-hidden focus:ring-1 focus:ring-slate-400 disabled:bg-slate-100 disabled:text-slate-500"
                required
              />
            </div>

            <div>
              <label className="block text-[11px] font-medium text-slate-600 mb-1">
                {t('settings.system.timezoneLabel')}
              </label>
              <input
                type="text"
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                disabled={!isAdmin || loadingSettings}
                className="w-full text-xs font-mono border border-slate-300 rounded px-2.5 py-1.5 focus:outline-hidden focus:ring-1 focus:ring-slate-400 disabled:bg-slate-100 disabled:text-slate-500"
                required
              />
            </div>
          </div>

          {/* Microsoft Teams Webhook URL Integration */}
          <div className="pt-2 border-t border-slate-100 space-y-1.5">
            <label className="block text-[11px] font-medium text-slate-700">
              {t('settings.teams.webhookUrlLabel', 'Webhook Microsoft Teams (Incoming Webhook URL)')}
            </label>
            <div className="flex gap-2">
              <input
                type="url"
                value={teamsWebhookUrl}
                onChange={(e) => setTeamsWebhookUrl(e.target.value)}
                disabled={!isAdmin || loadingSettings}
                placeholder="https://company.webhook.office.com/webhookb2/..."
                className="flex-1 text-xs font-mono border border-slate-300 rounded px-2.5 py-1.5 focus:outline-hidden focus:ring-1 focus:ring-slate-400 disabled:bg-slate-100 disabled:text-slate-500"
              />
              {isAdmin && (
                <button
                  type="button"
                  onClick={handleTestWebhook}
                  disabled={testWebhookMutation.isPending}
                  className="px-3 py-1.5 rounded border border-slate-300 bg-slate-50 hover:bg-slate-100 text-slate-700 text-xs font-medium transition-colors"
                >
                  {testWebhookMutation.isPending
                    ? t('settings.teams.testing', 'Menguji...')
                    : t('settings.teams.testButton', 'Uji Webhook')}
                </button>
              )}
            </div>
            {testStatus && (
              <p className="text-[11px] text-blue-700 font-medium">{testStatus}</p>
            )}
            <p className="text-[11px] text-slate-400">
              {t(
                'settings.teams.webhookHelp',
                'Notifikasi peringatan beban lebih (>100%), batas waktu task H-2, dan pengingat timesheet Jumat sore akan dikirimkan ke channel Teams melalui URL ini.'
              )}
            </p>
          </div>

          {/* AI Workload Balancing Assistant Toggle */}
          <div className="pt-2 border-t border-slate-100 flex items-start gap-2.5">
            <input
              type="checkbox"
              id="ai-recommendations-toggle"
              checked={aiEnabled}
              onChange={(e) => setAiEnabled(e.target.checked)}
              disabled={!isAdmin || loadingSettings}
              className="mt-0.5 rounded border-slate-300 text-slate-900 focus:ring-slate-900 w-4 h-4 cursor-pointer"
            />
            <label htmlFor="ai-recommendations-toggle" className="text-xs select-none cursor-pointer">
              <span className="font-medium text-slate-800 block">
                {t('settings.system.aiEnabledLabel', 'Asisten Rekomendasi Kapasitas AI')}
              </span>
              <span className="text-[11px] text-slate-500 block">
                {t(
                  'settings.system.aiEnabledHelp',
                  'Aktifkan rekomendasi penugasan cerdas dan saran penyeimbangan beban kerja berbasis keahlian dan ketersediaan waktu luang.'
                )}
              </span>
            </label>
          </div>

          {isAdmin && (
            <div className="pt-2 flex justify-end">
              <button
                type="submit"
                disabled={updateSettingsMutation.isPending}
                className="px-3 py-1.5 rounded bg-slate-900 text-white text-xs font-medium hover:bg-slate-800 disabled:opacity-50 transition-colors cursor-pointer"
              >
                {updateSettingsMutation.isPending ? t('common.saving') : t('common.saveChanges')}
              </button>
            </div>
          )}
        </form>
      </div>

      {/* 2. CSV Onboarding Importer */}
      {isSupervisorOrAdmin && (
        <div className="bg-white border border-slate-200 rounded-lg p-4 space-y-4">
          <div>
            <h2 className="text-sm font-semibold text-slate-900">
              {t('settings.import.title')}
            </h2>
            <p className="text-xs text-slate-500">
              {t('settings.import.subtitle')}
            </p>
          </div>

          <form onSubmit={handleProcessImport} className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                {t('settings.import.fileInput')}
              </label>
              <input
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                className="text-xs file:mr-2 file:py-1 file:px-2.5 file:rounded file:border-0 file:text-xs file:font-medium file:bg-slate-100 file:text-slate-700 hover:file:bg-slate-200"
              />
              {csvFileName && (
                <p className="text-[11px] text-slate-500 mt-1">
                  {t('settings.import.loaded')}: <span className="font-mono">{csvFileName}</span>
                </p>
              )}
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                {t('settings.import.pasteLabel')}
              </label>
              <textarea
                rows={5}
                value={csvContent}
                onChange={(e) => setCsvContent(e.target.value)}
                placeholder="project_code,project_name,task_title,user_email,start_date,end_date,hours_per_day&#10;PRJ-01,Alpha Project,Backend API,budi@toppaload.local,2026-11-02,2026-11-06,6.0"
                className="w-full text-xs font-mono border border-slate-300 rounded p-2 focus:outline-hidden focus:ring-1 focus:ring-slate-400"
              />
            </div>

            {importError && (
              <div className="p-2.5 rounded text-xs bg-rose-50 border border-rose-200 text-rose-800">
                {importError}
              </div>
            )}

            {importResult && (
              <div className="p-2.5 rounded text-xs bg-emerald-50 border border-emerald-200 text-emerald-800 space-y-1">
                <p className="font-semibold">{t('settings.import.success')}</p>
                <ul className="list-disc list-inside text-[11px]">
                  <li>
                    {t('settings.import.allocationsImported')}: {importResult.importedCount}
                  </li>
                  <li>
                    {t('settings.import.projectsCreated')}: {importResult.projectsCreated}
                  </li>
                  <li>
                    {t('settings.import.tasksCreated')}: {importResult.tasksCreated}
                  </li>
                </ul>
              </div>
            )}

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={!csvContent.trim() || importMutation.isPending}
                className="px-3 py-1.5 rounded bg-slate-900 text-white text-xs font-medium hover:bg-slate-800 disabled:opacity-50 transition-colors cursor-pointer"
              >
                {importMutation.isPending ? t('settings.import.processing') : t('settings.import.processButton')}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
