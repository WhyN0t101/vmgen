import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import type {
  CategoryList,
  CategoryDetail,
  CreateCategoryRequest,
  UpdateCategoryRequest,
  CategoryReorderRequest,
} from '@/types'
import { api } from '@/api/endpoints'
import { useToast } from '@/composables/useToast'
import { extractApiError } from '@/utils/apiError'

/**
 * Admin-facing helpers for listing, creating/updating, deprecating and
 * reordering vulnerability categories in the script catalog.
 */
export const CATEGORY_KEYS = {
  all: ['categories'] as const,
  lists: () => [...CATEGORY_KEYS.all, 'list'] as const,
  list: (activeOnly: boolean) => [...CATEGORY_KEYS.lists(), { activeOnly }] as const,
  details: () => [...CATEGORY_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...CATEGORY_KEYS.details(), id] as const,
}

export function useCategoryList(activeOnly: boolean | Ref<boolean> = true) {
  const resolve = () => (typeof activeOnly === 'boolean' ? activeOnly : activeOnly.value)

  return useQuery<CategoryList[]>({
    queryKey: computed(() => CATEGORY_KEYS.list(resolve())),
    queryFn: () => api.admin.listCategories(resolve()),
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
  const { show } = useToast()

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
      show('Category saved successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        extractApiError(error, 'Failed to save category')
      show(message, 'error')
    },
  })
}

export function useDeprecateCategory() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: (id: string) => api.admin.deprecateCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CATEGORY_KEYS.lists() })
      show('Category deprecated', 'success')
    },
    onError: (error: any) => {
      const message =
        extractApiError(error, 'Failed to deprecate category')
      show(message, 'error')
    },
  })
}

export function useReorderCategories() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: (items: CategoryReorderRequest[]) => api.admin.reorderCategories(items),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CATEGORY_KEYS.lists() })
      show('Category order updated', 'success')
    },
    onError: (error: any) => {
      const message =
        extractApiError(error, 'Failed to reorder categories')
      show(message, 'error')
    },
  })
}

