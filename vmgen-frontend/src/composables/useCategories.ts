import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import type {
  CategoryList,
  CategoryDetail,
  CreateCategoryRequest,
  UpdateCategoryRequest,
  CategoryReorderRequest,
} from '@/types'
import { api } from '@/api/endpoints'

export const CATEGORY_KEYS = {
  all: ['categories'] as const,
  lists: () => [...CATEGORY_KEYS.all, 'list'] as const,
  list: (activeOnly: boolean) => [...CATEGORY_KEYS.lists(), { activeOnly }] as const,
  details: () => [...CATEGORY_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...CATEGORY_KEYS.details(), id] as const,
}

function showToast(message: string, type: 'success' | 'error' = 'success') {
  // Simple console-based toast placeholder; can be replaced with real UI toasts.
  console.log(`[${type.toUpperCase()}] ${message}`)
}

export function useCategoryList(activeOnly = true) {
  return useQuery<CategoryList[]>({
    queryKey: CATEGORY_KEYS.list(activeOnly),
    queryFn: () => api.admin.listCategories(activeOnly),
    staleTime: 30 * 1000,
  })
}

export function useCategoryDetail(id: string) {
  return useQuery<CategoryDetail>({
    queryKey: CATEGORY_KEYS.detail(id),
    queryFn: () => api.admin.getCategory(id),
    enabled: !!id,
  })
}

export function useCreateOrUpdateCategory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: { id?: string; data: CreateCategoryRequest | UpdateCategoryRequest }) => {
      if (payload.id) {
        return api.admin.updateCategory(payload.id, payload.data as UpdateCategoryRequest)
      }
      return api.admin.createCategory(payload.data as CreateCategoryRequest)
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: CATEGORY_KEYS.lists() })
      if (variables.id) {
        queryClient.invalidateQueries({ queryKey: CATEGORY_KEYS.detail(variables.id) })
      }
      showToast('Category saved successfully', 'success')
    },
    onError: (error: any) => {
      const message = error?.response?.data?.message || 'Failed to save category'
      showToast(message, 'error')
    },
  })
}

export function useDeprecateCategory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => api.admin.deprecateCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CATEGORY_KEYS.lists() })
      showToast('Category deprecated', 'success')
    },
    onError: (error: any) => {
      const message = error?.response?.data?.message || 'Failed to deprecate category'
      showToast(message, 'error')
    },
  })
}

export function useReorderCategories() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (items: CategoryReorderRequest[]) => api.admin.reorderCategories(items),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CATEGORY_KEYS.lists() })
      showToast('Category order updated', 'success')
    },
    onError: (error: any) => {
      const message = error?.response?.data?.message || 'Failed to reorder categories'
      showToast(message, 'error')
    },
  })
}

