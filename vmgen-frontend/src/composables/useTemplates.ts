import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { api } from '@/api/endpoints'
import type { TemplateResponse, TemplateListResponse, RegisterTemplateRequest, UpdateTemplateMetadataRequest } from '@/types'

// Query keys
export const TEMPLATE_KEYS = {
  all: ['templates'] as const,
  lists: () => [...TEMPLATE_KEYS.all, 'list'] as const,
  list: (status?: string) => [...TEMPLATE_KEYS.lists(), { status }] as const,
  details: () => [...TEMPLATE_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...TEMPLATE_KEYS.details(), id] as const,
  proxmoxVms: (node?: string) => [...TEMPLATE_KEYS.all, 'proxmox-vms', { node }] as const,
  active: () => [...TEMPLATE_KEYS.all, 'active'] as const,
}

// Simple toast helper (can be replaced with proper toast library later)
function showToast(message: string, type: 'success' | 'error' = 'success') {
  // For now, just use console - can be replaced with a toast library
  console.log(`[${type.toUpperCase()}] ${message}`)
  // In a real app, you'd use something like vue-toastification or similar
}

// Admin queries
export function useTemplateList(status?: Ref<string | undefined>) {
  return useQuery({
    queryKey: TEMPLATE_KEYS.list(status?.value),
    queryFn: () => api.admin.listTemplates(status?.value),
    staleTime: 30 * 1000, // 30 seconds
  })
}

export function useTemplate(id: Ref<string>) {
  return useQuery({
    queryKey: computed(() => TEMPLATE_KEYS.detail(id.value)),
    queryFn: () => api.admin.getTemplate(id.value),
    enabled: computed(() => !!id.value),
  })
}

export function useProxmoxVms(node?: Ref<string | undefined> | string | (() => string)) {
  const getNodeValue = () => {
    if (typeof node === 'function') return node()
    if (typeof node === 'string') return node
    return node?.value
  }
  const nodeValue = getNodeValue()
  return useQuery({
    queryKey: TEMPLATE_KEYS.proxmoxVms(nodeValue),
    queryFn: () => {
      const currentNode = getNodeValue()
      return api.admin.listProxmoxVms(currentNode)
    },
    enabled: false, // Only fetch when explicitly called
  })
}

// Public queries
export function useActiveTemplates() {
  return useQuery({
    queryKey: TEMPLATE_KEYS.active(),
    queryFn: () => api.templates.listActive(),
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
}

// Backward compatibility: Legacy useTemplates() for existing pages
// Maps to useActiveTemplates() but returns data in old Template[] format
export function useTemplates() {
  const query = useActiveTemplates()
  
  return {
    ...query,
    data: computed(() => {
      // Transform TemplateListResponse[] to legacy Template[] format
      if (!query.data.value) return undefined
      return query.data.value.map(t => ({
        id: t.id,
        name: t.identifier,
        os_family: t.osFamily as any,
        version: t.version,
        arch: 'x86_64' as const, // Default, since we don't have arch in list response
        capabilities: [], // Not available in list response
        description: undefined,
      }))
    }),
  }
}

export function usePublicTemplate(id: Ref<string>) {
  return useQuery({
    queryKey: [...TEMPLATE_KEYS.details(), 'public', id.value],
    queryFn: () => api.templates.getById(id.value),
    enabled: !!id.value,
  })
}

// Mutations
export function useRegisterTemplate() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: RegisterTemplateRequest) => api.admin.registerTemplate(data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      showToast('Template registered successfully', 'success')
      return data
    },
    onError: (error: any) => {
      const message = error.response?.data?.message || 'Failed to register template'
      showToast(message, 'error')
    },
  })
}

export function useValidateTemplate() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => api.admin.validateTemplate(id),
    onSuccess: (data, id) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.detail(id) })
      showToast('Template validated successfully', 'success')
    },
    onError: (error: any) => {
      const message = error.response?.data?.message || 'Failed to validate template'
      showToast(message, 'error')
    },
  })
}

export function useUpdateTemplateMetadata() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateTemplateMetadataRequest }) =>
      api.admin.updateTemplateMetadata(id, data),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.detail(variables.id) })
      showToast('Template metadata updated successfully', 'success')
    },
    onError: (error: any) => {
      const message = error.response?.data?.message || 'Failed to update template'
      showToast(message, 'error')
    },
  })
}

export function useDeprecateTemplate() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => api.admin.deprecateTemplate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      showToast('Template deprecated successfully', 'success')
    },
    onError: (error: any) => {
      const message = error.response?.data?.message || 'Failed to deprecate template'
      showToast(message, 'error')
    },
  })
}