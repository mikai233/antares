import { reactive } from 'vue'
import { getGameTimeZone } from '@/api/gameTime'

export const serverTimeZone = reactive({
  zoneId: '',
  currentMillis: 0,
  loaded: false,
  loading: false,
  error: '',
})

const dateTimeFormatters = new Map<string, Intl.DateTimeFormat>()

export async function loadServerTimeZone() {
  if (serverTimeZone.loading) {
    return
  }
  serverTimeZone.loading = true
  try {
    const next = await getGameTimeZone()
    applyServerTimeZone(next.zoneId, next.currentMillis)
  } catch (error) {
    serverTimeZone.error = error instanceof Error ? error.message : 'Failed to load server time zone'
  } finally {
    serverTimeZone.loading = false
  }
}

export function applyServerTimeZone(zoneId: string, currentMillis?: number) {
  serverTimeZone.zoneId = zoneId
  serverTimeZone.currentMillis = currentMillis ?? serverTimeZone.currentMillis
  serverTimeZone.loaded = true
  serverTimeZone.error = ''
}

export function formatServerDateTime(value?: string | number | null) {
  if (value == null || value === '') {
    return '-'
  }
  const millis = typeof value === 'number' ? value : parseDateTimeMillis(value)
  if (millis == null) {
    return String(value)
  }
  const zoneId = serverTimeZone.zoneId || Intl.DateTimeFormat().resolvedOptions().timeZone
  return formatParts(formatterFor(zoneId).formatToParts(new Date(millis)))
}

export function serverTimeZoneText() {
  return serverTimeZone.zoneId || '-'
}

function parseDateTimeMillis(value: string) {
  const normalized = value.replace(/\.(\d{3})\d+(Z|[+-]\d{2}:\d{2})$/, '.$1$2')
  const millis = Date.parse(normalized)
  return Number.isFinite(millis) ? millis : undefined
}

function formatterFor(zoneId: string) {
  const cached = dateTimeFormatters.get(zoneId)
  if (cached) {
    return cached
  }
  const formatter = new Intl.DateTimeFormat('zh-CN', {
    timeZone: zoneId,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
  dateTimeFormatters.set(zoneId, formatter)
  return formatter
}

function formatParts(parts: Intl.DateTimeFormatPart[]) {
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`
}
