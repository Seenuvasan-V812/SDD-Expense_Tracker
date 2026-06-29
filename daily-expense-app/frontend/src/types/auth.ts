export interface AuthTokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSec: number
}

export interface UserProfileResponse {
  id: string
  email: string
  fullName: string
  status: 'INACTIVE_UNVERIFIED' | 'ACTIVE' | 'DELETED'
}
