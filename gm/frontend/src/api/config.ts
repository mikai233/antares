import { http } from './http'

export interface GmConfigMetadata {
  revision: unknown
  tableCount: number
}

export interface GmConfigReloadStatus {
  currentRevision?: unknown
  lastSuccess?: GmConfigReloadRecord | null
  lastFailure?: GmConfigReloadRecord | null
  recent: GmConfigReloadRecord[]
}

export interface GmConfigReloadRecord {
  id: number
  status: string
  occurredAt: string
  previousRevision?: unknown
  currentRevision?: unknown
  addedTables: GmConfigChangedTable[]
  removedTables: GmConfigChangedTable[]
  changedTables: GmConfigChangedTable[]
  signalReason?: string | null
  signalSource?: string | null
  message?: string | null
}

export interface GmConfigChangedTable {
  name: string
  keyType: string
  rowType: string
  previousSize?: number | null
  currentSize?: number | null
}

export interface GmConfigTableSummary {
  name: string
  keyType: string
  rowType: string
  size: number
}

export interface GmConfigFieldDescriptor {
  name: string
  type: string
  nullable: boolean
  indexed: boolean
  sensitive: boolean
}

export interface GmConfigTableDescriptor {
  name: string
  keyType: string
  rowType: string
  size: number
  fields: GmConfigFieldDescriptor[]
}

export interface GmConfigRow {
  id: string
  values: Record<string, unknown>
}

export interface GmConfigRowQuery {
  keyword?: string | null
  filters?: Array<{
    field: string
    op: 'Eq' | 'Contains'
    value: string
  }>
  offset?: number
  limit?: number
}

export interface GmConfigRowPage {
  table: string
  rows: GmConfigRow[]
  offset: number
  limit: number
  total: number
  nextOffset?: number | null
}

export interface ClusterConfigReloadRequest {
  target: 'all' | 'role' | 'nodes' | 'addresses'
  role?: string | null
  nodeIds?: string[]
  addresses?: string[]
  timeoutMillis: number
}

export interface ClusterConfigReloadResult {
  target: unknown
  requestedAt: string
  results: Array<{
    nodeId?: string | null
    address: string
    success: boolean
    revision?: unknown
    message?: string | null
  }>
}

export async function getConfigMetadata() {
  const response = await http.get<GmConfigMetadata>('/gm/api/config/metadata')
  return response.data
}

export async function getConfigReloadStatus() {
  const response = await http.get<GmConfigReloadStatus>('/gm/api/config/reload/status')
  return response.data
}

export async function listConfigReloadHistory(limit = 20) {
  const response = await http.get<GmConfigReloadRecord[]>('/gm/api/config/reload/history', {
    params: { limit },
  })
  return response.data
}

export async function reloadLocalConfig() {
  const response = await http.post<GmConfigReloadRecord>('/gm/api/config/reload')
  return response.data
}

export async function getClusterConfigStatus() {
  const response = await http.get<unknown[]>('/gm/api/config/cluster/status')
  return response.data
}

export async function getClusterConfigConsistency() {
  const response = await http.get<unknown>('/gm/api/config/cluster/consistency')
  return response.data
}

export async function reloadClusterConfig(request: ClusterConfigReloadRequest) {
  const response = await http.post<ClusterConfigReloadResult>('/gm/api/config/cluster/reload', request)
  return response.data
}

export async function listConfigTables() {
  const response = await http.get<GmConfigTableSummary[]>('/gm/api/config/tables')
  return response.data
}

export async function getConfigTableSchema(table: string) {
  const response = await http.get<GmConfigTableDescriptor>(`/gm/api/config/tables/${encodeURIComponent(table)}/schema`)
  return response.data
}

export async function queryConfigRows(table: string, query: GmConfigRowQuery) {
  const response = await http.post<GmConfigRowPage>(
    `/gm/api/config/tables/${encodeURIComponent(table)}/query`,
    query,
  )
  return response.data
}

export async function getConfigRow(table: string, id: string) {
  const response = await http.get<GmConfigRow>(
    `/gm/api/config/tables/${encodeURIComponent(table)}/rows/${encodeURIComponent(id)}`,
  )
  return response.data
}

export function revisionText(value: unknown): string {
  if (value == null) {
    return '-'
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  if (typeof value === 'object' && 'value' in value) {
    return revisionText((value as { value?: unknown }).value)
  }
  return JSON.stringify(value)
}
