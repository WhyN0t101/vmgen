import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import type {
  ScriptDetail,
  ScriptListItem,
  CreateScriptRequest,
  UpdateScriptRequest,
  ScriptCompatibilityInfo,
  ScriptParameter,
  CreateScriptParameterRequest,
  UpdateScriptParameterRequest,
} from '@/types'
import { api } from '@/api/endpoints'
import { computed, type Ref } from 'vue'
import { useToast } from '@/composables/useToast'

/**
 * Admin-facing composables for listing, inspecting and mutating
 * vulnerability scripts, their compatibility and their parameters.
 */
export const SCRIPT_KEYS = {
  all: ['scripts'] as const,
  lists: () => [...SCRIPT_KEYS.all, 'list'] as const,
  list: (filters: Record<string, unknown>) => [...SCRIPT_KEYS.lists(), filters] as const,
  details: () => [...SCRIPT_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...SCRIPT_KEYS.details(), id] as const,
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
  const { show } = useToast()
  return useMutation({
    mutationFn: (data: CreateScriptRequest) => api.admin.createScript(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.lists() })
      show('Script created successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        error?.response?.data?.errors?.join?.('\n') ??
        error?.response?.data?.message ??
        'Failed to create script'
      show(message, 'error')
    },
  })
}

export function useScriptCompatibility(id: Ref<string>) {
  return useQuery<ScriptCompatibilityInfo>({
    queryKey: computed(() => [...SCRIPT_KEYS.detail(id.value), 'compatibility']),
    queryFn: () => api.admin.getScriptCompatibility(id.value),
    enabled: computed(() => !!id.value),
  })
}

export function useScriptParameters(id: Ref<string>) {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const listQuery = useQuery<ScriptParameter[]>({
    queryKey: computed(() => [...SCRIPT_KEYS.detail(id.value), 'parameters']),
    queryFn: () => api.admin.listScriptParameters(id.value),
    enabled: computed(() => !!id.value),
  })

  const createMutation = useMutation({
    mutationFn: (payload: { scriptId: string; data: CreateScriptParameterRequest }) =>
      api.admin.createScriptParameter(payload.scriptId, payload.data),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: [...SCRIPT_KEYS.detail(variables.scriptId), 'parameters'],
      })
      show('Parameter created', 'success')
    },
    onError: (error: any) => {
      const message =
        error?.response?.data?.errors?.join?.('\n') ??
        error?.response?.data?.message ??
        'Failed to create parameter'
      show(message, 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: (payload: {
      scriptId: string
      parameterId: string
      data: UpdateScriptParameterRequest
    }) => api.admin.updateScriptParameter(payload.scriptId, payload.parameterId, payload.data),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: [...SCRIPT_KEYS.detail(variables.scriptId), 'parameters'],
      })
      show('Parameter updated', 'success')
    },
    onError: (error: any) => {
      const message =
        error?.response?.data?.errors?.join?.('\n') ??
        error?.response?.data?.message ??
        'Failed to update parameter'
      show(message, 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (payload: { scriptId: string; parameterId: string }) =>
      api.admin.deleteScriptParameter(payload.scriptId, payload.parameterId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: [...SCRIPT_KEYS.detail(variables.scriptId), 'parameters'],
      })
      show('Parameter deleted', 'success')
    },
    onError: (error: any) => {
      const message =
        error?.response?.data?.errors?.join?.('\n') ??
        error?.response?.data?.message ??
        'Failed to delete parameter'
      show(message, 'error')
    },
  })

  return {
    listQuery,
    createMutation,
    updateMutation,
    deleteMutation,
  }
}

export function useUpdateScript() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  return useMutation({
    mutationFn: (payload: { id: string; data: UpdateScriptRequest }) =>
      api.admin.updateScript(payload.id, payload.data),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.detail(variables.id) })
      show('Script updated successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        error?.response?.data?.errors?.join?.('\n') ??
        error?.response?.data?.message ??
        'Failed to update script'
      show(message, 'error')
    },
  })
}

export function useDeprecateScript() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  return useMutation({
    mutationFn: (id: string) => api.admin.deprecateScript(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SCRIPT_KEYS.lists() })
      show('Script deprecated', 'success')
    },
    onError: (error: any) => {
      const message =
        error?.response?.data?.errors?.join?.('\n') ??
        error?.response?.data?.message ??
        'Failed to deprecate script'
      show(message, 'error')
    },
  })
}

