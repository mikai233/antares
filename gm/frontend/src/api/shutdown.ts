import { http } from './http'

export interface ShutdownStatusResponse {
  planId?: string | null
  phase: string
  requestedBy?: string | null
  expectedGateCount: number
  drainedGateCount: number
  expectedPlayerCount: number
  flushedPlayerCount: number
  expectedWorldCount: number
  flushedWorldCount: number
  errors: string[]
}

export interface StartShutdownRequest {
  planId?: string | null
  requestedBy?: string | null
}

export async function getShutdownStatus() {
  const response = await http.get<ShutdownStatusResponse>('/gm/api/shutdown/status')
  return response.data
}

export async function startShutdown(request: StartShutdownRequest) {
  const response = await http.post<ShutdownStatusResponse>('/gm/api/shutdown/start', request)
  return response.data
}
