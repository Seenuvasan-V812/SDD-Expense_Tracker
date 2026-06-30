import { axiosClient } from '@/lib/axiosClient'
import type { PageResponse } from '@/types/api'
import type { CategoryResponse, CreateCategoryRequest, CategoryType } from './types'

interface ListParams {
  type?: CategoryType
  page?: number
  size?: number
}

export async function fetchCategories(
  params: ListParams = {},
): Promise<PageResponse<CategoryResponse>> {
  const query: Record<string, string | number> = {}
  if (params.type) query.type = params.type
  if (params.page !== undefined) query.page = params.page
  if (params.size !== undefined) query.size = params.size
  const { data } = await axiosClient.get('/api/v1/categories', { params: query })
  return data as PageResponse<CategoryResponse>
}

export async function createCategory(
  req: CreateCategoryRequest,
): Promise<CategoryResponse> {
  const { data } = await axiosClient.post('/api/v1/categories', req)
  return data as CategoryResponse
}

export async function updateCategory(
  id: string,
  req: Partial<CreateCategoryRequest>,
): Promise<CategoryResponse> {
  const { data } = await axiosClient.put(`/api/v1/categories/${id}`, req)
  return data as CategoryResponse
}

export async function deleteCategory(id: string): Promise<void> {
  await axiosClient.delete(`/api/v1/categories/${id}`)
}
