import { axiosClient } from '@/lib/axiosClient'
import type { PageResponse } from '@/types/api'
import type {
  SavingsGoalResponse,
  ContributionEntryResponse,
  CreateGoalRequest,
  RecordContributionRequest,
  GoalStatus,
} from './types'

export async function fetchGoals(
  status?: GoalStatus,
  page = 0,
): Promise<PageResponse<SavingsGoalResponse>> {
  const params: Record<string, string | number> = { page }
  if (status) params.status = status
  const { data } = await axiosClient.get('/api/v1/savings-goals', { params })
  return data as PageResponse<SavingsGoalResponse>
}

export async function createGoal(
  req: CreateGoalRequest,
): Promise<SavingsGoalResponse> {
  const { data } = await axiosClient.post('/api/v1/savings-goals', req)
  return data as SavingsGoalResponse
}

export async function updateGoal(
  id: string,
  req: Partial<CreateGoalRequest>,
): Promise<SavingsGoalResponse> {
  const { data } = await axiosClient.put(`/api/v1/savings-goals/${id}`, req)
  return data as SavingsGoalResponse
}

export async function deleteGoal(id: string): Promise<void> {
  await axiosClient.delete(`/api/v1/savings-goals/${id}`)
}

export async function recordContribution(
  id: string,
  req: RecordContributionRequest,
): Promise<void> {
  await axiosClient.post(`/api/v1/savings-goals/${id}/contributions`, req)
}

export async function fetchContributions(
  id: string,
  page = 0,
): Promise<PageResponse<ContributionEntryResponse>> {
  const { data } = await axiosClient.get(
    `/api/v1/savings-goals/${id}/contributions`,
    { params: { page } },
  )
  return data as PageResponse<ContributionEntryResponse>
}

export async function updateGoalStatus(
  id: string,
  status: GoalStatus,
): Promise<SavingsGoalResponse> {
  const { data } = await axiosClient.patch(
    `/api/v1/savings-goals/${id}/status`,
    { status },
  )
  return data as SavingsGoalResponse
}
