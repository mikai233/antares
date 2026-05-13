import { http } from './http'

export interface GmActionDescriptor {
  key: string
  name: string
  description?: string | null
  risk: string
}

export interface GmMenuItem {
  id: string
  title: string
  route?: string | null
  action?: string | null
  order: number
  children: GmMenuItem[]
}

export interface GmRoute {
  id: string
  path: string
  component: string
  action?: string | null
  meta: Record<string, string>
}

export interface GmFeatureDescriptor {
  id: string
  name: string
  description?: string | null
  actions: GmActionDescriptor[]
  menus: GmMenuItem[]
  routes: GmRoute[]
}

export async function listGmFeatures() {
  const response = await http.get<unknown[]>('/gm/api/features')
  return response.data.map(normalizeFeature)
}

export async function listGmMenus() {
  const response = await http.get<unknown[]>('/gm/api/menus')
  return response.data.map(normalizeMenu)
}

export async function listGmRoutes() {
  const response = await http.get<unknown[]>('/gm/api/routes')
  return response.data.map(normalizeRoute)
}

export async function listGmActions() {
  const response = await http.get<unknown[]>('/gm/api/actions')
  return response.data.map(normalizeAction)
}

export function gmId(value: string): string {
  return value
}

type RawRecord = Record<string, unknown>

function normalizeFeature(value: unknown): GmFeatureDescriptor {
  const record = asRecord(value)
  return {
    id: readString(record, 'id'),
    name: readString(record, 'name'),
    description: readNullableString(record, 'description'),
    actions: readArray(record, 'actions').map(normalizeAction),
    menus: readArray(record, 'menus').map(normalizeMenu),
    routes: readArray(record, 'routes').map(normalizeRoute),
  }
}

function normalizeAction(value: unknown): GmActionDescriptor {
  const record = asRecord(value)
  return {
    key: readString(record, 'key'),
    name: readString(record, 'name'),
    description: readNullableString(record, 'description'),
    risk: readString(record, 'risk'),
  }
}

function normalizeMenu(value: unknown): GmMenuItem {
  const record = asRecord(value)
  return {
    id: readString(record, 'id'),
    title: readString(record, 'title'),
    route: readNullableString(record, 'route'),
    action: readNullableString(record, 'action'),
    order: readNumber(record, 'order'),
    children: readArray(record, 'children').map(normalizeMenu),
  }
}

function normalizeRoute(value: unknown): GmRoute {
  const record = asRecord(value)
  return {
    id: readString(record, 'id'),
    path: readString(record, 'path'),
    component: readString(record, 'component'),
    action: readNullableString(record, 'action'),
    meta: readStringRecord(record, 'meta'),
  }
}

function asRecord(value: unknown): RawRecord {
  return value && typeof value === 'object' ? value as RawRecord : {}
}

function readString(record: RawRecord, key: string): string {
  const value = readValue(record, key)
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  if (value && typeof value === 'object' && 'value' in value) {
    return readString(value as RawRecord, 'value')
  }
  return ''
}

function readNullableString(record: RawRecord, key: string): string | null {
  const value = readValue(record, key)
  if (value == null) {
    return null
  }
  const text = readString({ [key]: value }, key)
  return text || null
}

function readNumber(record: RawRecord, key: string): number {
  const value = readValue(record, key)
  return typeof value === 'number' ? value : Number(value) || 0
}

function readArray(record: RawRecord, key: string): unknown[] {
  const value = readValue(record, key)
  return Array.isArray(value) ? value : []
}

function readStringRecord(record: RawRecord, key: string): Record<string, string> {
  const value = readValue(record, key)
  if (!value || typeof value !== 'object') {
    return {}
  }
  return Object.fromEntries(
    Object.entries(value as RawRecord)
      .filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
  )
}

function readValue(record: RawRecord, key: string): unknown {
  if (key in record) {
    return record[key]
  }
  const aliasedKey = Object.keys(record).find(name => name.startsWith(`${key}-`))
  return aliasedKey ? record[aliasedKey] : undefined
}
