import { Skeleton } from '@/components/ui/skeleton'

interface LoadingStateProps {
  rows?: number
}

export default function LoadingState({ rows = 3 }: LoadingStateProps) {
  return (
    <div aria-busy="true" aria-label="Loading" className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-10 w-full rounded-md" />
      ))}
    </div>
  )
}
