let _accessToken: string | null = null

export function setTokens(accessToken: string): void {
  _accessToken = accessToken
}

export function clearTokens(): void {
  _accessToken = null
}

export function getAccessToken(): string | null {
  return _accessToken
}
