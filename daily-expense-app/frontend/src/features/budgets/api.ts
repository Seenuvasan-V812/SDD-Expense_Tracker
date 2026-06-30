import { axiosClient } from '@/lib/axiosClient'
import type { PageResponse } from '@/types/api'
import type { BudgetResponse, CreateBudgetRequest } from './types'

export async function fetchBudgets(
  page = 0,
): Promise<PageResponse<BudgetResponse>> {
  const { data } = await axiosClient.get('/api/v1/budgets', {
    params: { page },
  })
  return data as PageResponse<BudgetResponse>
}

export async function createBudget(
  req: CreateBudgetRequest,
): Promise<BudgetResponse> {
  const { data } = await axiosClient.post('/api/v1/budgets', req)
  return data as BudgetResponse
}

export async function updateBudget(
  id: string,
  req: Partial<CreateBudgetRequest>,
): Promise<BudgetResponse> {
  const { data } = await axiosClient.put(`/api/v1/budgets/${id}`, req)
  return data as BudgetResponse
}

export async function deleteBudget(id: string): Promise<void> {
  await axiosClient.delete(`/api/v1/budgets/${id}`)
}

export async function patchActivation(
  id: string,
  active: boolean,
): Promise<BudgetResponse> {
  const { data } = await axiosClient.patch(`/api/v1/budgets/${id}/activation`, {
    active,
  })
  return data as BudgetResponse
}

export async function patchRollover(
  id: string,
  rolloverEnabled: boolean,
): Promise<BudgetResponse> {
  const { data } = await axiosClient.patch(`/api/v1/budgets/${id}/rollover`, {
    rolloverEnabled,
  })
  return data as BudgetResponse
}
