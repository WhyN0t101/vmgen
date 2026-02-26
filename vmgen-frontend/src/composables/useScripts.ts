import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import type {
  ScriptDetail,
  ScriptListItem,
  CreateScriptRequest,
  UpdateScriptRequest,
} from '@/types'
import { api } from '@/api/endpoints'
import { computed, type Ref } from 'vue'

export const SCRIPT_KEYS = {
  all: ['scripts'] as const,
  lists: () => [...SCRIPT_KEYS.all, 'list'] as const,
  list: (filters: Record<string, unknown>) => [...SCRIPT_KEYS.lists(), filters] as const,
  details: () => [...SCRIPT_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...SCRIPT_KEYS.details(), id] as const,
}

function showToast(message: string, type: 'success' | 'error' = 'success') {
  console.log(`[${type.toUpperCase()}] ${message}`)
}

export function useScriptList(params: {
  categoryId?: Ref<string | ''>
  difficulty?: Ref<string | ''>
  activeOnly?: Ref<boolean>
}) {
  const getFilters = () => ({
    categoryId: params.categoryId?.value || undefined,
    difficulty: params.difficulty?.value || undefined,
    activeOnly: params.activeOnly?.value ?? true,
    page: 0,
    size: 100,
  })

  return useQuery({
    queryKey: computed(() => SCRIPT_KEYS.list(getFilters())),
    queryFn: async () => {
      const f = getFilters()
      const search = new URLSearchParams()
      if (f.categoryId) search.set('categoryId', f.categoryId)
      if (f.difficulty) search.set('difficulty', f.difficulty)
      if (!f.activeOnly) search.set('activeOnly', 'false')
      search.set('page', String(f.page))
      search.set('size', String(f.size))

      const page = await api.admin.listScripts(search.toString())
      return page.content as ScriptListItem[]
    },
  })
}

export function useScriptDetail(id: Ref<string>) {
  return useQuery<ScriptDetail>({
    queryKey: computed(() => SCRIPT_KEYS.detail(id.value)),
    queryFn: () => api.admin.getScript(id.value),
    enabled: computed(() => !!id.value),
  })
}

export function useCreateScript() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateScriptRequest) => api.admin.createScript(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.lists() })
      showToast('Script created successfully', 'success')
    },
    onError: (error: any) => {
      const message = error?.response?.data?.message || 'Failed to create script'
      showToast(message, 'error')
    },
  })
}

export function useUpdateScript() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: { id: string; data: UpdateScriptRequest }) =>
      api.admin.updateScript(payload.id, payload.data),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.detail(variables.id) })
      showToast('Script updated successfully', 'success')
    },
    onError: (error: any) => {
      const message = error?.response?.data?.message || 'Failed to update script'
      showToast(message, 'error')
    },
  })
}

export function useDeprecateScript() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.admin.deprecateScript(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.lists() })
      showToast('Script deprecated', 'success')
    },
    onError: (error: any) => {
      const message = error?.response?.data?.message || 'Failed to deprecate script'
      showToast(message, 'error')
    },
  })
}

