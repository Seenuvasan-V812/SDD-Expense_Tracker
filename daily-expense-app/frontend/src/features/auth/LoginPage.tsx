import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { axiosClient } from '@/lib/axiosClient'
import { setTokens } from './authStore'
import type { AuthTokenResponse } from '@/types/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle } from 'lucide-react'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'

const loginSchema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
})

type LoginValues = z.infer<typeof loginSchema>

export default function LoginPage() {
  const navigate = useNavigate()
  const [apiError, setApiError] = useState<string | null>(null)

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  async function onSubmit(values: LoginValues) {
    setApiError(null)
    try {
      const response = await axiosClient.post<AuthTokenResponse>(
        '/api/v1/auth/login',
        values,
      )
      setTokens(response.data.accessToken)
      navigate('/')
    } catch {
      setApiError('Invalid credentials. Please try again.')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl font-bold">Sign in</CardTitle>
          <CardDescription>Enter your credentials to continue</CardDescription>
        </CardHeader>
        <CardContent>
          {apiError && (
            <Alert variant="destructive" className="mb-4" role="alert">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{apiError}</AlertDescription>
            </Alert>
          )}
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" placeholder="you@example.com" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input type="password" placeholder="••••••••" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button
                type="submit"
                className="w-full"
                disabled={form.formState.isSubmitting}
                aria-busy={form.formState.isSubmitting}
              >
                {form.formState.isSubmitting ? 'Signing in…' : 'Sign in'}
              </Button>
            </form>
          </Form>
          <div className="mt-4 text-center text-sm">
            <Link to="/forgot-password" className="text-primary underline-offset-4 hover:underline">
              Forgot password?
            </Link>
          </div>
          <div className="mt-2 text-center text-sm">
            No account?{' '}
            <Link to="/register" className="text-primary underline-offset-4 hover:underline">
              Register
            </Link>
          </div>
          <div className="mt-2 text-center text-sm">
            <Link to="/verify-email" className="text-muted-foreground underline-offset-4 hover:underline text-xs">
              Verify email with token
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
