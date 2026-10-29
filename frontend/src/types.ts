export interface VariableInfo {
  name: string
  description: string | null
  units: string | null
  dimensions: string[]
  shape: number[]
  times: string[]
  latCount: number
  lonCount: number
  latRange: [number, number]
  lonRange: [number, number]
  missingValue: number | null
}

export interface ValueResponse {
  variable: string
  requestedLat: number
  requestedLon: number
  requestedTime: string | null
  matchedLat: number
  matchedLon: number
  matchedTime: string | null
  latIndex: number
  lonIndex: number
  timeIndex: number
  value: number
  units: string | null
  exact: boolean
}

export interface GridResponse {
  variable: string
  latCount: number
  lonCount: number
  latMin: number
  latMax: number
  lonMin: number
  lonMax: number
  times: string[]
  lats?: number[]
  lons?: number[]
}
