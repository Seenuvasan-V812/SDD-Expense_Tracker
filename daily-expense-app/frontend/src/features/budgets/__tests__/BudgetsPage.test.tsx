import { describe, it, expect, beforeAll, afterEach, afterAll, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import BudgetsPage from '../BudgetsPage'

vi.mock('@/lib/apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))

const mockBudget = {
  budgetId: 'bud-1',
  scope: 'OVERALL',
  categoryId: null,
  limit: { amount: '10000.00', currency: 'INR' },
  period: 'MONTHLY',
  active: true,
  rolloverEnabled: false,
  periodWindow: { startDate: '2026-06-01', endDate: '2026-06-30' },
  carriedIn: { amount: '0.00', currency: 'INR' },
  spent: { amount: '7500.00', currency: 'INR' },
  remaining: { amount: '2500.00', currency: 'INR' },
  percentUsed: 75,
  firedThresholds: [],
}

const server = setupServer(
  http.get('http://test-api/api/v1/budgets', () =>
    HttpResponse.json({
      content: [mockBudget],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    }),
  ),
  http.patch('http://test-api/api/v1/budgets/:id/activation', () =>
    HttpResponse.json({ ...mockBudget, active: false }),
  ),
  http.patch('http://test-api/api/v1/budgets/:id/rollover', () =>
    HttpResponse.json({ ...mockBudget, rolloverEnabled: true }),
  ),
  http.get('http://test-api/api/v1/categories', () =>
    HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  ),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <BudgetsPage />
    </QueryClientProvider>,
  )
}

describe('BudgetsPage', () => {
  it('shows loading state initially', () => {
    server.use(
      http.get('http://test-api/api/v1/budgets', () => new Promise<Response>(() => {})),
    )
    renderPage()
    expect(screen.getByLabelText(/loading/i)).toBeInTheDocument()
  })

  it('renders budget status card with set/spent/remaining/percentUsed', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText(/overall/i)).toBeInTheDocument())
    expect(screen.getByText(/75/)).toBeInTheDocument()
    expect(screen.getByText(/monthly/i)).toBeInTheDocument()
  })

  it('shows empty state when no budgets', async () => {
    server.use(
      http.get('http://test-api/api/v1/budgets', () =>
        HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      ),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/no budgets/i)).toBeInTheDocument())
  })

  it('shows error state on fetch failure', async () => {
    server.use(
      http.get('http://test-api/api/v1/budgets', () => new HttpResponse(null, { status: 500 })),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/failed to load/i)).toBeInTheDocument())
  })

  it('budget form validates limit > 0', async () => {
    renderPage()
    await waitFor(() => screen.getByRole('button', { name: /add budget/i }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /add budget/i }))

    const submitBtn = await screen.findByRole('button', { name: /create budget/i })
    await user.click(submitBtn)

    await waitFor(() =>
      expect(screen.getByText(/limit must be/i)).toBeInTheDocument(),
    )
  })

  it('renders activation switch for budget', async () => {
    renderPage()
    await waitFor(() => screen.getByText(/overall/i))
    expect(screen.getByRole('switch', { name: /active/i })).toBeInTheDocument()
  })

  it('renders rollover switch for budget', async () => {
    renderPage()
    await waitFor(() => screen.getByText(/overall/i))
    expect(screen.getByRole('switch', { name: /rollover/i })).toBeInTheDocument()
  })
})
