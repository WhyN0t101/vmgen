import { apiRequest, API_BASE } from './client'
import { useAuthStore } from '@/stores/auth'
import type {
  Build,
  BuildSpec,
  ProvenanceResponse,
  TemplateResponse,
  TemplateListResponse,
  RegisterTemplateRequest,
  UpdateTemplateMetadataRequest,
  ProxmoxVmSummary,
  ScriptCompatibilityInfo,
  ScriptParameter,
  CreateScriptParameterRequest,
  UpdateScriptParameterRequest,
  StatusResponse,
  ScriptDetail,
  ScriptListItem,
  PagedResponse,
} from '@/types'

export const api = {
  // Builds
  createBuild: async (spec: BuildSpec): Promise<Build> => {
    return apiRequest<Build>('/api/builds', {
      method: 'POST',
      body: JSON.stringify(spec),
    })
  },

  getBuild: async (id: string): Promise<Build> => {
    return apiRequest<Build>(`/api/builds/${id}`)
  },

  getUserBuilds: async (page = 0, size = 20): Promise<PagedResponse<Build>> => {
    return apiRequest<PagedResponse<Build>>(`/api/builds?page=${page}&size=${size}`)
  },

  downloadBuild: async (id: string, filename: string): Promise<void> => {
    const authStore = useAuthStore()
    const response = await fetch(`${API_BASE}/api/builds/${id}/download`, {
      headers: authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {},
    })
    if (!response.ok) throw new Error(`Download failed: ${response.statusText}`)
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  },

  getProvenance: async (id: string): Promise<ProvenanceResponse> => {
    return apiRequest<ProvenanceResponse>(`/api/builds/${id}/provenance`)
  },

  downloadProvenance: async (id: string): Promise<void> => {
    const data = await api.getProvenance(id)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `provenance-${id}.json`
    a.click()
    URL.revokeObjectURL(url)
  },

  // Flags
  submitFlag: async (requestId: string, flag: string): Promise<{ valid: boolean; message: string }> => {
    return apiRequest<{ valid: boolean; message: string }>(
      `/api/builds/${requestId}/flag`,
      {
        method: 'POST',
        body: JSON.stringify({ flag }),
      }
    )
  },

  // Admin endpoints
  admin: {
    // Sessions
    getActiveSessions: async (): Promise<import('@/types').ActiveSession[]> => {
      return apiRequest<import('@/types').ActiveSession[]>('/api/admin/sessions')
    },

    revokeSession: async (sessionId: string): Promise<{ status: string }> => {
      return apiRequest<{ status: string }>('/api/admin/sessions/revoke', {
        method: 'POST',
        body: JSON.stringify({ sessionId }),
      })
    },

    revokeAllUserSessions: async (userId: string): Promise<{ status: string }> => {
      return apiRequest<{ status: string }>('/api/admin/sessions/revoke-all', {
        method: 'POST',
        body: JSON.stringify({ userId }),
      })
    },

    // Users
    updateUserRole: async (userId: string, role: 'user' | 'admin'): Promise<{ status: string }> => {
      return apiRequest<{ status: string }>(`/api/admin/users/${userId}/role`, {
        method: 'POST',
        body: JSON.stringify({ role: role.toUpperCase() }),
      })
    },

    // Templates
    listTemplates: async (status?: string): Promise<TemplateListResponse[]> => {
      const url = status ? `/api/admin/templates?status=${status}` : '/api/admin/templates'
      return apiRequest<TemplateListResponse[]>(url)
    },

    getTemplate: async (id: string): Promise<TemplateResponse> => {
      return apiRequest<TemplateResponse>(`/api/admin/templates/${id}`)
    },

    registerTemplate: async (data: RegisterTemplateRequest): Promise<TemplateResponse> => {
      return apiRequest<TemplateResponse>('/api/admin/templates', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },

    validateTemplate: async (id: string): Promise<TemplateResponse> => {
      return apiRequest<TemplateResponse>(`/api/admin/templates/${id}/validate`, {
        method: 'POST',
      })
    },

    updateTemplateMetadata: async (id: string, data: UpdateTemplateMetadataRequest): Promise<TemplateResponse> => {
      return apiRequest<TemplateResponse>(`/api/admin/templates/${id}/metadata`, {
        method: 'PATCH',
        body: JSON.stringify(data),
      })
    },

    deprecateTemplate: async (id: string): Promise<void> => {
      return apiRequest<void>(`/api/admin/templates/${id}/deprecate`, {
        method: 'PATCH',
      })
    },

    getTemplateCompatibleScripts: async (id: string): Promise<ScriptListItem[]> => {
      return apiRequest<ScriptListItem[]>(`/api/admin/templates/${id}/compatible-scripts`)
    },

    addTemplateCompatibleScript: async (templateId: string, scriptId: string): Promise<void> => {
      return apiRequest<void>(`/api/admin/templates/${templateId}/compatible-scripts`, {
        method: 'POST',
        body: JSON.stringify({ scriptId }),
      })
    },

    removeTemplateCompatibleScript: async (templateId: string, scriptId: string): Promise<void> => {
      return apiRequest<void>(`/api/admin/templates/${templateId}/compatible-scripts/${scriptId}`, {
        method: 'DELETE',
      })
    },

    listProxmoxVms: async (node?: string): Promise<ProxmoxVmSummary[]> => {
      const url = node ? `/api/admin/proxmox/vms?node=${node}` : '/api/admin/proxmox/vms'
      return apiRequest<ProxmoxVmSummary[]>(url)
    },

    getVmStorage: async (vmid: number, node?: string): Promise<{ storage: string }> => {
      const nodeParam = node ? `&node=${node}` : ''
      return apiRequest<{ storage: string }>(`/api/admin/proxmox/vm-storage?vmid=${vmid}${nodeParam}`)
    },

    // Scripts
    listScripts: async (queryString: string): Promise<PagedResponse<ScriptListItem>> => {
      const url = queryString ? `/api/admin/scripts?${queryString}` : '/api/admin/scripts'
      return apiRequest<PagedResponse<ScriptListItem>>(url)
    },

    getEnrichedScripts: async (): Promise<ScriptListItem[]> =>
      apiRequest<ScriptListItem[]>('/api/admin/scripts/enriched'),

    getScript: async (id: string): Promise<ScriptDetail> => {
      return apiRequest<ScriptDetail>(`/api/admin/scripts/${id}`)
    },

    createScript: async (data: import('@/types').CreateScriptRequest): Promise<ScriptDetail> => {
      return apiRequest<ScriptDetail>('/api/admin/scripts', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },

    updateScript: async (id: string, data: import('@/types').UpdateScriptRequest): Promise<ScriptDetail> => {
      return apiRequest<ScriptDetail>(`/api/admin/scripts/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      })
    },

    deprecateScript: async (id: string): Promise<StatusResponse> => {
      return apiRequest<StatusResponse>(`/api/admin/scripts/${id}/deprecate`, {
        method: 'POST',
      })
    },

    // Script compatibility
    getScriptCompatibility: async (id: string): Promise<ScriptCompatibilityInfo> => {
      return apiRequest<ScriptCompatibilityInfo>(`/api/admin/scripts/${id}/compatibility`)
    },

    addScriptConflict: async (id: string, conflictsWithId: string): Promise<StatusResponse> => {
      return apiRequest<StatusResponse>(`/api/admin/scripts/${id}/conflicts`, {
        method: 'POST',
        body: JSON.stringify({ conflictsWithScriptId: conflictsWithId }),
      })
    },

    removeScriptConflict: async (id: string, conflictId: string): Promise<StatusResponse> => {
      return apiRequest<StatusResponse>(`/api/admin/scripts/${id}/conflicts/${conflictId}`, {
        method: 'DELETE',
      })
    },

    addScriptDependency: async (id: string, requiresId: string): Promise<StatusResponse> => {
      return apiRequest<StatusResponse>(`/api/admin/scripts/${id}/dependencies`, {
        method: 'POST',
        body: JSON.stringify({ requiresScriptId: requiresId }),
      })
    },

    removeScriptDependency: async (id: string, dependencyId: string): Promise<StatusResponse> => {
      return apiRequest<StatusResponse>(`/api/admin/scripts/${id}/dependencies/${dependencyId}`, {
        method: 'DELETE',
      })
    },

    // Script parameters
    listScriptParameters: async (scriptId: string): Promise<ScriptParameter[]> => {
      return apiRequest<ScriptParameter[]>(`/api/admin/scripts/${scriptId}/parameters`)
    },

    createScriptParameter: async (
      scriptId: string,
      data: CreateScriptParameterRequest,
    ): Promise<ScriptParameter> => {
      return apiRequest<ScriptParameter>(`/api/admin/scripts/${scriptId}/parameters`, {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },

    updateScriptParameter: async (
      scriptId: string,
      parameterId: string,
      data: UpdateScriptParameterRequest,
    ): Promise<ScriptParameter> => {
      return apiRequest<ScriptParameter>(
        `/api/admin/scripts/${scriptId}/parameters/${parameterId}`,
        {
          method: 'PUT',
          body: JSON.stringify(data),
        },
      )
    },

    deleteScriptParameter: async (scriptId: string, parameterId: string): Promise<StatusResponse> => {
      return apiRequest<StatusResponse>(
        `/api/admin/scripts/${scriptId}/parameters/${parameterId}`,
        {
          method: 'DELETE',
        },
      )
    },

    // Vulnerability categories
    listCategories: async (activeOnly: boolean = true): Promise<import('@/types').CategoryList[]> => {
      const param = activeOnly ? '?activeOnly=true' : '?activeOnly=false'
      return apiRequest<import('@/types').CategoryList[]>(`/api/admin/categories${param}`)
    },

    getCategory: async (id: string): Promise<import('@/types').CategoryDetail> => {
      return apiRequest<import('@/types').CategoryDetail>(`/api/admin/categories/${id}`)
    },

    createCategory: async (data: import('@/types').CreateCategoryRequest): Promise<import('@/types').CategoryDetail> => {
      return apiRequest<import('@/types').CategoryDetail>('/api/admin/categories', {
        method: 'POST',
        body: JSON.stringify(data),
      })
    },

    updateCategory: async (id: string, data: import('@/types').UpdateCategoryRequest): Promise<import('@/types').CategoryDetail> => {
      return apiRequest<import('@/types').CategoryDetail>(`/api/admin/categories/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      })
    },

    deprecateCategory: async (id: string): Promise<import('@/types').StatusResponse> => {
      return apiRequest<import('@/types').StatusResponse>(`/api/admin/categories/${id}/deprecate`, {
        method: 'POST',
      })
    },

    reorderCategories: async (items: import('@/types').CategoryReorderRequest[]): Promise<import('@/types').StatusResponse> => {
      return apiRequest<import('@/types').StatusResponse>('/api/admin/categories/reorder', {
        method: 'POST',
        body: JSON.stringify(items),
      })
    },
  },

  // Public template endpoints
  templates: {
    listActive: async (page = 0, size = 20, search?: string, osFamily?: string): Promise<PagedResponse<TemplateListResponse>> => {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (search) params.set('search', search)
      if (osFamily) params.set('osFamily', osFamily)
      return apiRequest<PagedResponse<TemplateListResponse>>(`/api/templates?${params}`)
    },

    getById: async (id: string): Promise<TemplateResponse> => {
      return apiRequest<TemplateResponse>(`/api/templates/${id}`)
    },

    getCompatibleScripts: async (id: string): Promise<ScriptListItem[]> => {
      return apiRequest<ScriptListItem[]>(`/api/templates/${id}/scripts`)
    },
  },

  // Public script endpoints (user-facing, non-admin)
  scripts: {
    /** Returns only USER_INPUT and USER_INPUT_OR_RANDOM parameters for the given script. */
    getParameters: async (id: string): Promise<ScriptParameter[]> => {
      return apiRequest<ScriptParameter[]>(`/api/scripts/${id}/parameters`)
    },
  },
}
