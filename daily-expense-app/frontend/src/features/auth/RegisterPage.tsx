import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { axiosClient } from '@/lib/axiosClient'
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

const registerSchema = z.object({
  fullName: z.string().min(1, 'Full name is required'),
  email: z.string().email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
})

type RegisterValues = z.infer<typeof registerSchema>

export default function RegisterPage() {
  const [success, setSuccess] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)

  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '' },
  })

  async function onSubmit(values: RegisterValues) {
    setApiError(null)
    try {
      await axiosClient.post('/api/v1/auth/register', values)
      setSuccess(true)
    } catch {
      setApiError('Registration failed. The email may already be in use.')
    }
  }

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <Card className="w-full max-w-sm">
          <CardContent className="pt-6 space-y-4">
            <Alert role="status">
              <AlertDescription>
                Account created! A verification link will be sent to your email once the
                notification service is enabled.
              </AlertDescription>
            </Alert>
            <p className="text-sm text-center text-muted-foreground">
              Have a verification token?{' '}
              <Link to="/verify-email" className="text-primary underline-offset-4 hover:underline">
                Verify email
              </Link>
            </p>
            <p className="text-sm text-center">
              <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                Back to sign in
              </Link>
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl font-bold">Create account</CardTitle>
          <CardDescription>Start tracking your expenses</CardDescription>
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
                name="fullName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Full name</FormLabel>
                    <FormControl>
                      <Input placeholder="Asha Rao" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
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
                {form.formState.isSubmitting ? 'Creating account…' : 'Create account'}
              </Button>
            </form>
          </Form>
          <div className="mt-4 text-center text-sm">
            Already have an account?{' '}
            <Link to="/login" className="text-primary underline-offset-4 hover:underline">
              Sign in
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
