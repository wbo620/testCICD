import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fetchGrid, fetchValue, fetchVariables } from './api'

function mockFetch(payload: unknown, ok = true) {
  const spy = vi.fn().mockResolvedValue({
    ok,
    status: ok ? 200 : 500,
    json: async () => payload,
    text: async () => (ok ? '' : 'boom'),
  })
  vi.stubGlobal('fetch', spy)
  return spy
}

describe('api', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
  })

  it('请求变量列表', async () => {
    const spy = mockFetch([{ name: 'Visibility_surface' }])
    await expect(fetchVariables()).resolves.toEqual([{ name: 'Visibility_surface' }])
    expect(spy).toHaveBeenCalledWith('/api/grib/variables', expect.any(Object))
  })

  it('按经纬度构造查询参数', async () => {
    const spy = mockFetch({ value: 1 })
    await fetchValue({ variable: 'Visibility_surface', lat: 39.9, lon: 116.4, time: '2026-07-01T00:00:00Z' })
    const url = spy.mock.calls[0][0] as string
    expect(url).toContain('/api/grib/value?')
    expect(url).toContain('variable=Visibility_surface')
    expect(url).toContain('lat=39.9')
    expect(url).toContain('lon=116.4')
    expect(url).toContain('time=2026-07-01T00%3A00%3A00Z')
  })

  it('时间留空时不传 time 参数', async () => {
    const spy = mockFetch({ value: 1 })
    await fetchValue({ lat: 0, lon: 0 })
    expect(spy.mock.calls[0][0] as string).not.toContain('time=')
  })

  it('请求网格信息', async () => {
    const spy = mockFetch({ variable: 'Visibility_surface' })
    await fetchGrid('Visibility_surface', true)
    expect(spy.mock.calls[0][0] as string).toBe('/api/grib/grid/Visibility_surface?axes=true')
  })

  it('非 2xx 时抛出异常', async () => {
    mockFetch({}, false)
    await expect(fetchVariables()).rejects.toThrow(/请求失败 \(500\)/)
  })
})
