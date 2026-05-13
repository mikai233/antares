import { http } from './http'

export interface GameTimeOverrideResponse {
  epoch: number
  globalOffsetMillis: number
  currentMillis: number
  zoneId: string
}

export interface GameTimeZoneResponse {
  zoneId: string
  currentMillis: number
}

export interface GameTimeReloadStatusResponse {
  epoch: number
  acks: GameTimeReloadAckResponse[]
}

export interface GameTimeReloadAckResponse {
  nodeId: string
  role: string
  success: boolean
  error?: string | null
}

export async function getGameTimeOverride() {
  const response = await http.get<GameTimeOverrideResponse>('/gm/api/game-time/override')
  return response.data
}

export async function getGameTimeZone() {
  const response = await http.get<GameTimeZoneResponse>('/gm/api/game-time/time-zone')
  return response.data
}

export async function updateGameTimeOverride(globalOffsetMillis: number) {
  const response = await http.post<GameTimeOverrideResponse>('/gm/api/game-time/override', {
    globalOffsetMillis,
  })
  return response.data
}

export async function getGameTimeReloadStatus(epoch: number) {
  const response = await http.get<GameTimeReloadStatusResponse>('/gm/api/game-time/reload-status', {
    params: { epoch },
  })
  return response.data
}
