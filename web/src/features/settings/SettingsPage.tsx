import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { UsersSettingsSection } from './UsersSettingsSection';

export const SettingsPage: React.FC = () => {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<'users' | 'system'>('users');

  return (
    <div className="space-y-4 max-w-5xl mx-auto">
      {/* Page Title */}
      <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
        <div>
          <h1 className="text-base font-semibold text-slate-900">
            {t('settings.pageTitle')}
          </h1>
          <p className="text-xs text-slate-500">
            {t('settings.pageSubtitle')}
          </p>
        </div>

        {/* Tab switch */}
        <div className="flex rounded bg-slate-100 p-0.5 border border-slate-200 text-xs">
          <button
            type="button"
            onClick={() => setActiveTab('users')}
            className={`px-3 py-1 rounded font-medium transition-colors ${
              activeTab === 'users'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {t('settings.tabs.users')}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('system')}
            className={`px-3 py-1 rounded font-medium transition-colors ${
              activeTab === 'system'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {t('settings.tabs.system')}
          </button>
        </div>
      </div>

      {/* Tab Contents */}
      {activeTab === 'users' ? (
        <UsersSettingsSection />
      ) : (
        <div className="bg-white border border-slate-200 rounded p-4 text-xs text-slate-600 space-y-3">
          <h3 className="font-semibold text-slate-900">{t('settings.system.thresholdTitle')}</h3>
          <p className="text-slate-500">{t('settings.system.thresholdSubtitle')}</p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2">
            <div className="p-3 border border-slate-200 rounded bg-slate-50 space-y-1">
              <span className="text-[11px] font-medium text-slate-500">{t('settings.system.optimalRange')}</span>
              <div className="text-sm font-semibold text-slate-800 font-mono">80% – 100%</div>
            </div>
            <div className="p-3 border border-slate-200 rounded bg-slate-50 space-y-1">
              <span className="text-[11px] font-medium text-slate-500">{t('settings.system.overloadedThreshold')}</span>
              <div className="text-sm font-semibold text-rose-600 font-mono">&gt; 100%</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SettingsPage;
