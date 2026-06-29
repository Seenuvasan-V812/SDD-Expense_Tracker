import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { axiosClient } from '@/lib/axiosClient'
import { Card, CardContent } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle } from 'lucide-react'

type Status = 'loading' | 'success' | 'error'

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [status, setStatus] = useState<Status>('loading')

  useEffect(() => {
    if (!token) {
      setStatus('error')
      return
    }
    axiosClient
      .get(`/api/v1/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(() => setStatus('success'))
      .catch(() => setStatus('error'))
  }, [token])

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardContent className="pt-6">
          {status === 'loading' && (
            <p className="text-sm text-muted-foreground" role="status" aria-busy="true">
              Verifying your email…
            </p>
          )}
          {status === 'success' && (
            <Alert role="status">
              <AlertDescription>
                Email verified!{' '}
                <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                  Sign in
                </Link>
              </AlertDescription>
            </Alert>
          )}
          {status === 'error' && (
            <Alert variant="destructive" role="alert">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>
                Verification failed. The link may have expired.{' '}
                <Link to="/register" className="underline-offset-4 hover:underline">
                  Register again
                </Link>
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
