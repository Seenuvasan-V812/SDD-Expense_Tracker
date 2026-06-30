import { describe, it, expect, beforeAll, afterEach, afterAll, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import CategoriesPage from '../CategoriesPage'

vi.mock('@/lib/apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))

const defaultCategories = {
  content: [
    {
      categoryId: 'cat-1',
      name: 'Food',
      type: 'EXPENSE',
      origin: 'DEFAULT',
      systemRole: 'NONE',
      icon: null,
      color: null,
      deletable: false,
    },
    {
      categoryId: 'cat-2',
      name: 'My Category',
      type: 'EXPENSE',
      origin: 'CUSTOM',
      systemRole: 'NONE',
      icon: null,
      color: null,
      deletable: true,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
}

const server = setupServer(
  http.get('http://test-api/api/v1/categories', () =>
    HttpResponse.json(defaultCategories),
  ),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <CategoriesPage />
    </QueryClientProvider>,
  )
}

describe('CategoriesPage', () => {
  it('shows loading state initially', () => {
    server.use(
      http.get('http://test-api/api/v1/categories', () => new Promise<Response>(() => {})),
    )
    renderPage()
    expect(screen.getByLabelText(/loading/i)).toBeInTheDocument()
  })

  it('renders categories list', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Food')).toBeInTheDocument())
    expect(screen.getByText('My Category')).toBeInTheDocument()
  })

  it('DEFAULT category (deletable=false) has no delete button', async () => {
    renderPage()
    await waitFor(() => screen.getByText('Food'))
    expect(screen.queryByRole('button', { name: /delete food/i })).not.toBeInTheDocument()
  })

  it('CUSTOM category has a delete button', async () => {
    renderPage()
    await waitFor(() => screen.getByText('My Category'))
    expect(screen.getByRole('button', { name: /delete my category/i })).toBeInTheDocument()
  })

  it('shows empty state when no categories', async () => {
    server.use(
      http.get('http://test-api/api/v1/categories', () =>
        HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      ),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/no categories/i)).toBeInTheDocument())
  })

  it('shows error state on fetch failure', async () => {
    server.use(
      http.get('http://test-api/api/v1/categories', () => new HttpResponse(null, { status: 500 })),
    )
    renderPage()
    await waitFor(() => expect(screen.getByText(/failed to load/i)).toBeInTheDocument())
  })

  it('type filter sends ?type param to API', async () => {
    let capturedType: string | null = null
    server.use(
      http.get('http://test-api/api/v1/categories', ({ request }) => {
        const url = new URL(request.url)
        capturedType = url.searchParams.get('type')
        return HttpResponse.json(defaultCategories)
      }),
    )
    renderPage()
    await waitFor(() => screen.getByText('Food'))

    const user = userEvent.setup()
    const trigger = screen.getByRole('combobox', { name: /type filter/i })
    await user.click(trigger)
    const option = await screen.findByRole('option', { name: /^income$/i })
    await user.click(option)

    await waitFor(() => expect(capturedType).toBe('INCOME'))
  })
})
