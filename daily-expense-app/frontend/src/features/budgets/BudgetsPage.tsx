import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import LoadingState from '@/components/LoadingState'
import ErrorState from '@/components/ErrorState'
import EmptyState from '@/components/EmptyState'
import MoneyDisplay from '@/components/MoneyDisplay'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Switch } from '@/components/ui/switch'
import BudgetForm from './BudgetForm'
import { fetchBudgets, patchActivation, patchRollover } from './api'
import type { BudgetStatusResponse } from './types'

function budgetColor(pct: number): string {
  if (pct > 100) return 'text-danger'
  if (pct >= 80) return 'text-warning'
  return 'text-success'
}

interface BudgetCardProps {
  budget: BudgetStatusResponse
  onActivationChange: (active: boolean) => void
  onRolloverChange: (enabled: boolean) => void
}

function BudgetCard({ budget, onActivationChange, onRolloverChange }: BudgetCardProps) {
  const pct = budget.percentUsed
  const colorClass = budgetColor(pct)

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">
            {budget.scope === 'OVERALL' ? 'Overall' : `Category ${budget.categoryId ?? ''}`}
          </CardTitle>
          <Badge variant="outline">{budget.period}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-2 gap-2 text-sm">
          <div>
            <span className="text-muted-foreground">Set </span>
            <MoneyDisplay amount={parseFloat(budget.limit.amount)} />
          </div>
          <div>
            <span className="text-muted-foreground">Spent </span>
            <MoneyDisplay amount={parseFloat(budget.spent.amount)} />
          </div>
          <div>
            <span className="text-muted-foreground">Remaining </span>
            <MoneyDisplay amount={parseFloat(budget.remaining.amount)} />
          </div>
          <div className={colorClass}>
            <span className="font-semibold tabular-nums">{pct.toFixed(1)}%</span>
            <span className="text-muted-foreground ml-1">used</span>
          </div>
        </div>

        <Progress
          value={Math.min(pct, 100)}
          className="h-2"
          aria-label={`${pct.toFixed(1)}% budget used`}
        />

        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <Switch
              id={`active-${budget.budgetId}`}
              checked={budget.active}
              onCheckedChange={onActivationChange}
              aria-label="Active"
            />
            <label htmlFor={`active-${budget.budgetId}`} className="text-sm">
              Active
            </label>
          </div>
          <div className="flex items-center gap-2">
            <Switch
              id={`rollover-${budget.budgetId}`}
              checked={budget.rolloverEnabled}
              onCheckedChange={onRolloverChange}
              aria-label="Rollover"
            />
            <label htmlFor={`rollover-${budget.budgetId}`} className="text-sm">
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
                key={budget.budgetId}
                budget={budget}
                onActivationChange={(active) =>
                  activationMutation.mutate({ id: budget.budgetId, active })
                }
                onRolloverChange={(rolloverEnabled) =>
                  rolloverMutation.mutate({ id: budget.budgetId, rolloverEnabled })
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
