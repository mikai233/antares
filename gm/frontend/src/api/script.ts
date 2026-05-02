import {http} from './http'

export interface ScriptExecutionResponse {
    id: string
    scriptName: string
    scriptType: string
    targetType: string
    status: string
    totalTargets: number
    successCount: number
    failureCount: number
    timeoutCount: number
    createdAt: string
    finishedAt?: string | null
    targets: ScriptExecutionTargetResponse[]
}

export interface ScriptExecutionTargetResponse {
    target: string
    status: string
    success?: boolean | null
    error?: string | null
    nodeAddress?: string | null
    actorPath?: string | null
    startedAt: string
    finishedAt?: string | null
}

export interface ScriptUploadPayload {
    script: File
    extra?: File
}

export type ScriptExecutionTargetType =
    | 'PlayerActor'
    | 'WorldActor'
    | 'GlobalActor'
    | 'ActorPath'
    | 'Node'
    | 'NodeRole'

export interface CreateScriptExecutionRequest {
    targetType: ScriptExecutionTargetType
    targets?: string[]
    role?: string
    addresses?: string[]
    patch?: boolean
}

export interface CreateScriptExecutionPayload extends ScriptUploadPayload {
    request: CreateScriptExecutionRequest
}

function formData(payload: CreateScriptExecutionPayload) {
    const data = new FormData()
    data.append('script', payload.script)
    if (payload.extra) {
        data.append('extra', payload.extra)
    }
    data.append(
        'request',
        new Blob([JSON.stringify(payload.request)], {type: 'application/json'}),
    )
    return data
}

export async function createScriptExecution(payload: CreateScriptExecutionPayload) {
    const data = formData(payload)
    const response = await http.post<ScriptExecutionResponse>('/script/executions', data)
    return response.data
}

export async function listScriptExecutions() {
    const response = await http.get<ScriptExecutionResponse[]>('/script/executions')
    return response.data
}

export async function getScriptExecution(id: string) {
    const response = await http.get<ScriptExecutionResponse>(`/script/executions/${id}`)
    return response.data
}
