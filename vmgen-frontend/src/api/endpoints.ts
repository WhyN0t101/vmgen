import { apiRequest, rawRequest, API_BASE } from './client'
import { useAuthStore } from '@/stores/auth'
import type {
  AdminBuildCredentials,
  AdminBuildSummary,
  AdminMetrics,
  AuthResponse,
  Build,
  BuildLogEntry,
  BuildSpec,
  ProvenanceResponse,
  TelemetryUploadResponse,
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
  // Auth (public endpoints — no JWT required)
  auth: {
    login: (identifier: string, password: string): Promise<AuthResponse> =>
      rawRequest<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ identifier, password }),
      }),

    register: (username: string, email: string, password: string): Promise<void> =>
      rawRequest<void>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ username, email, password }),
      }),

    // logout sends the token explicitly; apiRequest injects it via the auth store
    logout: (): Promise<void> =>
      apiRequest<void>('/api/auth/logout', { method: 'POST' }),
  },

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

  /**
   * Downloads a build's OVA via a single authenticated request, streaming the
   * response body into one pre-sized buffer. The JWT is attached as a header
   * (the token lives in memory only, so a plain anchor link cannot carry it).
   * The downloaded bytes are verified against the build's recorded SHA-256
   * before being handed to the browser, so a corrupted transfer never reaches
   * the user. `onProgress` reports received/total bytes for a progress UI.
   */
  downloadBuild: async (
    id: string,
    filename: string,
    expectedSha256?: string,
    onProgress?: (received: number, total: number) => void,
  ): Promise<void> => {
    const authStore = useAuthStore()
    const authHeaders: Record<string, string> = {}
    if (authStore.token) authHeaders.Authorization = `Bearer ${authStore.token}`

    const resp = await fetch(`${API_BASE}/api/builds/${id}/download`, { headers: authHeaders })
    if (!resp.ok || !resp.body) {
      throw new Error(`Download failed: ${resp.status} ${resp.statusText}`)
    }

    const expectedHash = (expectedSha256 ?? resp.headers.get('X-Checksum-SHA256') ?? '').toLowerCase()
    const total = Number.parseInt(resp.headers.get('Content-Length') ?? '0', 10)

    // Stream the body. When the length is known we write directly into a single
    // contiguous buffer; this avoids holding both a per-chunk array AND a second
    // copy of the whole file, which is what exhausted browser memory on
    // multi-GB OVAs under the old ranged approach.
    const reader = resp.body.getReader()
    let received = 0
    let bytes: Uint8Array<ArrayBuffer>

    if (total > 0) {
      bytes = new Uint8Array(total)
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        bytes.set(value, received)
        received += value.length
        onProgress?.(received, total)
      }
    } else {
      // Unknown length: accumulate then concatenate once.
      const chunks: Uint8Array[] = []
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        chunks.push(value)
        received += value.length
        onProgress?.(received, received)
      }
      bytes = new Uint8Array(received)
      let pos = 0
      for (const c of chunks) {
        bytes.set(c, pos)
        pos += c.length
      }
    }

    if (expectedHash) {
      // Hash the buffer in place — no second large allocation.
      const digest = await crypto.subtle.digest('SHA-256', bytes)
      const actual = Array.from(new Uint8Array(digest))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('')
      if (actual !== expectedHash) {
        throw new Error('Checksum verification failed — the downloaded file is corrupted and was not saved.')
      }
    }

    const url = URL.createObjectURL(new Blob([bytes]))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    // Revoke after a delay so the browser has started writing the file.
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
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

  cloneBuild: async (id: string): Promise<Build> => {
    return apiRequest<Build>(`/api/builds/${id}/clone`, { method: 'POST' })
  },

  // Telemetry
  uploadTelemetry: async (id: string, jsonData: unknown): Promise<TelemetryUploadResponse> => {
    return apiRequest<TelemetryUploadResponse>(`/api/builds/${id}/telemetry`, {
      method: 'POST',
      body: JSON.stringify(jsonData),
    })
  },

  deleteTelemetry: async (id: string): Promise<void> => {
    return apiRequest<void>(`/api/builds/${id}/telemetry`, { method: 'DELETE' })
  },

  // Build logs
  getBuildLogs: async (id: string): Promise<BuildLogEntry[]> => {
    return apiRequest<BuildLogEntry[]>(`/api/builds/${id}/logs`)
  },

  // Flags
  submitFlag: async (requestId: string, flag: string): Promise<{ correct: boolean; attemptsRemaining: number }> => {
    return apiRequest<{ correct: boolean; attemptsRemaining: number }>(
      `/api/builds/${requestId}/flag`,
      {
        method: 'POST',
        body: JSON.stringify({ flagValue: flag }),
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

    // Builds (admin)
    getAllBuilds: async (page = 0, size = 20): Promise<PagedResponse<AdminBuildSummary>> => {
      return apiRequest<PagedResponse<AdminBuildSummary>>(`/api/admin/builds?page=${page}&size=${size}`)
    },

    getBuildCredentials: async (id: string): Promise<AdminBuildCredentials> => {
      return apiRequest<AdminBuildCredentials>(`/api/admin/builds/${id}/credentials`)
    },

    getBuildTelemetry: async (id: string): Promise<TelemetryUploadResponse> => {
      return apiRequest<TelemetryUploadResponse>(`/api/admin/builds/${id}/telemetry`)
    },

    // Metrics
    getMetrics: async (): Promise<AdminMetrics> => {
      return apiRequest<AdminMetrics>('/api/admin/metrics')
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
