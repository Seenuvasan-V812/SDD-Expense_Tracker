import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Receipt, Tag, PiggyBank, Target, LogOut, UserCircle } from 'lucide-react'
import { clearTokens } from '@/features/auth/authStore'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/expenses',      label: 'Expenses',      icon: Receipt },
  { to: '/categories',    label: 'Categories',    icon: Tag },
  { to: '/budgets',       label: 'Budgets',       icon: LayoutDashboard },
  { to: '/savings-goals', label: 'Savings Goals', icon: Target },
  { to: '/profile',       label: 'Profile',       icon: UserCircle },
]

export default function AppShell() {
  const navigate = useNavigate()

  function handleLogout() {
    clearTokens()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-background">
      {/* Sidebar */}
      <aside className="w-56 shrink-0 border-r bg-card flex flex-col">
        <div className="h-14 flex items-center px-4 border-b">
          <PiggyBank className="h-5 w-5 text-primary mr-2" />
          <span className="font-semibold text-sm">Daily Expense</span>
        </div>

        <nav className="flex-1 px-2 py-4 space-y-1">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                )
              }
            >
              <Icon className="h-4 w-4 shrink-0" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="p-2 border-t">
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <LogOut className="h-4 w-4 shrink-0" />
            Logout
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto p-6">
        <Outlet />
      </main>
    </div>
  )
}
