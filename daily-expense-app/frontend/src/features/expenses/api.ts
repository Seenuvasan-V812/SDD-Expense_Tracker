import { axiosClient } from '@/lib/axiosClient'
import type { PageResponse } from '@/types/api'
import type {
  ExpenseResponse,
  CreateExpenseRequest,
  ExpenseFilters,
} from './types'

export async function fetchExpenses(
  filters: ExpenseFilters = {},
): Promise<PageResponse<ExpenseResponse>> {
  const params: Record<string, string | number> = {}
  if (filters.from) params.from = filters.from
  if (filters.to) params.to = filters.to
  if (filters.categoryId) params.categoryId = filters.categoryId
  if (filters.paymentMethod) params.paymentMethod = filters.paymentMethod
  if (filters.tag) params.tag = filters.tag
  if (filters.sort) params.sort = filters.sort
  if (filters.page !== undefined) params.page = filters.page
  if (filters.size !== undefined) params.size = filters.size

  const { data } = await axiosClient.get('/api/v1/expenses', { params })
  return data as PageResponse<ExpenseResponse>
}

export async function createExpense(
  req: CreateExpenseRequest,
): Promise<ExpenseResponse> {
  const { data } = await axiosClient.post('/api/v1/expenses', req)
  return data as ExpenseResponse
}

export async function updateExpense(
  id: string,
  req: Partial<CreateExpenseRequest>,
): Promise<ExpenseResponse> {
  const { data } = await axiosClient.put(`/api/v1/expenses/${id}`, req)
  return data as ExpenseResponse
}

export async function deleteExpense(id: string): Promise<void> {
  await axiosClient.delete(`/api/v1/expenses/${id}`)
}

export async function uploadReceipt(id: string, file: File): Promise<void> {
  const form = new FormData()
  form.append('file', file)
  await axiosClient.post(`/api/v1/expenses/${id}/receipt`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function exportExpensesUrl(from: string, to: string): string {
  return `/api/v1/expenses/export?from=${from}&to=${to}`
}

export async function importExpenses(file: File): Promise<unknown> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await axiosClient.post('/api/v1/expenses/import', form, {
    headers: { 'Content-Type': 'text/csv' },
  })
  return data
}
