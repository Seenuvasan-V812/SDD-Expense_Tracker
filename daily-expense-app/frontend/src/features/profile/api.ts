import { axiosClient } from '@/lib/axiosClient'
import type { UserProfile, UpdateProfileRequest, ChangePasswordRequest } from './types'

export async function fetchProfile(): Promise<UserProfile> {
  const { data } = await axiosClient.get('/api/v1/users/me')
  return data as UserProfile
}

export async function updateProfile(req: UpdateProfileRequest): Promise<UserProfile> {
  const { data } = await axiosClient.put('/api/v1/users/me', req)
  return data as UserProfile
}

export async function changePassword(req: ChangePasswordRequest): Promise<void> {
  await axiosClient.patch('/api/v1/users/me/password', req)
}
