export type BudgetScope = 'OVERALL' | 'CATEGORY'
export type BudgetPeriod = 'WEEKLY' | 'MONTHLY'

export interface BudgetResponse {
  id: string
  userId: string
  scope: BudgetScope
  categoryId: string | null
  budgetLimit: string
  currency: string
  periodType: BudgetPeriod
  active: boolean
  rolloverEnabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateBudgetRequest {
  scope: BudgetScope
  categoryId?: string
  budgetLimit: number
  periodType: BudgetPeriod
  rolloverEnabled: boolean
}
