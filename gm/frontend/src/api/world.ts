import { http } from './http'

export type WorldRuntimeStatus = 'Loading' | 'Up' | 'Stopping' | 'Down'
export type WorldHeartbeatFilter = 'Healthy' | 'Unhealthy'

export interface WorldStatusListResponse {
  staleAfterMillis: number
  page: number
  pageSize: number
  total: number
  totalWorlds: number
  upWorlds: number
  loadingWorlds: number
  downWorlds: number
  staleWorlds: number
  worlds: WorldStatus[]
}

export interface WorldStatus {
  worldId: number
  name?: string | null
  configured: boolean
  status: WorldRuntimeStatus
  reportedStatus?: WorldRuntimeStatus | null
  stale: boolean
  nodeId?: string | null
  nodeAddress?: string | null
  updatedAtMillis?: number | null
  message?: string | null
  openDateTime?: string | null
  onlineLimit?: number | null
  registerLimit?: number | null
}

export interface GetWorldStatusesParams {
  page?: number
  pageSize?: number
  worldId?: number | string
  name?: string
  status?: WorldRuntimeStatus | ''
  configured?: boolean
  heartbeat?: WorldHeartbeatFilter | ''
  openFrom?: string
  openTo?: string
}

export async function getWorldStatuses(params: GetWorldStatusesParams = {}) {
  const response = await http.get<WorldStatusListResponse>('/gm/api/worlds/status', {
    params: {
      page: params.page,
      pageSize: params.pageSize,
      worldId: params.worldId || undefined,
      name: params.name || undefined,
      status: params.status || undefined,
      configured: params.configured,
      heartbeat: params.heartbeat || undefined,
      openFrom: params.openFrom || undefined,
      openTo: params.openTo || undefined,
    },
  })
  return response.data
}
