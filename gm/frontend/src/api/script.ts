import { http } from './http'

export interface ScriptExecutionResponse {
  uid: string | null
  success: boolean
  error?: string | null
}

export interface ScriptUploadPayload {
  script: File
  extra?: File
}

export interface ActorScriptPayload extends ScriptUploadPayload {
  actorName?: string
  actorPath?: string
  ids?: string
  role?: string
  addresses?: string[]
  patch?: boolean
}

function formData(payload: ScriptUploadPayload) {
  const data = new FormData()
  data.append('script', payload.script)
  if (payload.extra) {
    data.append('extra', payload.extra)
  }
  return data
}

function appendAddresses(data: FormData, addresses: string[] = []) {
  addresses.filter(Boolean).forEach(address => data.append('address', address))
}

export async function executePlayerActorScript(payload: ActorScriptPayload) {
  const data = formData(payload)
  data.append('player_id', payload.ids ?? '')
  const response = await http.post<ScriptExecutionResponse[]>('/script/player_actor_script', data)
  return response.data
}

export async function executeWorldActorScript(payload: ActorScriptPayload) {
  const data = formData(payload)
  data.append('world_id', payload.ids ?? '')
  const response = await http.post<ScriptExecutionResponse[]>('/script/world_actor_script', data)
  return response.data
}

export async function executeGlobalActorScript(payload: ActorScriptPayload) {
  const data = formData(payload)
  data.append('actor_name', payload.actorName ?? '')
  const response = await http.post<ScriptExecutionResponse>('/script/global_actor_script', data)
  return response.data
}

export async function executeChannelActorScript(payload: ActorScriptPayload) {
  const data = formData(payload)
  data.append('actor_path', payload.actorPath ?? '')
  const response = await http.post<ScriptExecutionResponse>('/script/channel_actor_script', data)
  return response.data
}

export async function executeNodeScript(payload: ActorScriptPayload) {
  const data = formData(payload)
  appendAddresses(data, payload.addresses)
  await http.post('/script/node_script', data)
}

export async function executeNodeRoleScript(payload: ActorScriptPayload) {
  const data = formData(payload)
  data.append('role', payload.role ?? '')
  appendAddresses(data, payload.addresses)
  if (payload.patch) {
    data.append('patch', 'true')
  }
  await http.post('/script/node_role_script', data)
}
