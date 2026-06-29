import type { MoneyVO } from '@/types/api'

export type GoalStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ABANDONED'

export interface SavingsGoalResponse {
  savingsGoalId: string
  name: string
  targetAmount: MoneyVO
  targetDate: string | null
  status: GoalStatus
  totalContributed: MoneyVO
  remainingAmount: MoneyVO
  percentAchieved: number
  projectedCompletionDate: string | null
  icon: string | null
  color: string | null
}

export interface ContributionEntryResponse {
  contributionEntryId: string
  expenseId: string
  amount: MoneyVO
  date: string
  source: 'GOAL_SCREEN' | 'LINKED_EXPENSE'
}

export interface CreateGoalRequest {
  name: string
  targetAmount: MoneyVO
  targetDate?: string
  description?: string
}

export interface RecordContributionRequest {
  amount: MoneyVO
  date: string
}
