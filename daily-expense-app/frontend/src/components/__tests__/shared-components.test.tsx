import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ColumnDef } from '@tanstack/react-table'
import LoadingState from '../LoadingState'
import ErrorState from '../ErrorState'
import EmptyState from '../EmptyState'
import PaginatedTable from '../PaginatedTable'
import MoneyDisplay from '../MoneyDisplay'
import DateDisplay from '../DateDisplay'
import type { PageResponse } from '@/types/api'

// ── LoadingState ──────────────────────────────────────────────────────────────
describe('LoadingState', () => {
  it('renders with aria-busy=true', () => {
    render(<LoadingState />)
    expect(screen.getByLabelText(/loading/i)).toHaveAttribute('aria-busy', 'true')
  })

  it('renders the requested number of skeleton rows', () => {
    const { container } = render(<LoadingState rows={5} />)
    const skeletons = container.querySelectorAll('.animate-pulse')
    expect(skeletons.length).toBe(5)
  })
})

// ── ErrorState ────────────────────────────────────────────────────────────────
describe('ErrorState', () => {
  it('renders the error message', () => {
    render(<ErrorState message="Data load failed" />)
    expect(screen.getByText('Data load failed')).toBeInTheDocument()
  })

  it('renders retry button when onRetry is provided', async () => {
    const onRetry = vi.fn()
    render(<ErrorState message="Oops" onRetry={onRetry} />)
    const retryBtn = screen.getByRole('button', { name: /retry/i })
    await userEvent.click(retryBtn)
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('does not render retry button when onRetry is absent', () => {
    render(<ErrorState message="Oops" />)
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument()
  })
})

// ── EmptyState ────────────────────────────────────────────────────────────────
describe('EmptyState', () => {
  it('renders the message', () => {
    render(<EmptyState message="No expenses found." />)
    expect(screen.getByText('No expenses found.')).toBeInTheDocument()
  })

  it('renders action button when both actionLabel and onAction are provided', async () => {
    const onAction = vi.fn()
    render(<EmptyState message="Empty" actionLabel="Add item" onAction={onAction} />)
    const btn = screen.getByRole('button', { name: /add item/i })
    await userEvent.click(btn)
    expect(onAction).toHaveBeenCalledOnce()
  })
})

// ── MoneyDisplay ──────────────────────────────────────────────────────────────
describe('MoneyDisplay', () => {
  it('formats INR amounts with en-IN locale', () => {
    render(<MoneyDisplay amount={1234.5} />)
    const text = screen.getByText(/₹/)
    expect(text).toBeInTheDocument()
  })

  it('applies tabular-nums class for column alignment', () => {
    const { container } = render(<MoneyDisplay amount={100} />)
    expect(container.querySelector('.tabular-nums')).not.toBeNull()
  })
})

// ── DateDisplay ───────────────────────────────────────────────────────────────
describe('DateDisplay', () => {
  it('renders a formatted date string', () => {
    render(<DateDisplay dateStr="2026-06-29" />)
    expect(screen.getByText(/Jun 2026/i)).toBeInTheDocument()
  })
})

// ── PaginatedTable ────────────────────────────────────────────────────────────
interface Row {
  id: string
  label: string
}

const columns: ColumnDef<Row>[] = [
  { accessorKey: 'id', header: 'ID' },
  { accessorKey: 'label', header: 'Label' },
]

const singlePage: PageResponse<Row> = {
  content: [
    { id: '1', label: 'Alpha' },
    { id: '2', label: 'Beta' },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
}

const multiPage: PageResponse<Row> = {
  content: [{ id: '1', label: 'Alpha' }],
  page: 0,
  size: 1,
  totalElements: 3,
  totalPages: 3,
}

describe('PaginatedTable', () => {
  it('renders rows from PageResponse content', () => {
    render(<PaginatedTable data={singlePage} columns={columns} onPageChange={vi.fn()} />)
    expect(screen.getByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Beta')).toBeInTheDocument()
  })

  it('disables previous button on first page', () => {
    render(<PaginatedTable data={singlePage} columns={columns} onPageChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
  })

  it('calls onPageChange with next page index when Next is clicked', async () => {
    const onPageChange = vi.fn()
    render(<PaginatedTable data={multiPage} columns={columns} onPageChange={onPageChange} />)
    await userEvent.click(screen.getByRole('button', { name: /next/i }))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('shows page count summary', () => {
    render(<PaginatedTable data={multiPage} columns={columns} onPageChange={vi.fn()} />)
    expect(screen.getByText(/Page 1 of 3/i)).toBeInTheDocument()
  })
})
