import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Trash2, Plus } from 'lucide-react'
import LoadingState from '@/components/LoadingState'
import ErrorState from '@/components/ErrorState'
import EmptyState from '@/components/EmptyState'
import PaginatedTable from '@/components/PaginatedTable'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import CategoryForm from './CategoryForm'
import { fetchCategories, deleteCategory } from './api'
import type { CategoryResponse, CategoryType } from './types'

type TypeFilter = CategoryType | 'ALL'

export default function CategoriesPage() {
  const qc = useQueryClient()
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL')
  const [page, setPage] = useState(0)
  const [editTarget, setEditTarget] = useState<CategoryResponse | undefined>()
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['categories', typeFilter, page],
    queryFn: () =>
      fetchCategories({
        type: typeFilter === 'ALL' ? undefined : typeFilter,
        page,
      }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteCategory,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['categories'] }),
  })

  const openCreate = () => {
    setEditTarget(undefined)
    setDialogOpen(true)
  }

  const openEdit = (cat: CategoryResponse) => {
    setEditTarget(cat)
    setDialogOpen(true)
  }

  const columns: ColumnDef<CategoryResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    {
      accessorKey: 'type',
      header: 'Type',
      cell: ({ row }) => <Badge variant="outline">{row.original.type}</Badge>,
    },
    {
      accessorKey: 'origin',
      header: 'Origin',
      cell: ({ row }) => (
        <Badge
          variant={row.original.origin === 'DEFAULT' ? 'secondary' : 'default'}
        >
          {row.original.origin}
        </Badge>
      ),
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => {
        const cat = row.original
        return (
          <div className="flex gap-2">
            {cat.origin === 'CUSTOM' && (
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Edit ${cat.name}`}
                onClick={() => openEdit(cat)}
              >
                <Pencil className="h-4 w-4" aria-hidden="true" />
              </Button>
            )}
            {cat.deletable && (
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Delete ${cat.name}`}
                onClick={() => deleteMutation.mutate(cat.categoryId)}
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </Button>
            )}
          </div>
        )
      },
    },
  ]

  if (isLoading) return <LoadingState />
  if (isError)
    return (
      <ErrorState
        message="Failed to load categories"
        onRetry={() => void refetch()}
      />
    )

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Categories</h1>
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
          Add Category
        </Button>
      </div>

      <Select
        value={typeFilter}
        onValueChange={(v) => {
          setTypeFilter(v as TypeFilter)
          setPage(0)
        }}
      >
        <SelectTrigger className="w-48" aria-label="Type filter">
          <SelectValue placeholder="All types" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">All types</SelectItem>
          <SelectItem value="EXPENSE">Expense</SelectItem>
          <SelectItem value="INCOME">Income</SelectItem>
          <SelectItem value="BOTH">Both</SelectItem>
        </SelectContent>
      </Select>

      {!data?.content.length ? (
        <EmptyState
          message="No categories found."
          actionLabel="Add Category"
          onAction={openCreate}
        />
      ) : (
        <PaginatedTable data={data} columns={columns} onPageChange={setPage} />
      )}

      <CategoryForm
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        category={editTarget}
      />
    </div>
  )
}
