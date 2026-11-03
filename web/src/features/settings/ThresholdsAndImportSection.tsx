import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/httpClient';
import { useCurrentUser } from '../auth/useCurrentUser';

interface SystemSettings {
  underutilizedThresholdPct: number;
  overloadedThresholdPct: number;
  workTimezone: string;
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
  const [settingsMsg, setSettingsMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    if (settings) {
      setUnderutilized(settings.underutilizedThresholdPct);
      setOverloaded(settings.overloadedThresholdPct);
      setTimezone(settings.workTimezone);
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
    });
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
      setImportResult(null);
      const detail = err?.response?.data?.detail || err?.message || t('settings.system.importError');
      setImportError(detail);
    },
  });

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
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
                className="w-full text-xs font-mono border border-slate-300 rounded px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-slate-400 disabled:bg-slate-100 disabled:text-slate-500"
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
                className="w-full text-xs font-mono border border-slate-300 rounded px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-slate-400 disabled:bg-slate-100 disabled:text-slate-500"
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
                className="w-full text-xs font-mono border border-slate-300 rounded px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-slate-400 disabled:bg-slate-100 disabled:text-slate-500"
                required
              />
            </div>
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
              {t('settings.system.importTitle')}
            </h2>
            <p className="text-xs text-slate-500">
              {t('settings.system.importSubtitle')}
            </p>
          </div>

          <div className="p-2.5 bg-slate-50 border border-slate-200 rounded text-[11px] font-mono text-slate-600 break-all">
            {t('settings.system.formatHint')}
          </div>

          {importResult && (
            <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded text-xs">
              {t('settings.system.importSuccess', {
                count: importResult.importedCount,
                projects: importResult.projectsCreated,
                tasks: importResult.tasksCreated,
              })}
            </div>
          )}

          {importError && (
            <div className="p-3 bg-rose-50 border border-rose-200 text-rose-800 rounded text-xs whitespace-pre-wrap">
              {importError}
            </div>
          )}

          <form onSubmit={handleProcessImport} className="space-y-3">
            <div className="flex items-center space-x-3">
              <label className="cursor-pointer px-3 py-1.5 border border-slate-300 rounded text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors">
                {t('settings.system.chooseFile')}
                <input
                  type="file"
                  accept=".csv,text/csv"
                  onChange={handleFileUpload}
                  className="hidden"
                />
              </label>
              {csvFileName && (
                <span className="text-xs text-slate-600 font-mono truncate">{csvFileName}</span>
              )}
            </div>

            <div>
              <textarea
                rows={5}
                value={csvContent}
                onChange={(e) => setCsvContent(e.target.value)}
                placeholder={t('settings.system.csvPlaceholder')}
                className="w-full text-xs font-mono border border-slate-300 rounded p-2 focus:outline-none focus:ring-1 focus:ring-slate-400"
              />
            </div>

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={!csvContent.trim() || importMutation.isPending}
                className="px-3 py-1.5 rounded bg-blue-600 text-white text-xs font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors cursor-pointer"
              >
                {importMutation.isPending ? t('common.loading') : t('settings.system.processImport')}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

