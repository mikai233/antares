import {http} from './http'
import { i18n } from '@/i18n'

export interface GmScriptMetadata {
    engines: string[]
    targetTypes: string[]
    roles: string[]
    entityKinds: string[]
    singletons: string[]
    nodeAddresses: string[]
    defaultMaxConcurrentItems: number
    templates: Array<{
        id: string
        name: string
        engine: string
    }>
}

export interface GmScriptSettings {
    defaultMaxConcurrentItems: number
}

const DefaultScriptRoles = ['Gate', 'Global', 'Gm', 'Player', 'World']
const DefaultEntityKinds = ['PlayerActor', 'WorldActor']
const DefaultSingletons = ['worker']
const DefaultTargetTypes = ['all-nodes', 'role', 'nodes', 'actor-paths', 'entity', 'singleton']
const DefaultMaxConcurrentItems = 256

export interface ScriptArtifact {
    name: string
    engine: string
}

export interface GmScriptJobCommand {
    executionId: string
    target: unknown
    artifact: ScriptArtifact
    metadata: {
        requester?: string | null
        reason?: string | null
        attributes: Record<string, string>
    }
}

export interface ScriptJob {
    id: string | { value: string }
    command: GmScriptJobCommand
    status: string
    attempt: number
    totalItems: number
    completedItems: number
    failedItems: number
    cancelledItems: number
    createdAtMillis: number
    updatedAtMillis: number
}

export interface ScriptJobResultEntry {
    executionId: string
    success: boolean
    target?: string | null
    error?: string | null
    nodeAddress?: string | null
    actorPath?: string | null
}

export interface ScriptJobItemAttempt {
    attempt: number
    command: GmScriptJobCommand
    status: string
    results: ScriptJobResultEntry[]
    error?: string | null
    startedAtMillis: number
    finishedAtMillis?: number | null
}

export interface ScriptJobItem {
    id: string | { value: string }
    jobId: string | { value: string }
    target: unknown
    status: string
    results: ScriptJobResultEntry[]
    attempts: ScriptJobItemAttempt[]
    leaseOwner?: string | null
    leaseUntilMillis?: number | null
    createdAtMillis: number
    updatedAtMillis: number
}

export interface ScriptJobPage {
    jobs: ScriptJob[]
    offset: number
    limit: number
    total: number
    nextOffset?: number | null
}

export interface ScriptJobItemPage {
    items: ScriptJobItem[]
    offset: number
    limit: number
    total: number
    nextOffset?: number | null
}

export interface ScriptJobResultSummary {
    jobId: string
    totalItems: number
    completedItems: number
    failedItems: number
    cancelledItems: number
    errorTypes: Array<{
        error: string
        count: number
        sampleTargets: string[]
    }>
}

export type GmScriptTargetType =
    | 'all-nodes'
    | 'role'
    | 'nodes'
    | 'actor-paths'
    | 'entity'
    | 'singleton'

export interface GmScriptTargetRequest {
    type: GmScriptTargetType
    role?: string
    addresses?: string[]
    paths?: string[]
    kind?: string
    ids?: string[]
    name?: string
}

export interface CreateScriptJobPayload {
    script: File
    extra?: File
    target: GmScriptTargetRequest
    reason?: string
    maxConcurrentItems?: number
}

export async function getScriptMetadata() {
    const [metadataResponse, settings] = await Promise.all([
        http.get<GmScriptMetadata>('/gm/api/scripts/metadata'),
        getScriptSettingsOrDefault(),
    ])
    return normalizeScriptMetadata(metadataResponse.data, settings)
}

export async function createScriptJob(payload: CreateScriptJobPayload) {
    const jobId = crypto.randomUUID()
    const response = await http.post<ScriptJob>('/gm/api/scripts/jobs', {
        executionId: jobId,
        target: payload.target,
        artifact: {
            name: fileNameWithoutExtension(payload.script.name),
            engine: inferScriptEngine(payload.script.name),
            bodyBase64: await fileToBase64(payload.script),
            extraBase64: payload.extra ? await fileToBase64(payload.extra) : undefined,
        },
        metadata: {
            reason: payload.reason,
        },
        options: {
            maxConcurrentItems: payload.maxConcurrentItems,
        },
        timeoutMillis: 180_000,
    })
    return response.data
}

