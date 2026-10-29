import type { GridResponse, ValueResponse, VariableInfo } from './types'

const BASE = import.meta.env.VITE_API_BASE ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`请求失败 (${response.status}) ${body}`.trim())
  }
  return (await response.json()) as T
}

/** 列出 GRIB2 文件中的全部变量。 */
export function fetchVariables(): Promise<VariableInfo[]> {
  return request<VariableInfo[]>('/api/grib/variables')
}

/** 查询变量的网格信息。 */
export function fetchGrid(variable: string, axes = false): Promise<GridResponse> {
  const suffix = axes ? '?axes=true' : ''
  return request<GridResponse>(`/api/grib/grid/${encodeURIComponent(variable)}${suffix}`)
}

/** 按经纬度（可选时次）查询要素值。 */
export function fetchValue(params: {
  variable?: string
  lat: number
  lon: number
  time?: string
}): Promise<ValueResponse> {
  const query = new URLSearchParams()
  if (params.variable) query.set('variable', params.variable)
  query.set('lat', String(params.lat))
  query.set('lon', String(params.lon))
  if (params.time) query.set('time', params.time)
  return request<ValueResponse>(`/api/grib/value?${query.toString()}`)
}
