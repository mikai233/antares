import { http } from './http'

export interface GmClusterStatus {
  nodes: GmClusterNode[]
  roleCounts: Record<string, number>
}

export interface GmClusterNode {
  nodeId?: string | null
  address: string
  status: string
  roles: string[]
  seed?: boolean | null
  attributes: Record<string, string>
}

export interface GmClusterOperationResult {
  action: string
  targetAddress: string
  accepted: boolean
  message: string
  managementEndpoint?: string | null
  attributes: Record<string, string>
}

export interface ClusterActionPayload {
  address: string
  reason: string
}

export interface ClusterJoinPayload {
  nodeAddress: string
  seedAddress?: string | null
  reason: string
}

export interface ClusterDownPayload extends ClusterActionPayload {
  confirmed: boolean
}

export async function getClusterStatus() {
  const response = await http.get<GmClusterStatus>('/gm/api/cluster/status')
  return response.data
}

export async function getRawClusterStatus() {
  const response = await http.get<string>('/gm/api/cluster/management/raw', {
    responseType: 'text',
  })
  return response.data
}

export async function leaveClusterNode(payload: ClusterActionPayload) {
  const response = await http.post<GmClusterOperationResult>('/gm/api/cluster/actions/leave', payload)
  return response.data
}

export async function joinClusterNode(payload: ClusterJoinPayload) {
  const response = await http.post<GmClusterOperationResult>('/gm/api/cluster/actions/join', payload)
  return response.data
}

export async function downClusterNode(payload: ClusterDownPayload) {
  const response = await http.post<GmClusterOperationResult>('/gm/api/cluster/actions/down', payload)
  return response.data
}
