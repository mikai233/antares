import { http } from './http'

export type PatchStatus = 'Draft' | 'Enabled' | 'Disabled' | 'Expired' | 'Failed'

export interface RuntimePatchDescriptor {
  id: string | { value: string }
  artifact: PatchArtifact
  compatibility: PatchCompatibility
  requirements: PatchRequirements
  name: string
  target: unknown
  status: PatchStatus
  revision: number
}

export interface PatchArtifact {
  name: string
  checksum: string
  version?: string | null
}

export interface PatchCompatibility {
  appName: string
  versions: string[]
}

export interface PatchRequirements {
  roles: string[]
  modules: string[]
  capabilities: string[]
}

export interface RuntimePatchNodeResult {
  patchId: string | { value: string }
  nodeId?: string | null
  address: string
  appName: string
  version: string
  roles: string[]
  modules: string[]
  capabilities: string[]
  status: string
  attempt: number
  operationCount?: number | null
  message?: string | null
  updatedAt: string
}

export interface PatchClusterApplyResult {
  patchId: string | { value: string }
  requestedAt: string
  results: RuntimePatchNodeResult[]
  succeeded: boolean
}

export interface CreatePatchPayload {
  file: File
  id: string
  name: string
  appName: string
  versions: string[]
  artifactName?: string
  artifactVersion?: string
  targetType: 'all-nodes' | 'roles' | 'nodes'
  roles: string[]
  addresses: string[]
  requiredRoles: string[]
  requiredModules: string[]
  requiredCapabilities: string[]
  status: PatchStatus
}

export async function listPatches(params: {
  status?: string
  appName?: string
  version?: string
} = {}) {
  const response = await http.get<RuntimePatchDescriptor[]>('/gm/api/patches', {
    params: blankToUndefined(params),
  })
  return response.data
}

export async function createPatch(payload: CreatePatchPayload) {
  const form = new FormData()
  form.append('file', payload.file)
  form.append('id', payload.id)
  form.append('name', payload.name)
  form.append('appName', payload.appName)
  appendList(form, 'versions', payload.versions)
  appendOptional(form, 'artifactName', payload.artifactName)
  appendOptional(form, 'artifactVersion', payload.artifactVersion)
  form.append('targetType', payload.targetType)
  appendList(form, 'roles', payload.roles)
  appendList(form, 'addresses', payload.addresses)
  appendList(form, 'requiredRoles', payload.requiredRoles)
  appendList(form, 'requiredModules', payload.requiredModules)
  appendList(form, 'requiredCapabilities', payload.requiredCapabilities)
  form.append('status', payload.status)

  const response = await http.post<RuntimePatchDescriptor>('/gm/api/patches', form)
  return response.data
}

export async function getPatch(id: string) {
  const response = await http.get<RuntimePatchDescriptor>(`/gm/api/patches/${encodeURIComponent(id)}`)
  return response.data
}

export async function applyPatch(id: string) {
  const response = await http.post<PatchClusterApplyResult>(`/gm/api/patches/${encodeURIComponent(id)}/apply`)
  return response.data
}

export async function applyEnabledPatches() {
  const response = await http.post<PatchClusterApplyResult[]>('/gm/api/patches/apply-enabled')
  return response.data
}

export async function expireIncompatiblePatches() {
  const response = await http.post<RuntimePatchDescriptor[]>('/gm/api/patches/expire-incompatible')
  return response.data
}

export async function disablePatch(id: string) {
  await http.post(`/gm/api/patches/${encodeURIComponent(id)}/disable`)
}

export async function listPatchNodeResults(params: {
  patchId?: string
  address?: string
  status?: string
} = {}) {
  const response = await http.get<RuntimePatchNodeResult[]>('/gm/api/patches/node-results', {
    params: blankToUndefined(params),
  })
  return response.data
}

export function patchIdValue(value: string | { value: string }): string {
  return typeof value === 'string' ? value : value.value
}

export function describePatchTarget(target: unknown): string {
  if (!target || typeof target !== 'object') {
    return target == null ? 'all nodes' : String(target)
  }
  const record = target as Record<string, unknown>
  if (Array.isArray(record.roles)) {
    return `roles: ${record.roles.join(', ')}`
  }
  if (Array.isArray(record.addresses)) {
    return `nodes: ${record.addresses.join(', ')}`
  }
  if ('type' in record) {
    return String(record.type)
  }
  return 'all nodes'
}

function appendOptional(form: FormData, name: string, value?: string) {
  if (value?.trim()) {
    form.append(name, value.trim())
  }
}

function appendList(form: FormData, name: string, values: string[]) {
  for (const value of values) {
    const trimmed = value.trim()
    if (trimmed) {
      form.append(name, trimmed)
    }
  }
}

function blankToUndefined<T extends Record<string, string | undefined>>(params: T) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value && value.trim()),
  )
}
