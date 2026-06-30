import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from '@/features/auth/ProtectedRoute'
import LoginPage from '@/features/auth/LoginPage'
import RegisterPage from '@/features/auth/RegisterPage'
import VerifyEmailPage from '@/features/auth/VerifyEmailPage'
import ForgotPasswordPage from '@/features/auth/ForgotPasswordPage'
import ResetPasswordPage from '@/features/auth/ResetPasswordPage'
import AppShell from '@/components/AppShell'
import ExpensesPage from '@/features/expenses/ExpensesPage'
import CategoriesPage from '@/features/categories/CategoriesPage'
import BudgetsPage from '@/features/budgets/BudgetsPage'
import SavingsGoalsPage from '@/features/savings-goals/SavingsGoalsPage'
import ProfilePage from '@/features/profile/ProfilePage'

function App() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      {/* Protected app routes — all rendered inside AppShell */}
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/expenses" replace />} />
        <Route path="/expenses" element={<ExpensesPage />} />
        <Route path="/categories" element={<CategoriesPage />} />
        <Route path="/budgets" element={<BudgetsPage />} />
        <Route path="/savings-goals" element={<SavingsGoalsPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>

      <Route path="*" element={<Navigate to="/expenses" replace />} />
    </Routes>
  )
}

export default App