export async function listScriptJobs() {
    const response = await http.get<ScriptJobPage>('/gm/api/scripts/jobs')
    return response.data
}

export async function cancelScriptJob(id: string, reason?: string) {
    const response = await http.post<ScriptJob>(`/gm/api/scripts/jobs/${encodeURIComponent(id)}/cancel`, {
        reason: blankToUndefined(reason),
    })
    return response.data
}

export async function exportScriptJobResults(id: string, status?: string) {
    const response = await http.get<string>(`/gm/api/scripts/jobs/${encodeURIComponent(id)}/results.csv`, {
        params: {status: blankToUndefined(status)},
        responseType: 'text',
    })
    return response.data
}

export async function getScriptJob(id: string) {
    const response = await http.get<ScriptJob>(`/gm/api/scripts/jobs/${encodeURIComponent(id)}`)
    return response.data
}

export async function listScriptJobItems(id: string, params: {
    status?: string
    offset?: number
    limit?: number
} = {}) {
    const response = await http.get<ScriptJobItemPage>(`/gm/api/scripts/jobs/${encodeURIComponent(id)}/items`, {
        params: blankObjectToUndefined(params),
    })
    return response.data
}

export async function getScriptJobSummary(id: string) {
    const response = await http.get<ScriptJobResultSummary>(`/gm/api/scripts/jobs/${encodeURIComponent(id)}/summary`)
    return response.data
}

export async function getScriptJobItem(jobId: string, itemId: string) {
    const response = await http.get<ScriptJobItem>(
        `/gm/api/scripts/jobs/${encodeURIComponent(jobId)}/items/${encodeURIComponent(itemId)}`,
    )
    return response.data
}

export async function cancelScriptJobItem(jobId: string, itemId: string, reason?: string) {
    const response = await http.post<ScriptJobItem>(
        `/gm/api/scripts/jobs/${encodeURIComponent(jobId)}/items/${encodeURIComponent(itemId)}/cancel`,
        {reason: blankToUndefined(reason)},
    )
    return response.data
}

export async function retryScriptJobItem(jobId: string, itemId: string, timeoutMillis = 3_000) {
    const response = await http.post<ScriptJobItem>(
        `/gm/api/scripts/jobs/${encodeURIComponent(jobId)}/items/${encodeURIComponent(itemId)}/retry`,
        {timeoutMillis},
    )
    return response.data
}

export async function retryFailedScriptJobItems(id: string, request: {
    error?: string
    limit?: number
    timeoutMillis?: number
} = {}) {
    const response = await http.post<ScriptJobItem[]>(
        `/gm/api/scripts/jobs/${encodeURIComponent(id)}/failed-items/retry`,
        blankObjectToUndefined(request),
    )
    return response.data
}

export function describeScriptTarget(target: unknown): string {
    const t = i18n.global.t
    const value = target as Record<string, unknown> | null
    if (!value) {
        return t('未知')
    }
    if ('addresses' in value && Array.isArray(value.addresses)) {
        return (value.addresses as string[]).join(', ')
    }
    if ('paths' in value && Array.isArray(value.paths)) {
        return (value.paths as string[]).join(', ')
    }
    if ('ids' in value && Array.isArray(value.ids)) {
        const kind = nestedValue(value.kind)
        return `${kind ?? t('实体')}: ${(value.ids as string[]).join(', ')}`
    }
    if ('name' in value) {
        return nestedValue(value.name) ?? t('单例 Actor')
    }
    if ('role' in value) {
        return nestedValue(value.role) ?? 'Role'
    }
    return t('全部节点')
}

export function targetTypeLabel(target: unknown): string {
    const t = i18n.global.t
    const value = target as Record<string, unknown> | null
    if (!value) {
        return t('未知')
    }
    if ('addresses' in value && Array.isArray(value.addresses)) {
        return t('节点')
    }
    if ('paths' in value && Array.isArray(value.paths)) {
        return 'Actor Path'
    }
    if ('ids' in value && Array.isArray(value.ids)) {
        return t('实体 Actor')
    }
    if ('name' in value) {
        return t('单例 Actor')
    }
    if ('role' in value) {
        return 'Role'
    }
    return t('全部节点')
}

