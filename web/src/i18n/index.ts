import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import idLocale from './locales/id.json';
import enLocale from './locales/en.json';

const getInitialLanguage = (): string => {
  try {
    const saved = localStorage.getItem('toppa_lang');
    if (saved === 'id' || saved === 'en') {
      return saved;
    }
  } catch {
    // Graceful fallback for non-browser or sandboxed environments
  }
  return 'en'; // Default to English for multinational enterprise standard
};

i18n
  .use(initReactI18next)
  .init({
    resources: {
      id: { translation: idLocale },
      en: { translation: enLocale },
    },
    lng: getInitialLanguage(),
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false,
    },
  });

export default i18n;
