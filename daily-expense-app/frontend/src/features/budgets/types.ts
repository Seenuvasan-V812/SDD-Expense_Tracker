import type { MoneyVO } from '@/types/api'

export type BudgetScope = 'OVERALL' | 'CATEGORY'
export type BudgetPeriod = 'WEEKLY' | 'MONTHLY'
export type FiredThreshold = 'EIGHTY_PERCENT' | 'EXCEEDED'

export interface PeriodWindow {
  startDate: string
  endDate: string
}

export interface BudgetStatusResponse {
  budgetId: string
  scope: BudgetScope
  categoryId: string | null
  limit: MoneyVO
  period: BudgetPeriod
  active: boolean
  rolloverEnabled: boolean
  periodWindow: PeriodWindow
  carriedIn: MoneyVO
  spent: MoneyVO
  remaining: MoneyVO
  percentUsed: number
  firedThresholds: FiredThreshold[]
}

export interface CreateBudgetRequest {
  scope: BudgetScope
  categoryId?: string
  limit: MoneyVO
  period: BudgetPeriod
  rolloverEnabled: boolean
}
