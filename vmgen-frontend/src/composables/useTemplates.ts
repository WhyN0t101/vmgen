import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { api } from '@/api/endpoints'
import { useToast } from '@/composables/useToast'
import type { TemplateResponse, TemplateListResponse, RegisterTemplateRequest, UpdateTemplateMetadataRequest, ScriptListItem } from '@/types'

// Query keys
export const TEMPLATE_KEYS = {
  all: ['templates'] as const,
  lists: () => [...TEMPLATE_KEYS.all, 'list'] as const,
  list: (status?: string) => [...TEMPLATE_KEYS.lists(), { status }] as const,
  details: () => [...TEMPLATE_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...TEMPLATE_KEYS.details(), id] as const,
  proxmoxVms: (node?: string) => [...TEMPLATE_KEYS.all, 'proxmox-vms', { node }] as const,
  active: () => [...TEMPLATE_KEYS.all, 'active'] as const,
  compatibleScripts: (id: string) => [...TEMPLATE_KEYS.detail(id), 'scripts'] as const,
}

/**
 * Admin-only list query for templates, optionally filtered by status
 * (e.g. DRAFT, ACTIVE, DEPRECATED).
 */
export function useTemplateList(status?: Ref<string | undefined>) {
  return useQuery({
    queryKey: TEMPLATE_KEYS.list(status?.value),
    queryFn: () => api.admin.listTemplates(status?.value),
    staleTime: 30 * 1000, // 30 seconds
  })
}

/**
 * Admin-only detail query for a single template by ID.
 */
export function useTemplate(id: Ref<string>) {
  return useQuery({
    queryKey: computed(() => TEMPLATE_KEYS.detail(id.value)),
    queryFn: () => api.admin.getTemplate(id.value),
    enabled: computed(() => !!id.value),
  })
}

/**
 * Lazy Proxmox VM listing used by the template registration flow.
 * Does not auto-fetch; caller must trigger refetch manually.
 */
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

/**
 * Public list query for active templates shown in the catalog.
 */
export function useActiveTemplates() {
  return useQuery({
    queryKey: TEMPLATE_KEYS.active(),
    queryFn: () => api.templates.listActive(),
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
}

// Backward compatibility: Legacy useTemplates() for existing pages.
// Maps to useActiveTemplates() but returns data in old Template[] format.
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

/**
 * Public detail query for a single template by ID.
 */
export function usePublicTemplate(id: Ref<string>) {
  return useQuery({
    queryKey: [...TEMPLATE_KEYS.details(), 'public', id.value],
    queryFn: () => api.templates.getById(id.value),
    enabled: !!id.value,
  })
}

/**
 * Admin mutation to register a new template and refresh relevant lists.
 */
export function useRegisterTemplate() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: (data: RegisterTemplateRequest) => api.admin.registerTemplate(data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      show('Template registered successfully', 'success')
      return data
    },
    onError: (error: any) => {
      const message =
        error.response?.data?.errors?.join?.('\n') ??
        error.response?.data?.message ??
        'Failed to register template'
      show(message, 'error')
    },
  })
}

/**
 * Admin mutation to validate a template in Proxmox and mark it ACTIVE.
 */
export function useValidateTemplate() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: (id: string) => api.admin.validateTemplate(id),
    onSuccess: (data, id) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.detail(id) })
      show('Template validated successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        error.response?.data?.errors?.join?.('\n') ??
        error.response?.data?.message ??
        'Failed to validate template'
      show(message, 'error')
    },
  })
}

/**
 * Admin mutation to update mutable template metadata fields.
 */
export function useUpdateTemplateMetadata() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateTemplateMetadataRequest }) =>
      api.admin.updateTemplateMetadata(id, data),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.detail(variables.id) })
      show('Template metadata updated successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        error.response?.data?.errors?.join?.('\n') ??
        error.response?.data?.message ??
        'Failed to update template'
      show(message, 'error')
    },
  })
}

/**
 * Admin mutation to deprecate a template and remove it from active lists.
 */
export function useDeprecateTemplate() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: (id: string) => api.admin.deprecateTemplate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      show('Template deprecated successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        error.response?.data?.errors?.join?.('\n') ??
        error.response?.data?.message ??
        'Failed to deprecate template'
      show(message, 'error')
    },
  })
}

/**
 * Admin query to fetch scripts marked as compatible with the given template.
 */
export function useTemplateCompatibleScripts(templateId: Ref<string | null>) {
  return useQuery({
    queryKey: computed(() => TEMPLATE_KEYS.compatibleScripts(templateId.value ?? '')),
    queryFn: () => api.admin.getTemplateCompatibleScripts(templateId.value!),
    enabled: computed(() => !!templateId.value),
  })
}

/**
 * Admin mutation to link a script as compatible with a template.
 */
export function useAddTemplateCompatibleScript() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: ({ templateId, scriptId }: { templateId: string; scriptId: string }) =>
      api.admin.addTemplateCompatibleScript(templateId, scriptId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.compatibleScripts(variables.templateId) })
      show('Script added to compatible list', 'success')
    },
    onError: (error: any) => {
      const message =
        error.response?.data?.errors?.join?.('\n') ??
        error.response?.data?.message ??
        'Failed to add script'
      show(message, 'error')
    },
  })
}

/**
 * Admin mutation to remove a script–template compatibility link.
 */
export function useRemoveTemplateCompatibleScript() {
  const queryClient = useQueryClient()
  const { show } = useToast()

  return useMutation({
    mutationFn: ({ templateId, scriptId }: { templateId: string; scriptId: string }) =>
      api.admin.removeTemplateCompatibleScript(templateId, scriptId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.compatibleScripts(variables.templateId) })
      show('Script removed from compatible list', 'success')
    },
    onError: (error: any) => {
      const message =
        error.response?.data?.errors?.join?.('\n') ??
        error.response?.data?.message ??
        'Failed to remove script'
      show(message, 'error')
    },
  })
}

/**
 * Public query for the Generate page — returns scripts compatible with a template.
 * Falls back to all active scripts if no compatibility entries are configured.
 */
export function usePublicCompatibleScripts(templateId: Ref<string | null>) {
  return useQuery({
    queryKey: computed(() => ['public-compatible-scripts', templateId.value]),
    queryFn: () => api.templates.getCompatibleScripts(templateId.value!),
    enabled: computed(() => !!templateId.value),
  })
}