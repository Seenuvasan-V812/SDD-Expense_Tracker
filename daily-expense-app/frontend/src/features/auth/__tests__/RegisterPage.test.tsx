import { describe, it, expect, beforeAll, afterEach, afterAll, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import RegisterPage from '../RegisterPage'

vi.mock('@/lib/apiConfig', () => ({ API_BASE_URL: 'http://test-api' }))

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderRegister() {
  return render(
    <MemoryRouter>
      <RegisterPage />
    </MemoryRouter>,
  )
}

describe('RegisterPage', () => {
  it('shows loading state while submitting', async () => {
    let releaseRegister: () => void = () => {}
    server.use(
      http.post('http://test-api/api/v1/auth/register', (): Promise<Response> =>
        new Promise((resolve) => {
          releaseRegister = () => resolve(new HttpResponse(null, { status: 201 }))
        }),
      ),
    )
    renderRegister()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/full name/i), 'Asha Rao')
    await user.type(screen.getByLabelText(/email/i), 'asha@example.in')
    await user.type(screen.getByLabelText(/password/i), 'SecurePass1!')
    const button = screen.getByRole('button', { name: /create account/i })
    void user.click(button)
    await waitFor(() => expect(button).toHaveAttribute('aria-busy', 'true'))
    releaseRegister()
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /create account/i })).not.toBeInTheDocument(),
    )
  })

  it('shows success state after registration', async () => {
    server.use(
      http.post('http://test-api/api/v1/auth/register', () =>
        new HttpResponse(null, { status: 201 }),
      ),
    )
    renderRegister()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/full name/i), 'Asha Rao')
    await user.type(screen.getByLabelText(/email/i), 'asha@example.in')
    await user.type(screen.getByLabelText(/password/i), 'SecurePass1!')
    await user.click(screen.getByRole('button', { name: /create account/i }))
    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(/check your email/i),
    )
  })

  it('shows error state on 409 conflict', async () => {
    server.use(
      http.post('http://test-api/api/v1/auth/register', () =>
        new HttpResponse(null, { status: 409 }),
      ),
    )
    renderRegister()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/full name/i), 'Asha Rao')
    await user.type(screen.getByLabelText(/email/i), 'asha@example.in')
    await user.type(screen.getByLabelText(/password/i), 'SecurePass1!')
    await user.click(screen.getByRole('button', { name: /create account/i }))
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/registration failed/i),
    )
  })

  it('validates password length client-side', async () => {
    renderRegister()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/full name/i), 'Asha Rao')
    await user.type(screen.getByLabelText(/email/i), 'asha@example.in')
    await user.type(screen.getByLabelText(/password/i), 'short')
    await user.click(screen.getByRole('button', { name: /create account/i }))
    await waitFor(() =>
      expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument(),
    )
  })
})
