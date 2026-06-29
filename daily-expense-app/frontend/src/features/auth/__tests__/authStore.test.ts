import { describe, it, expect, beforeEach } from 'vitest'
import { setTokens, clearTokens, getAccessToken } from '../authStore'

describe('authStore — in-memory access token', () => {
  beforeEach(() => clearTokens())

  it('getAccessToken returns null before any token is set', () => {
    expect(getAccessToken()).toBeNull()
  })

  it('getAccessToken returns the stored token after setTokens', () => {
    setTokens('my-access-token')
    expect(getAccessToken()).toBe('my-access-token')
  })

  it('getAccessToken returns null after clearTokens', () => {
    setTokens('my-access-token')
    clearTokens()
    expect(getAccessToken()).toBeNull()
  })

  it('token is NOT stored in localStorage or sessionStorage', () => {
    setTokens('secret-jwt')
    const lsKeys = Object.keys(localStorage)
    const ssKeys = Object.keys(sessionStorage)
    const anyStorageValue = [...lsKeys, ...ssKeys].some(
      (k) =>
        localStorage.getItem(k)?.includes('secret-jwt') ||
        sessionStorage.getItem(k)?.includes('secret-jwt'),
    )
    expect(anyStorageValue).toBe(false)
  })
})
