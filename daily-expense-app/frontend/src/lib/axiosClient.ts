import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { API_BASE_URL } from './apiConfig'
import { clearTokens, getAccessToken } from '@/features/auth/authStore'

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let refreshingPromise: Promise<string | null> | null = null

async function doRefresh(): Promise<string | null> {
  const response = await axios.post<{ accessToken: string }>(
    `${API_BASE_URL}/api/v1/auth/refresh`,
    undefined,
    { withCredentials: true },
  )
  return response.data.accessToken
}

export const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

// Attach Bearer token to every request automatically
axiosClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryConfig | undefined

    const requestUrl = originalRequest?.url ?? ''
    if (
      error.response?.status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      requestUrl.includes('/auth/')
    ) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    if (!refreshingPromise) {
      refreshingPromise = doRefresh()
        .catch(() => null)
        .finally(() => {
          refreshingPromise = null
        })
    }

    const newToken = await refreshingPromise

    if (!newToken) {
      clearTokens()
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }

    originalRequest.headers.Authorization = `Bearer ${newToken}`
    return axiosClient(originalRequest)
  },
)
