import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { Plus, Trash2, Pencil, Download, Upload } from 'lucide-react'
import LoadingState from '@/components/LoadingState'
import ErrorState from '@/components/ErrorState'
import EmptyState from '@/components/EmptyState'
import PaginatedTable from '@/components/PaginatedTable'
import MoneyDisplay from '@/components/MoneyDisplay'
import DateDisplay from '@/components/DateDisplay'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import ExpenseForm from './ExpenseForm'
import { fetchExpenses, deleteExpense, importExpenses, exportExpensesUrl } from './api'
import type { ExpenseResponse, ExpenseFilters } from './types'

export default function ExpensesPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<ExpenseFilters>({})
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<ExpenseResponse | undefined>()

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['expenses', filters, page],
    queryFn: () => fetchExpenses({ ...filters, page }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteExpense,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['expenses'] }),
  })

  const openCreate = () => {
    setEditTarget(undefined)
    setDialogOpen(true)
  }

  const openEdit = (exp: ExpenseResponse) => {
    setEditTarget(exp)
    setDialogOpen(true)
  }

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    await importExpenses(file)
    void qc.invalidateQueries({ queryKey: ['expenses'] })
    e.target.value = ''
  }

  const handleExport = () => {
    const from = filters.from ?? new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10)
    const to = filters.to ?? new Date().toISOString().slice(0, 10)
    window.open(exportExpensesUrl(from, to), '_blank')
  }

  const columns: ColumnDef<ExpenseResponse>[] = [
    {
      accessorKey: 'date',
      header: 'Date',
      cell: ({ row }) => <DateDisplay dateStr={row.original.date} />,
    },
    {
      accessorKey: 'description',
      header: 'Description',
      cell: ({ row }) => <span>{row.original.description ?? '—'}</span>,
    },
    {
      accessorKey: 'amount',
      header: 'Amount',
      cell: ({ row }) => (
        <MoneyDisplay amount={parseFloat(row.original.amount.amount)} />
      ),
    },
    {
      accessorKey: 'paymentMethod',
      header: 'Method',
      cell: ({ row }) => (
        <Badge variant="outline">{row.original.paymentMethod}</Badge>
      ),
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => {
        const exp = row.original
        return (
          <div className="flex gap-2">
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Edit expense`}
              onClick={() => openEdit(exp)}
            >
              <Pencil className="h-4 w-4" aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Delete expense`}
              onClick={() => deleteMutation.mutate(exp.expenseId)}
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        )
      },
    },
  ]

  if (isLoading) return <LoadingState />
  if (isError)
    return (
      <ErrorState
        message="Failed to load expenses"
        onRetry={() => void refetch()}
      />
    )

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h1 className="text-2xl font-semibold">Expenses</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={handleExport}>
            <Download className="mr-1 h-4 w-4" aria-hidden="true" />
            Export
          </Button>
          <label htmlFor="import-csv">
            <Button variant="outline" size="sm" asChild>
              <span>
                <Upload className="mr-1 h-4 w-4" aria-hidden="true" />
                Import CSV
              </span>
            </Button>
          </label>
          <input
            id="import-csv"
            type="file"
            accept=".csv"
            className="hidden"
            onChange={handleImport}
          />
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
            Add Expense
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Input
          type="date"
          aria-label="From date"
          className="w-40"
          value={filters.from ?? ''}
          onChange={(e) => {
            setFilters((f) => ({ ...f, from: e.target.value || undefined }))
            setPage(0)
          }}
        />
        <Input
          type="date"
          aria-label="To date"
          className="w-40"
          value={filters.to ?? ''}
          onChange={(e) => {
            setFilters((f) => ({ ...f, to: e.target.value || undefined }))
            setPage(0)
          }}
        />
      </div>

      {!data?.content.length ? (
        <EmptyState
          message="No expenses found."
          actionLabel="Add Expense"
          onAction={openCreate}
        />
      ) : (
        <PaginatedTable data={data} columns={columns} onPageChange={setPage} />
      )}

      <ExpenseForm
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        expense={editTarget}
      />
    </div>
  )
}