export function itemError(item: ScriptJobItem): string | undefined {
    const attempts = item.attempts ?? []
    const results = item.results ?? []
    const failedAttempt = [...attempts].reverse().find(attempt => attempt.error)
    return failedAttempt?.error ?? results.find(entry => entry.error)?.error ?? undefined
}

export function scriptIdValue(value: string | { value: string }): string {
    return typeof value === 'string' ? value : value.value
}

export function downloadCsv(fileName: string, content: string) {
    const blob = new Blob([content], {type: 'text/csv;charset=utf-8'})
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    URL.revokeObjectURL(url)
}

function blankToUndefined(value?: string) {
    const trimmed = value?.trim()
    return trimmed ? trimmed : undefined
}

function blankObjectToUndefined<T extends Record<string, unknown>>(value: T) {
    return Object.fromEntries(
        Object.entries(value).filter(([, item]) => item !== undefined && item !== null && item !== ''),
    )
}

function nestedValue(value: unknown): string | undefined {
    if (typeof value === 'string') {
        return value
    }
    if (value && typeof value === 'object' && 'value' in value) {
        const nested = (value as { value?: unknown }).value
        return typeof nested === 'string' ? nested : undefined
    }
    return undefined
}

function inferScriptEngine(fileName: string): string {
    const extension = fileName.split('.').pop()?.toLowerCase()
    if (extension === 'jar' || extension === 'groovy') {
        return extension
    }
    throw new Error(i18n.global.t('脚本文件扩展名必须是 jar 或 groovy'))
}

function fileNameWithoutExtension(fileName: string): string {
    const index = fileName.lastIndexOf('.')
    return index > 0 ? fileName.slice(0, index) : fileName
}

function fileToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => {
            const dataUrl = reader.result
            if (typeof dataUrl !== 'string') {
                reject(new Error(i18n.global.t('文件 {name} 读取失败', {name: file.name})))
                return
            }
            const content = dataUrl.split(',', 2)[1]
            if (!content) {
                reject(new Error(i18n.global.t('文件 {name} 编码失败', {name: file.name})))
                return
            }
            resolve(content)
        }
        reader.onerror = () => reject(reader.error ?? new Error(i18n.global.t('文件 {name} 读取失败', {name: file.name})))
        reader.readAsDataURL(file)
    })
}

async function getScriptSettingsOrDefault() {
    try {
        const response = await http.get<GmScriptSettings>('/gm/api/scripts/settings')
        return response.data
    } catch {
        return {defaultMaxConcurrentItems: DefaultMaxConcurrentItems}
    }
}

function normalizeScriptMetadata(data: Partial<GmScriptMetadata>, settings: GmScriptSettings): GmScriptMetadata {
    return {
        engines: stringArrayOrDefault(data.engines, []),
        targetTypes: stringArrayOrDefault(data.targetTypes, DefaultTargetTypes),
        roles: stringArrayOrDefault(data.roles, DefaultScriptRoles),
        entityKinds: stringArrayOrDefault(data.entityKinds, DefaultEntityKinds),
        singletons: stringArrayOrDefault(data.singletons, DefaultSingletons),
        nodeAddresses: stringArrayOrDefault(data.nodeAddresses, []),
        defaultMaxConcurrentItems: positiveNumberOrDefault(
            data.defaultMaxConcurrentItems ?? settings.defaultMaxConcurrentItems,
            DefaultMaxConcurrentItems,
        ),
        templates: Array.isArray(data.templates) ? data.templates : [],
    }
}

function stringArrayOrDefault(value: unknown, fallback: string[]) {
    if (!Array.isArray(value)) {
        return fallback
    }
    const items = value.filter(item => typeof item === 'string')
    return items.length > 0 ? items : fallback
}

function positiveNumberOrDefault(value: unknown, fallback: number) {
    return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback
}
