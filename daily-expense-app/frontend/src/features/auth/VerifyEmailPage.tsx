import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { axiosClient } from '@/lib/axiosClient'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, CheckCircle2, Mail } from 'lucide-react'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
})
type FormValues = z.infer<typeof schema>

type Status = 'idle' | 'success' | 'error'

export default function VerifyEmailPage() {
  const [status, setStatus] = useState<Status>('idle')
  const [errorMsg, setErrorMsg] = useState<string>('')

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '' },
  })

  async function onSubmit({ email }: FormValues) {
    setStatus('idle')
    try {
      await axiosClient.post('/api/v1/auth/verify-email/by-email', { email })
      setStatus('success')
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 404) {
        setErrorMsg('No account found with that email address.')
      } else if (status === 409) {
        setErrorMsg('This account is already verified or cannot be activated.')
      } else {
        setErrorMsg('Verification failed. Please try again.')
      }
      setStatus('error')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-xl font-bold flex items-center gap-2">
            <Mail className="h-5 w-5" />
            Verify Your Email
          </CardTitle>
          <CardDescription>
            Enter the email address you registered with to activate your account.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {status === 'success' ? (
            <>
              <Alert role="status">
                <CheckCircle2 className="h-4 w-4" />
                <AlertDescription>
                  Account verified successfully! You can now sign in.
                </AlertDescription>
              </Alert>
              <div className="text-center">
                <Link
                  to="/login"
                  className="text-primary underline-offset-4 hover:underline font-medium text-sm"
                >
                  Go to Sign In
                </Link>
              </div>
            </>
          ) : (
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                  control={form.control}
                  name="email"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Email Address</FormLabel>
                      <FormControl>
                        <Input
                          type="email"
                          placeholder="you@example.com"
                          autoFocus
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {status === 'error' && (
                  <Alert variant="destructive" role="alert">
                    <AlertCircle className="h-4 w-4" />
                    <AlertDescription>{errorMsg}</AlertDescription>
                  </Alert>
                )}

                <Button
                  type="submit"
                  className="w-full"
                  disabled={form.formState.isSubmitting}
                  aria-busy={form.formState.isSubmitting}
                >
                  {form.formState.isSubmitting ? 'Verifying…' : 'Verify Email'}
                </Button>
              </form>
            </Form>
          )}

          <div className="text-center text-sm pt-1">
            <Link to="/login" className="text-muted-foreground underline-offset-4 hover:underline">
              Back to sign in
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
