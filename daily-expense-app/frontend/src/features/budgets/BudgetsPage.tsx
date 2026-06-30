import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import LoadingState from '@/components/LoadingState'
import ErrorState from '@/components/ErrorState'
import EmptyState from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import BudgetForm from './BudgetForm'
import { fetchBudgets, patchActivation, patchRollover } from './api'
import { fetchCategories } from '@/features/categories/api'
import type { BudgetResponse } from './types'

interface BudgetCardProps {
  budget: BudgetResponse
  categoryName: string | null
  onActivationChange: (active: boolean) => void
  onRolloverChange: (enabled: boolean) => void
}

function BudgetCard({
  budget,
  categoryName,
  onActivationChange,
  onRolloverChange,
}: BudgetCardProps) {
  const limit = parseFloat(budget.budgetLimit)
  const currency = budget.currency ?? 'INR'

  const scopeLabel =
    budget.scope === 'OVERALL'
      ? 'Overall Budget'
      : `Category: ${categoryName ?? budget.categoryId?.slice(0, 8) ?? '—'}`

  return (
    <Card className={budget.active ? '' : 'opacity-60'}>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="text-base leading-snug">{scopeLabel}</CardTitle>
          <Badge variant="outline">{budget.periodType}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="text-sm space-y-1">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Limit</span>
            <span className="font-semibold">
              {currency} {limit.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </span>
          </div>
          {!budget.active && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Status</span>
              <Badge variant="secondary">Inactive</Badge>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between gap-4 pt-1">
          <div className="flex items-center gap-2">
            <Switch
              id={`active-${budget.id}`}
              checked={budget.active}
              onCheckedChange={onActivationChange}
              aria-label="Active"
            />
            <label htmlFor={`active-${budget.id}`} className="text-sm cursor-pointer">
              Active
            </label>
          </div>
          <div className="flex items-center gap-2">
            <Switch
              id={`rollover-${budget.id}`}
              checked={budget.rolloverEnabled}
              onCheckedChange={onRolloverChange}
              aria-label="Rollover"
            />
            <label htmlFor={`rollover-${budget.id}`} className="text-sm cursor-pointer">
              Rollover
            </label>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

export default function BudgetsPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['budgets', page],
    queryFn: () => fetchBudgets(page),
  })

  // Fetch all categories to resolve names — size=100 covers typical usage
  const { data: catData } = useQuery({
    queryKey: ['categories', 'ALL', 0, 100],
    queryFn: () => fetchCategories({ page: 0, size: 100 }),
    staleTime: 5 * 60 * 1000,
  })

  const categoryMap: Record<string, string> = {}
  catData?.content.forEach((c) => {
    categoryMap[c.categoryId] = c.name
  })

  const activationMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      patchActivation(id, active),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['budgets'] }),
  })

  const rolloverMutation = useMutation({
    mutationFn: ({ id, rolloverEnabled }: { id: string; rolloverEnabled: boolean }) =>
      patchRollover(id, rolloverEnabled),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['budgets'] }),
  })

  if (isLoading) return <LoadingState />
  if (isError)
    return (
      <ErrorState
        message="Failed to load budgets"
        onRetry={() => void refetch()}
      />
    )

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Budgets</h1>
        <Button onClick={() => setDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
          Add Budget
        </Button>
      </div>

      {!data?.content.length ? (
        <EmptyState
          message="No budgets found."
          actionLabel="Add Budget"
          onAction={() => setDialogOpen(true)}
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.content.map((budget) => (
              <BudgetCard
                key={budget.id}
                budget={budget}
                categoryName={
                  budget.categoryId ? (categoryMap[budget.categoryId] ?? null) : null
                }
                onActivationChange={(active) =>
                  activationMutation.mutate({ id: budget.id, active })
                }
                onRolloverChange={(rolloverEnabled) =>
                  rolloverMutation.mutate({ id: budget.id, rolloverEnabled })
                }
              />
            ))}
          </div>
          <div className="flex justify-end gap-2 mt-4">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <span className="text-sm self-center">
              Page {page + 1} of {data.totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </>
      )}

      <BudgetForm open={dialogOpen} onOpenChange={setDialogOpen} />
    </div>
  )
}
