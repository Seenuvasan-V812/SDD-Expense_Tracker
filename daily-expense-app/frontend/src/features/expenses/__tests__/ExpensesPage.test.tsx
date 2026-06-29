import { describe, it, expect, beforeAll, afterEach, afterAll, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ExpensesPage from '../ExpensesPage'

vi.mock('@/lib/apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))

const mockExpensePage = {
  content: [
    {
      expenseId: 'exp-1',
      amount: { amount: '450.00', currency: 'INR' },
      date: '2026-06-25',
      categoryId: 'cat-1',
      paymentMethod: 'UPI',
      description: 'Lunch',
      merchant: null,
      notes: null,
      tags: [],
      hasReceipt: false,
      savingsGoalId: null,
      recurringExpenseId: null,
      createdAt: '2026-06-25T10:00:00+05:30',
      updatedAt: '2026-06-25T10:00:00+05:30',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

const server = setupServer(
  http.get('http://test-api/api/v1/expenses', () => HttpResponse.json(mockExpensePage)),
  http.get('http://test-api/api/v1/categories', () =>
    HttpResponse.json({
      content: [
        { categoryId: 'cat-1', name: 'Food', type: 'EXPENSE', origin: 'DEFAULT', systemRole: 'NONE', icon: null, color: null, deletable: false },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    }),
  ),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <ExpensesPage />
    </QueryClientProvider>,
  )
}

describe('ExpensesPage', () => {
  it('shows loading state initially', () => {
    server.use(
      http.get('http://test-api/api/v1/expenses', () => new Promise<Response>(() => {})),
    )
    renderPage()
    expect(screen.getByLabelText(/loading/i)).toBeInTheDocument()
  })

  it('renders expense list with amount and date', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Lunch')).toBeInTheDocument())
    expect(screen.getByText(/₹/)).toBeInTheDocument()
    expect(screen.getByText(/Jun 2026/i)).toBeInTheDocument()
  })

  it('shows empty state when no expenses', async () => {
    server.use(
      http.get('http://test-api/api/v1/expenses', () =>
        HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      ),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/no expenses/i)).toBeInTheDocument())
  })

  it('shows error state on fetch failure', async () => {
    server.use(
      http.get('http://test-api/api/v1/expenses', () => new HttpResponse(null, { status: 500 })),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/failed to load/i)).toBeInTheDocument())
  })

  it('shows Add Expense button', async () => {
    renderPage()
    await waitFor(() => screen.getByText(/add expense/i))
    expect(screen.getByRole('button', { name: /add expense/i })).toBeInTheDocument()
  })

  it('expense form validates required fields before submit', async () => {
    renderPage()
    await waitFor(() => screen.getByRole('button', { name: /add expense/i }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /add expense/i }))

    const submitBtn = await screen.findByRole('button', { name: /save expense/i })
    await user.click(submitBtn)

    await waitFor(() =>
      expect(screen.getByText(/amount must be/i)).toBeInTheDocument(),
    )
  })

  it('receipt pre-validates file size >5MB before submit', async () => {
    renderPage()
    await waitFor(() => screen.getByRole('button', { name: /add expense/i }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /add expense/i }))

    await screen.findByRole('button', { name: /save expense/i })
    const receiptInput = screen.getByLabelText(/receipt/i)

    const bigFile = new File(['x'.repeat(6 * 1024 * 1024)], 'receipt.jpg', {
      type: 'image/jpeg',
    })
    Object.defineProperty(bigFile, 'size', { value: 6 * 1024 * 1024 })
    await user.upload(receiptInput, bigFile)

    await waitFor(() =>
      expect(screen.getByText(/5 mb/i)).toBeInTheDocument(),
    )
  })

  it('receipt pre-validates wrong MIME type before submit', async () => {
    renderPage()
    await waitFor(() => screen.getByRole('button', { name: /add expense/i }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /add expense/i }))

    await screen.findByRole('button', { name: /save expense/i })
    const receiptInput = screen.getByLabelText(/receipt/i)

    const badFile = new File(['data'], 'doc.pdf', { type: 'application/pdf' })
    // Use fireEvent to bypass the browser accept filter — tests handler validation
    fireEvent.change(receiptInput, { target: { files: [badFile] } })

    await waitFor(() =>
      expect(screen.getByText(/jpeg.*png.*webp/i)).toBeInTheDocument(),
    )
  })
})
