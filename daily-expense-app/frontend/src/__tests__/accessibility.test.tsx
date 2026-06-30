/**
 * T110 — Accessibility + Responsive tests
 * Uses axe-core to check for 0 serious/critical violations.
 * Viewport simulation: sets window.innerWidth before render.
 */
import { describe, it, expect, beforeAll, afterEach, afterAll, vi } from 'vitest'
import { render } from '@testing-library/react'
import { waitFor } from '@testing-library/react'
import axe from 'axe-core'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import CategoriesPage from '@/features/categories/CategoriesPage'
import ExpensesPage from '@/features/expenses/ExpensesPage'
import SavingsGoalsPage from '@/features/savings-goals/SavingsGoalsPage'
import BudgetsPage from '@/features/budgets/BudgetsPage'

vi.mock('@/lib/apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

const server = setupServer(
  http.get('http://test-api/api/v1/categories', () => HttpResponse.json(emptyPage)),
  http.get('http://test-api/api/v1/expenses', () => HttpResponse.json(emptyPage)),
  http.get('http://test-api/api/v1/savings-goals', () => HttpResponse.json(emptyPage)),
  http.get('http://test-api/api/v1/budgets', () => HttpResponse.json(emptyPage)),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function makeQC() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

async function runAxe(container: HTMLElement) {
  const results = await axe.run(container, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa'] },
  })
  return results.violations.filter(
    (v) => v.impact === 'serious' || v.impact === 'critical',
  )
}

async function renderAndWaitEmpty(Component: React.ComponentType) {
  const qc = makeQC()
  const { container } = render(
    <QueryClientProvider client={qc}>
      <Component />
    </QueryClientProvider>,
  )
  await waitFor(() => {
    const el = container.querySelector('[aria-busy="true"]')
    if (el) throw new Error('still loading')
  }, { timeout: 3000 }).catch(() => {})
  return container
}

const VIEWPORTS = [320, 768, 1024]

describe('T110 — Accessibility (axe-core: 0 serious/critical)', () => {
  for (const width of VIEWPORTS) {
    describe(`CategoriesPage @ ${width}px`, () => {
      it(`renders without serious/critical axe violations at ${width}px`, async () => {
        Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: width })
        const container = await renderAndWaitEmpty(CategoriesPage)
        const violations = await runAxe(container)
        expect(violations).toHaveLength(0)
      })
    })

    describe(`ExpensesPage @ ${width}px`, () => {
      it(`renders without serious/critical axe violations at ${width}px`, async () => {
        Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: width })
        const container = await renderAndWaitEmpty(ExpensesPage)
        const violations = await runAxe(container)
        expect(violations).toHaveLength(0)
      })
    })

    describe(`SavingsGoalsPage @ ${width}px`, () => {
      it(`renders without serious/critical axe violations at ${width}px`, async () => {
        Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: width })
        const container = await renderAndWaitEmpty(SavingsGoalsPage)
        const violations = await runAxe(container)
        expect(violations).toHaveLength(0)
      })
    })

    describe(`BudgetsPage @ ${width}px`, () => {
      it(`renders without serious/critical axe violations at ${width}px`, async () => {
        Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: width })
        const container = await renderAndWaitEmpty(BudgetsPage)
        const violations = await runAxe(container)
        expect(violations).toHaveLength(0)
      })
    })
  }
})
