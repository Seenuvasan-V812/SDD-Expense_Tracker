import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from '@/features/auth/ProtectedRoute'
import LoginPage from '@/features/auth/LoginPage'
import RegisterPage from '@/features/auth/RegisterPage'
import VerifyEmailPage from '@/features/auth/VerifyEmailPage'
import ForgotPasswordPage from '@/features/auth/ForgotPasswordPage'
import ResetPasswordPage from '@/features/auth/ResetPasswordPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <div className="p-4 text-foreground">Daily Expense Tracker</div>
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
