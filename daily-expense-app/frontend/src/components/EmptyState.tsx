import type { ReactNode } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { PackageOpen } from 'lucide-react'

interface EmptyStateProps {
  message?: string
  icon?: ReactNode
  actionLabel?: string
  onAction?: () => void
}

export default function EmptyState({
  message = 'No items found.',
  icon,
  actionLabel,
  onAction,
}: EmptyStateProps) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center justify-center py-12 text-center space-y-4">
        <div className="text-muted-foreground">
          {icon ?? <PackageOpen className="h-10 w-10" />}
        </div>
        <p className="text-sm text-muted-foreground">{message}</p>
        {actionLabel && onAction && (
          <Button variant="outline" size="sm" onClick={onAction}>
            {actionLabel}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}
