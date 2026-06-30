import { axiosClient } from '@/lib/axiosClient'
import type { PageResponse } from '@/types/api'
import type { BudgetStatusResponse, CreateBudgetRequest } from './types'

export async function fetchBudgets(
  page = 0,
): Promise<PageResponse<BudgetStatusResponse>> {
  const { data } = await axiosClient.get('/api/v1/budgets', {
    params: { page },
  })
  return data as PageResponse<BudgetStatusResponse>
}

export async function createBudget(
  req: CreateBudgetRequest,
): Promise<BudgetStatusResponse> {
  const { data } = await axiosClient.post('/api/v1/budgets', req)
  return data as BudgetStatusResponse
}

export async function updateBudget(
  id: string,
  req: Partial<CreateBudgetRequest>,
): Promise<BudgetStatusResponse> {
  const { data } = await axiosClient.put(`/api/v1/budgets/${id}`, req)
  return data as BudgetStatusResponse
}

export async function deleteBudget(id: string): Promise<void> {
  await axiosClient.delete(`/api/v1/budgets/${id}`)
}

export async function patchActivation(
  id: string,
  active: boolean,
): Promise<BudgetStatusResponse> {
  const { data } = await axiosClient.patch(`/api/v1/budgets/${id}/activation`, {
    active,
  })
  return data as BudgetStatusResponse
}

export async function patchRollover(
  id: string,
  rolloverEnabled: boolean,
): Promise<BudgetStatusResponse> {
  const { data } = await axiosClient.patch(`/api/v1/budgets/${id}/rollover`, {
    rolloverEnabled,
  })
  return data as BudgetStatusResponse
}
