export interface MoneyVO {
  amount: string
  currency: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  traceId: string
}
