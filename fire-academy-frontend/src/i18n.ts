import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import commonPl from './locales/pl/common.json'
import authPl from './locales/pl/auth.json'
import settingsPl from './locales/pl/settings.json'
import errorsPl from './locales/pl/errors.json'
import eventsPl from './locales/pl/events.json'
import adminPl from './locales/pl/admin.json'
import accountPl from './locales/pl/account.json'
import marketingPl from './locales/pl/marketing.json'

i18n
  .use(initReactI18next)
  .init({
    resources: {
      pl: { common: commonPl, auth: authPl, settings: settingsPl, errors: errorsPl, events: eventsPl, admin: adminPl, account: accountPl, marketing: marketingPl },
    },
    lng: 'pl',
    fallbackLng: 'pl',
    defaultNS: 'common',
    ns: ['common', 'auth', 'settings', 'errors', 'events', 'admin', 'account', 'marketing'],
    interpolation: { escapeValue: false },
  })

export default i18n
