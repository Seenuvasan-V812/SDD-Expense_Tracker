export interface UserProfile {
  userId: string
  fullName: string
  email: string
  status: string
  preferredCurrency: string
  timezone: string | null
  locale: string | null
  weeklyDigestEnabled: boolean
  createdAt: string
}

export interface UpdateProfileRequest {
  fullName: string
  timezone: string
  locale: string
  weeklyDigestEnabled: boolean
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}
