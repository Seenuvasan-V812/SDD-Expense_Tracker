import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import LoadingState from '@/components/LoadingState'
import ErrorState from '@/components/ErrorState'
import EmptyState from '@/components/EmptyState'
import PaginatedTable from '@/components/PaginatedTable'
import MoneyDisplay from '@/components/MoneyDisplay'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import GoalForm from './GoalForm'
import ContributionForm from './ContributionForm'
import { fetchGoals, updateGoalStatus } from './api'
import type { SavingsGoalResponse, GoalStatus } from './types'

const STATUS_BADGE: Record<GoalStatus, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  ACTIVE: 'default',
  PAUSED: 'secondary',
  COMPLETED: 'outline',
  ABANDONED: 'destructive',
}

export default function SavingsGoalsPage() {
  const qc = useQueryClient()
  const [activePage, setActivePage] = useState(0)
  const [completedPage, setCompletedPage] = useState(0)
  const [goalFormOpen, setGoalFormOpen] = useState(false)
  const [contribTarget, setContribTarget] = useState<SavingsGoalResponse | null>(null)

  const {
    data: activeData,
    isLoading: activeLoading,
    isError: activeError,
    refetch: refetchActive,
  } = useQuery({
    queryKey: ['savings-goals', 'ACTIVE', activePage],
    queryFn: () => fetchGoals('ACTIVE', activePage),
  })

  const {
    data: completedData,
    isLoading: completedLoading,
    isError: completedError,
    refetch: refetchCompleted,
  } = useQuery({
    queryKey: ['savings-goals', 'COMPLETED', completedPage],
    queryFn: () => fetchGoals('COMPLETED', completedPage),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: GoalStatus }) =>
      updateGoalStatus(id, status),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['savings-goals'] })
    },
  })

  const goalColumns: ColumnDef<SavingsGoalResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    {
      accessorKey: 'percentAchieved',
      header: 'Progress',
      cell: ({ row }) => {
        const pct = row.original.percentAchieved
        return (
          <div className="space-y-1 min-w-[120px]">
            <Progress value={Math.min(pct, 100)} className="h-2" aria-label={`${pct}% achieved`} />
            <span className="text-xs text-muted-foreground">{pct.toFixed(1)}%</span>
          </div>
        )
      },
    },
    {
      id: 'contributed',
      header: 'Contributed',
      cell: ({ row }) => (
        <MoneyDisplay amount={parseFloat(row.original.totalContributed.amount)} />
      ),
    },
    {
      id: 'target',
      header: 'Target',
      cell: ({ row }) => (
        <MoneyDisplay amount={parseFloat(row.original.targetAmount.amount)} />
      ),
    },
    {
      id: 'remaining',
      header: 'Remaining',
      cell: ({ row }) => (
        <MoneyDisplay amount={parseFloat(row.original.remainingAmount.amount)} />
      ),
    },
    {
      accessorKey: 'targetDate',
      header: 'Target Date',
      cell: ({ row }) => row.original.targetDate ?? '—',
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <Badge variant={STATUS_BADGE[row.original.status]}>
          {row.original.status}
        </Badge>
      ),
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => {
        const goal = row.original
        return (
          <div className="flex gap-1 flex-wrap">
            {goal.status === 'ACTIVE' && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setContribTarget(goal)}
                >
                  Contribute
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    statusMutation.mutate({ id: goal.savingsGoalId, status: 'PAUSED' })
                  }
                >
                  Pause
                </Button>
              </>
            )}
            {goal.status === 'PAUSED' && (
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  statusMutation.mutate({ id: goal.savingsGoalId, status: 'ACTIVE' })
                }
              >
                Resume
              </Button>
            )}
          </div>
        )
      },
    },
  ]

  if (activeLoading) return <LoadingState />
  if (activeError)
    return (
      <ErrorState
        message="Failed to load savings goals"
        onRetry={() => {
          void refetchActive()
          void refetchCompleted()
        }}
      />
    )

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Savings Goals</h1>
        <Button onClick={() => setGoalFormOpen(true)}>
          <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
          New Goal
        </Button>
      </div>

      <Tabs defaultValue="active">
        <TabsList>
          <TabsTrigger value="active">
            Active{activeData?.totalElements ? ` (${activeData.totalElements})` : ''}
          </TabsTrigger>
          <TabsTrigger value="completed">Completed</TabsTrigger>
        </TabsList>

        <TabsContent value="active">
          {!activeData?.content.length ? (
            <EmptyState
              message="No active savings goals."
              actionLabel="Create your first goal"
              onAction={() => setGoalFormOpen(true)}
            />
          ) : (
            <PaginatedTable
              data={activeData}
              columns={goalColumns}
              onPageChange={setActivePage}
            />
          )}
        </TabsContent>

        <TabsContent value="completed">
          {completedLoading ? (
            <LoadingState />
          ) : completedError ? (
            <ErrorState
              message="Failed to load completed goals"
              onRetry={() => void refetchCompleted()}
            />
          ) : !completedData?.content.length ? (
            <EmptyState message="No completed goals yet." />
          ) : (
            <PaginatedTable
              data={completedData}
              columns={goalColumns}
              onPageChange={setCompletedPage}
            />
          )}
        </TabsContent>
      </Tabs>

      <GoalForm open={goalFormOpen} onOpenChange={setGoalFormOpen} />

      {contribTarget && (
        <ContributionForm
          open={!!contribTarget}
          onOpenChange={(open) => !open && setContribTarget(null)}
          goalId={contribTarget.savingsGoalId}
          goalName={contribTarget.name}
        />
      )}
    </div>
  )
}
