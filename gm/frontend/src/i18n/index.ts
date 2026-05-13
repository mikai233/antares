import en from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { createI18n } from 'vue-i18n'
import { computed } from 'vue'
import { enUS, zhCN } from './messages'

export type GmLocale = 'zh-CN' | 'en-US'

const STORAGE_KEY = 'gm-locale'
const supportedLocales: GmLocale[] = ['zh-CN', 'en-US']

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  missingWarn: false,
  fallbackWarn: false,
  locale: readInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    zh: zhCN,
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export const elementLocale = computed(() => currentLocale() === 'zh-CN' ? zhCn : en)

export function setLocale(nextLocale: GmLocale) {
  i18n.global.locale.value = nextLocale
  localStorage.setItem(STORAGE_KEY, nextLocale)
  document.documentElement.lang = nextLocale
}

export function toggleLocale() {
  setLocale(currentLocale() === 'zh-CN' ? 'en-US' : 'zh-CN')
}

export function currentLocale(): GmLocale {
  return i18n.global.locale.value as GmLocale
}

function readInitialLocale(): GmLocale {
  if (typeof localStorage === 'undefined') {
    return 'zh-CN'
  }
  const saved = localStorage.getItem(STORAGE_KEY)
  if (supportedLocales.includes(saved as GmLocale)) {
    return saved as GmLocale
  }
  return navigator.language.toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN'
}
