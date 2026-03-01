import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { api } from '@/api/endpoints'
import { useToast } from '@/composables/useToast'
import { extractApiError } from '@/utils/apiError'
import type { RegisterTemplateRequest, UpdateTemplateMetadataRequest } from '@/types'

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
 * Supports optional server-side filtering (search, osFamily) and pagination.
 * Reactive refs are included in the queryKey so TanStack Query re-fetches on change.
 *
 * - Pass `size` to override the page size (e.g. 100 for a full-list selector).
 * - Search is matched against identifier, version, and description server-side.
 */
export function useActiveTemplates(options?: {
  search?: Ref<string>
  osFamily?: Ref<string>
  page?: Ref<number>
  size?: number
}) {
  return useQuery({
    queryKey: computed(() => [
      ...TEMPLATE_KEYS.active(),
      {
        page: options?.page?.value ?? 0,
        size: options?.size ?? 20,
        search: options?.search?.value || undefined,
        osFamily: options?.osFamily?.value || undefined,
      },
    ]),
    queryFn: () =>
      api.templates.listActive(
        options?.page?.value ?? 0,
        options?.size ?? 20,
        options?.search?.value || undefined,
        options?.osFamily?.value || undefined,
      ),
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      show('Template registered successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        extractApiError(error, 'Failed to register template')
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
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.active() })
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.detail(id) })
      show('Template validated successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        extractApiError(error, 'Failed to validate template')
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
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.detail(variables.id) })
      show('Template metadata updated successfully', 'success')
    },
    onError: (error: any) => {
      const message =
        extractApiError(error, 'Failed to update template')
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
        extractApiError(error, 'Failed to deprecate template')
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
        extractApiError(error, 'Failed to add script')
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
        extractApiError(error, 'Failed to remove script')
      show(message, 'error')
    },
  })
}

/**
 * Public query for the Generate page — returns scripts compatible with a template.
 * Falls back to all active scripts if no compatibility entries are configured.
 */
export function usePublicCompatibleScripts(templateId: Ref<string | null | undefined>) {
  return useQuery({
    queryKey: computed(() => ['public-compatible-scripts', templateId.value]),
    queryFn: () => api.templates.getCompatibleScripts(templateId.value!),
    enabled: computed(() => !!templateId.value),
  })
}