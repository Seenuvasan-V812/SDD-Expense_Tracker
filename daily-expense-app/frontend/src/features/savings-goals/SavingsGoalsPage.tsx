import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
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
import ContributionForm from './ContributionForm'
import { fetchGoals } from './api'
import type { SavingsGoalResponse, GoalStatus } from './types'

const STATUS_COLORS: Record<GoalStatus, string> = {
  ACTIVE: 'default',
  PAUSED: 'secondary',
  COMPLETED: 'outline',
  ABANDONED: 'destructive',
}

export default function SavingsGoalsPage() {
  const qc = useQueryClient()
  const [activePage, setActivePage] = useState(0)
  const [completedPage, setCompletedPage] = useState(0)
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
            <span className="text-xs text-muted-foreground">{pct}%</span>
          </div>
        )
      },
    },
    {
      id: 'remaining',
      header: 'Remaining',
      cell: ({ row }) => (
        <MoneyDisplay amount={parseFloat(row.original.remainingAmount.amount)} />
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
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <Badge variant={STATUS_COLORS[row.original.status] as 'default' | 'secondary' | 'outline' | 'destructive'}>
          {row.original.status}
        </Badge>
      ),
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => {
        const goal = row.original
        if (goal.status !== 'ACTIVE') return null
        return (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setContribTarget(goal)}
            aria-label="Add contribution"
          >
            Add contribution
          </Button>
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
        <Button onClick={() => qc.invalidateQueries({ queryKey: ['savings-goals'] })}>
          <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
          New Goal
        </Button>
      </div>

      <Tabs defaultValue="active">
        <TabsList>
          <TabsTrigger value="active">Active</TabsTrigger>
          <TabsTrigger value="completed">Completed</TabsTrigger>
        </TabsList>

        <TabsContent value="active">
          {!activeData?.content.length ? (
            <EmptyState message="No savings goals found." />
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
