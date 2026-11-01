import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import idLocale from './locales/id.json';
import enLocale from './locales/en.json';

i18n
  .use(initReactI18next)
  .init({
    resources: {
      id: { translation: idLocale },
      en: { translation: enLocale },
    },
    lng: 'id',
    fallbackLng: 'id',
    interpolation: {
      escapeValue: false,
    },
  });

export default i18n;
