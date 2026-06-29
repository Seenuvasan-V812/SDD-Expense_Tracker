import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { getAccessToken } from './authStore'

interface ProtectedRouteProps {
  children: ReactNode
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const token = getAccessToken()
  if (!token) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}
