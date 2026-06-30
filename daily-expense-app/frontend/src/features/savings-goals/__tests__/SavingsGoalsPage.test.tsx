import { describe, it, expect, beforeAll, afterEach, afterAll, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import SavingsGoalsPage from '../SavingsGoalsPage'

vi.mock('@/lib/apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))

const activeGoal = {
  savingsGoalId: 'goal-1',
  name: 'Trip to Goa',
  targetAmount: { amount: '50000.00', currency: 'INR' },
  targetDate: '2026-12-31',
  status: 'ACTIVE',
  totalContributed: { amount: '15000.00', currency: 'INR' },
  remainingAmount: { amount: '35000.00', currency: 'INR' },
  percentAchieved: 30,
  projectedCompletionDate: '2026-10-15',
  icon: null,
  color: null,
}

const completedGoal = {
  savingsGoalId: 'goal-2',
  name: 'Emergency Fund',
  targetAmount: { amount: '10000.00', currency: 'INR' },
  targetDate: null,
  status: 'COMPLETED',
  totalContributed: { amount: '10000.00', currency: 'INR' },
  remainingAmount: { amount: '0.00', currency: 'INR' },
  percentAchieved: 100,
  projectedCompletionDate: null,
  icon: null,
  color: null,
}

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

const server = setupServer(
  http.get('http://test-api/api/v1/savings-goals', ({ request }) => {
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    if (status === 'COMPLETED') {
      return HttpResponse.json({ content: [completedGoal], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    }
    return HttpResponse.json({ content: [activeGoal], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  }),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <SavingsGoalsPage />
    </QueryClientProvider>,
  )
}

describe('SavingsGoalsPage', () => {
  it('shows loading state initially', () => {
    server.use(
      http.get('http://test-api/api/v1/savings-goals', () => new Promise<Response>(() => {})),
    )
    renderPage()
    expect(screen.getByLabelText(/loading/i)).toBeInTheDocument()
  })

  it('shows ACTIVE goals in the ACTIVE tab', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Trip to Goa')).toBeInTheDocument())
  })

  it('shows remaining amount and percent achieved', async () => {
    renderPage()
    await waitFor(() => screen.getByText('Trip to Goa'))
    expect(screen.getByText(/30%/i)).toBeInTheDocument()
    expect(screen.getAllByText(/₹/).length).toBeGreaterThan(0)
  })

  it('switches to COMPLETED tab and shows completed goals', async () => {
    renderPage()
    await waitFor(() => screen.getByText('Trip to Goa'))

    const user = userEvent.setup()
    const completedTab = screen.getByRole('tab', { name: /completed/i })
    await user.click(completedTab)

    await waitFor(() =>
      expect(screen.getByText('Emergency Fund')).toBeInTheDocument(),
    )
  })

  it('shows empty state when no active goals', async () => {
    server.use(
      http.get('http://test-api/api/v1/savings-goals', () =>
        HttpResponse.json(emptyPage),
      ),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/no savings goals/i)).toBeInTheDocument())
  })

  it('shows error state on fetch failure', async () => {
    server.use(
      http.get('http://test-api/api/v1/savings-goals', () => new HttpResponse(null, { status: 500 })),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/failed to load/i)).toBeInTheDocument())
  })

  it('contribution form validates amount > 0', async () => {
    renderPage()
    await waitFor(() => screen.getByText('Trip to Goa'))

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /add contribution/i }))

    const submitBtn = await screen.findByRole('button', { name: /record contribution/i })
    await user.click(submitBtn)

    await waitFor(() =>
      expect(screen.getByText(/amount.*greater than 0/i)).toBeInTheDocument(),
    )
  })
})
