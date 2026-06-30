import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { AlertCircle } from 'lucide-react'

interface ErrorStateProps {
  message?: string
  onRetry?: () => void
}

export default function ErrorState({
  message = 'Something went wrong. Please try again.',
  onRetry,
}: ErrorStateProps) {
  return (
    <Alert variant="destructive">
      <AlertCircle className="h-4 w-4" />
      <AlertTitle>Error</AlertTitle>
      <AlertDescription className="mt-1">
        {message}
        {onRetry && (
          <Button
            variant="outline"
            size="sm"
            onClick={onRetry}
            className="ml-3"
            aria-label="Retry"
          >
            Retry
          </Button>
        )}
      </AlertDescription>
    </Alert>
  )
}
