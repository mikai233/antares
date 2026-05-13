import axios from 'axios'
import { computed, reactive } from 'vue'

export type BackendHealthStatus = 'checking' | 'online' | 'offline'

export const backendHealth = reactive({
  status: 'checking' as BackendHealthStatus,
  message: '',
  lastCheckedAt: 0,
})

export const isBackendOnline = computed(() => backendHealth.status === 'online')

const healthHttp = axios.create({
  baseURL: '/',
  timeout: 5_000,
})

export async function checkBackendHealth() {
  const wasNeverChecked = backendHealth.lastCheckedAt === 0
  if (wasNeverChecked) {
    backendHealth.status = 'checking'
  }
  try {
    const response = await healthHttp.get('/actuator/health')
    const status = typeof response.data?.status === 'string' ? response.data.status : 'UP'
    if (status.toUpperCase() === 'UP') {
      markBackendOnline()
    } else {
      markBackendOffline(`Health status: ${status}`)
    }
  } catch (error) {
    markBackendOffline(errorMessage(error))
  } finally {
    backendHealth.lastCheckedAt = Date.now()
  }
}

export function markBackendOnline() {
  backendHealth.status = 'online'
  backendHealth.message = ''
  backendHealth.lastCheckedAt = Date.now()
}

export function markBackendOffline(message: string) {
  backendHealth.status = 'offline'
  backendHealth.message = message
  backendHealth.lastCheckedAt = Date.now()
}

export function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data
    if (typeof data === 'string' && data.trim()) {
      return data
    }
    if (data && typeof data === 'object') {
      const record = data as Record<string, unknown>
      for (const key of ['message', 'detail', 'error']) {
        const value = record[key]
        if (typeof value === 'string' && value.trim()) {
          return value
        }
      }
    }
    if (error.response?.status) {
      return `${error.response.status} ${error.response.statusText || 'Request failed'}`
    }
    return error.message || 'Network Error'
  }
  return error instanceof Error ? error.message : 'Request failed'
}

export function isBackendRequest(url?: string) {
  return Boolean(url && (url.startsWith('/gm/api') || url.startsWith('/script') || url.startsWith('/actuator')))
}
