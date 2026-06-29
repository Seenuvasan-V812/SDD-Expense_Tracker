import type { MoneyVO } from '@/types/api'

export type PaymentMethod =
  | 'UPI'
  | 'CASH'
  | 'CREDIT_CARD'
  | 'DEBIT_CARD'
  | 'NET_BANKING'
  | 'OTHER'

export interface ExpenseResponse {
  expenseId: string
  amount: MoneyVO
  date: string
  categoryId: string
  paymentMethod: PaymentMethod
  description: string | null
  merchant: string | null
  notes: string | null
  tags: string[]
  hasReceipt: boolean
  savingsGoalId: string | null
  recurringExpenseId: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateExpenseRequest {
  amount: MoneyVO
  date: string
  categoryId: string
  paymentMethod: PaymentMethod
  description?: string
  merchant?: string
  notes?: string
  tags?: string[]
  savingsGoalId?: string
}

export interface ExpenseFilters {
  from?: string
  to?: string
  categoryId?: string
  paymentMethod?: PaymentMethod
  tag?: string
  sort?: string
  page?: number
  size?: number
}
