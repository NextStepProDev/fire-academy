import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import commonPl from './locales/pl/common.json'
import authPl from './locales/pl/auth.json'
import settingsPl from './locales/pl/settings.json'
import errorsPl from './locales/pl/errors.json'

import commonEn from './locales/en/common.json'
import authEn from './locales/en/auth.json'
import settingsEn from './locales/en/settings.json'
import errorsEn from './locales/en/errors.json'

import commonEs from './locales/es/common.json'
import authEs from './locales/es/auth.json'
import settingsEs from './locales/es/settings.json'
import errorsEs from './locales/es/errors.json'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      pl: { common: commonPl, auth: authPl, settings: settingsPl, errors: errorsPl },
      en: { common: commonEn, auth: authEn, settings: settingsEn, errors: errorsEn },
      es: { common: commonEs, auth: authEs, settings: settingsEs, errors: errorsEs },
    },
    fallbackLng: 'en',
    defaultNS: 'common',
    ns: ['common', 'auth', 'settings', 'errors'],
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'i18nextLng',
    },
  })

export default i18n
