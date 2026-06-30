export type CategoryType = 'EXPENSE' | 'INCOME' | 'BOTH'
export type CategoryOrigin = 'DEFAULT' | 'CUSTOM'
export type SystemRole = 'NONE' | 'SAVINGS'

export interface CategoryResponse {
  categoryId: string
  name: string
  type: CategoryType
  origin: CategoryOrigin
  systemRole: SystemRole
  icon: string | null
  color: string | null
  deletable: boolean
}

export interface CreateCategoryRequest {
  name: string
  type: CategoryType
  icon?: string
  color?: string
}
