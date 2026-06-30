import { useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
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

const resetSchema = z
  .object({
    password: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  })

type ResetValues = z.infer<typeof resetSchema>

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [success, setSuccess] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)

  const form = useForm<ResetValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { password: '', confirmPassword: '' },
  })

  async function onSubmit(values: ResetValues) {
    setApiError(null)
    try {
      await axiosClient.post('/api/v1/auth/reset-password', {
        token,
        newPassword: values.password,
      })
      setSuccess(true)
    } catch {
      setApiError('Reset failed. The link may have expired or already been used.')
    }
  }

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <Card className="w-full max-w-sm">
          <CardContent className="pt-6">
            <Alert role="status">
              <AlertDescription>
                Password reset!{' '}
                <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                  Sign in
                </Link>
              </AlertDescription>
            </Alert>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl font-bold">Reset password</CardTitle>
          <CardDescription>Choose a new password for your account.</CardDescription>
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
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>New password</FormLabel>
                    <FormControl>
                      <Input type="password" placeholder="••••••••" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="confirmPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Confirm password</FormLabel>
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
                {form.formState.isSubmitting ? 'Resetting…' : 'Reset password'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
