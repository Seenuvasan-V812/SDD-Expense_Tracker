import { beforeAll, afterEach, afterAll, describe, it, expect, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'

vi.mock('../apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))
vi.mock('@/features/auth/authStore', () => ({
  clearTokens: vi.fn(),
  getAccessToken: vi.fn(() => null),
  setTokens: vi.fn(),
}))

// Defer import so the mock is in place first
const { axiosClient } = await import('../axiosClient')
const { clearTokens } = await import('@/features/auth/authStore')

let refreshCallCount = 0

const server = setupServer(
  http.get('http://test-api/api/v1/protected', () => {
    return HttpResponse.json({ message: 'ok' })
  }),
  http.post('http://test-api/api/v1/auth/refresh', () => {
    refreshCallCount++
    return HttpResponse.json({ accessToken: 'new-token-abc' })
  }),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  refreshCallCount = 0
})
afterAll(() => server.close())

describe('axiosClient — single-flight refresh', () => {
  it('issues exactly ONE POST /auth/refresh for two concurrent 401s', async () => {
    let callCount = 0

    server.use(
      http.get('http://test-api/api/v1/protected', () => {
        callCount++
        if (callCount <= 2) {
          return new HttpResponse(null, { status: 401 })
        }
        return HttpResponse.json({ message: 'ok' })
      }),
    )

    const [result1, result2] = await Promise.all([
      axiosClient.get('/api/v1/protected'),
      axiosClient.get('/api/v1/protected'),
    ])

    expect(refreshCallCount).toBe(1)
    expect(result1.data).toEqual({ message: 'ok' })
    expect(result2.data).toEqual({ message: 'ok' })
  })

  it('clears tokens and rejects when refresh itself returns 401', async () => {
    server.use(
      http.get('http://test-api/api/v1/protected', () =>
        new HttpResponse(null, { status: 401 }),
      ),
      http.post('http://test-api/api/v1/auth/refresh', () =>
        new HttpResponse(null, { status: 401 }),
      ),
    )

    await expect(axiosClient.get('/api/v1/protected')).rejects.toBeDefined()
    expect(clearTokens).toHaveBeenCalled()
  })
})
