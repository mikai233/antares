import { http } from './http'

export interface GmActionDescriptor {
  key: string | { value: string }
  name: string
  description?: string | null
  risk: string
}

export interface GmMenuItem {
  id: string
  title: string
  route?: string | null
  action?: string | { value: string } | null
  order: number
  children: GmMenuItem[]
}

export interface GmRoute {
  id: string
  path: string
  component: string
  action?: string | { value: string } | null
  meta: Record<string, string>
}

export interface GmFeatureDescriptor {
  id: string | { value: string }
  name: string
  description?: string | null
  actions: GmActionDescriptor[]
  menus: GmMenuItem[]
  routes: GmRoute[]
}

export async function listGmFeatures() {
  const response = await http.get<GmFeatureDescriptor[]>('/gm/api/features')
  return response.data
}

export async function listGmMenus() {
  const response = await http.get<GmMenuItem[]>('/gm/api/menus')
  return response.data
}

export async function listGmRoutes() {
  const response = await http.get<GmRoute[]>('/gm/api/routes')
  return response.data
}

export async function listGmActions() {
  const response = await http.get<GmActionDescriptor[]>('/gm/api/actions')
  return response.data
}

export function gmId(value: string | { value: string }): string {
  return typeof value === 'string' ? value : value.value
}
