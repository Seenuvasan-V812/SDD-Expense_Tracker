import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from '@tanstack/react-table'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import type { PageResponse } from '@/types/api'

interface PaginatedTableProps<TData> {
  data: PageResponse<TData>
  columns: ColumnDef<TData>[]
  onPageChange: (page: number) => void
}

export default function PaginatedTable<TData>({
  data,
  columns,
  onPageChange,
}: PaginatedTableProps<TData>) {
  const table = useReactTable({
    data: data.content,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    pageCount: data.totalPages,
  })

  return (
    <div className="space-y-3">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id}>
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length ? (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={columns.length} className="text-center py-6 text-muted-foreground">
                  No results.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>
          Page {data.page + 1} of {data.totalPages} ({data.totalElements} items)
        </span>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => onPageChange(data.page - 1)}
            disabled={data.page === 0}
            aria-label="Previous page"
          >
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => onPageChange(data.page + 1)}
            disabled={data.page >= data.totalPages - 1}
            aria-label="Next page"
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
