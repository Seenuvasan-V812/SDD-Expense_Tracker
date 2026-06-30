import { describe, it, expect, beforeAll, afterEach, afterAll, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import LoginPage from '../LoginPage'

vi.mock('@/lib/apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <LoginPage />
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('renders email and password fields', () => {
    renderLogin()
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
  })

  it('shows loading state while submitting', async () => {
    let releaseLogin: () => void = () => {}
    server.use(
      http.post('http://test-api/api/v1/auth/login', (): Promise<Response> =>
        new Promise((resolve) => {
          releaseLogin = () =>
            resolve(
              HttpResponse.json({
                accessToken: 'tok',
                refreshToken: 'r',
                tokenType: 'Bearer',
                expiresInSec: 900,
              }),
            )
        }),
      ),
    )
    renderLogin()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/email/i), 'a@b.com')
    await user.type(screen.getByLabelText(/password/i), 'pass1234')
    const button = screen.getByRole('button', { name: /sign in/i })
    void user.click(button)
    await waitFor(() => expect(button).toHaveAttribute('aria-busy', 'true'))
    releaseLogin()
    await waitFor(() => expect(button).not.toHaveAttribute('aria-busy', 'true'))
  })

  it('shows error state on 401', async () => {
    server.use(
      http.post('http://test-api/api/v1/auth/login', () =>
        new HttpResponse(null, { status: 401 }),
      ),
    )
    renderLogin()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/email/i), 'a@b.com')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid credentials/i),
    )
  })

  it('shows validation error for empty email before submit', async () => {
    renderLogin()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => expect(screen.getByText(/valid email/i)).toBeInTheDocument())
  })
})
