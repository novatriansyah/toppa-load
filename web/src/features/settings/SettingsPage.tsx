import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { UsersSettingsSection } from './UsersSettingsSection';
import { ThresholdsAndImportSection } from './ThresholdsAndImportSection';

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
            className={`px-3 py-1 rounded font-medium transition-colors cursor-pointer ${activeTab === 'users'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
              }`}
          >
            {t('settings.tabs.users')}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('system')}
            className={`px-3 py-1 rounded font-medium transition-colors cursor-pointer ${activeTab === 'system'
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
        <ThresholdsAndImportSection />
      )}
    </div>
  );
};

export default SettingsPage;
