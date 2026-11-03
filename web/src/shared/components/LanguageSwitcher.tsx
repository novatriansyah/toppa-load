import React from 'react';
import { useTranslation } from 'react-i18next';

export const LanguageSwitcher: React.FC<{ className?: string }> = ({ className = '' }) => {
  const { i18n } = useTranslation();
  const currentLang = i18n.language?.startsWith('id') ? 'id' : 'en';

  const toggleLanguage = (lang: 'en' | 'id') => {
    i18n.changeLanguage(lang);
    try {
      localStorage.setItem('toppa_lang', lang);
    } catch {
      // storage unavailable
    }
  };

  return (
    <div
      className={`inline-flex items-center rounded border border-slate-200 bg-slate-100 p-0.5 text-xs font-medium select-none ${className}`}
      role="group"
      aria-label="Language selector"
    >
      <button
        type="button"
        onClick={() => toggleLanguage('en')}
        className={`px-1.5 py-0.5 rounded text-[11px] font-semibold transition-all cursor-pointer ${
          currentLang === 'en'
            ? 'bg-white text-slate-900 shadow-xs'
            : 'text-slate-500 hover:text-slate-800'
        }`}
        title="English"
      >
        EN
      </button>
      <button
        type="button"
        onClick={() => toggleLanguage('id')}
        className={`px-1.5 py-0.5 rounded text-[11px] font-semibold transition-all cursor-pointer ${
          currentLang === 'id'
            ? 'bg-white text-slate-900 shadow-xs'
            : 'text-slate-500 hover:text-slate-800'
        }`}
        title="Bahasa Indonesia"
      >
        ID
      </button>
    </div>
  );
};
