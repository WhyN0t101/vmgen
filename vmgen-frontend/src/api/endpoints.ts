import { apiRequest } from './client'
import type { Template, Vulnerability, Build, BuildSpec, TemplateResponse, TemplateListResponse, RegisterTemplateRequest, UpdateTemplateMetadataRequest, ProxmoxVmSummary } from '@/types'
import { mockTemplates, mockVulnerabilities } from './mockData'

// Use mock data for development
// Set to false to use real API endpoints
// TODO: Make this configurable via environment variable
const USE_MOCK = true

export const api = {
  // Templates
  getTemplates: async (): Promise<Template[]> => {
    if (USE_MOCK) {
      return new Promise((resolve) => {
        setTimeout(() => resolve(mockTemplates), 500)
      })
    }
    return apiRequest<Template[]>('/api/templates')
  },
  
  // Vulnerabilities
  getVulnerabilities: async (templateId?: string): Promise<Vulnerability[]> => {
    if (USE_MOCK) {
      return new Promise((resolve) => {
        setTimeout(() => {
          // Filter by template capabilities if templateId provided
          const template = mockTemplates.find(t => t.id === templateId)
          if (templateId && template) {
            const filtered = mockVulnerabilities.filter(vuln =>
              vuln.requires_capabilities.every(cap =>
                template.capabilities.includes(cap)
              )
            )
            resolve(filtered)
          } else {
            resolve(mockVulnerabilities)
          }
        }, 500)
      })
    }
    return apiRequest<Vulnerability[]>(
      `/api/vulnerabilities${templateId ? `?template=${templateId}` : ''}`
    )
  },
  
  // Builds
  createBuild: async (spec: BuildSpec): Promise<Build> => {
    if (USE_MOCK) {
      return new Promise((resolve) => {
        setTimeout(() => {
          const build: Build = {
            request_id: `req-${Math.random().toString(36).substr(2, 9)}`,
            user_id: 'user-123',
            spec,
            state: 'queued',
            created_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
            flag_validated: false,
          }
          resolve(build)
        }, 1000)
      })
    }
    return apiRequest<Build>('/api/builds', {
      method: 'POST',
      body: JSON.stringify(spec),
    })
  },
  
  getBuildStatus: async (requestId: string): Promise<Build> => {
    if (USE_MOCK) {
      return new Promise((resolve) => {
        setTimeout(() => {
          const build: Build = {
            request_id: requestId,
            user_id: 'user-123',
            spec: {
              template_id: 'ubuntu-20.04-v2.1.0',
              vulnerability_ids: ['sql-injection-v1.0'],
              parameters: { difficulty: 'medium', telemetry_enabled: false },
            },
            state: 'building',
            progress: 65,
            message: 'Injecting vulnerabilities...',
            created_at: new Date(Date.now() - 120000).toISOString(),
            updated_at: new Date().toISOString(),
            flag_validated: false,
          }
          resolve(build)
        }, 500)
      })
    }
    return apiRequest<Build>(`/api/builds/${requestId}/status`)
  },
  
  getUserBuilds: async (): Promise<Build[]> => {
    if (USE_MOCK) {
      return new Promise((resolve) => {
        setTimeout(() => {
          resolve([
            {
              request_id: 'req-a3f9c2b7',
              user_id: 'user-123',
              spec: {
                template_id: 'ubuntu-20.04-v2.1.0',
                vulnerability_ids: ['sql-injection-v1.0'],
                parameters: { difficulty: 'medium', telemetry_enabled: false },
              },
              state: 'ready',
              created_at: new Date(Date.now() - 120000).toISOString(),
              updated_at: new Date().toISOString(),
              completed_at: new Date(Date.now() - 60000).toISOString(),
              flag_validated: false,
              ova_url: '/downloads/vm-req-a3f9c2b7.ova',
              provenance_url: '/downloads/provenance-req-a3f9c2b7.json',
            },
          ])
        }, 500)
      })
    }
    return apiRequest<Build[]>('/api/builds')
  },
  
  // Flags
  submitFlag: async (requestId: string, flag: string): Promise<{ valid: boolean; message: string }> => {
    if (USE_MOCK) {
      return new Promise((resolve) => {
        setTimeout(() => {
          // Mock validation - accept any flag starting with "FLAG{"
          const valid = flag.startsWith('FLAG{')
          resolve({
            valid,
            message: valid ? 'Flag validated successfully' : 'Invalid flag',
          })
        }, 1000)
      })
    }
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
        body: JSON.stringify({ role }),
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

    listProxmoxVms: async (node?: string): Promise<ProxmoxVmSummary[]> => {
      const url = node ? `/api/admin/proxmox/vms?node=${node}` : '/api/admin/proxmox/vms'
      return apiRequest<ProxmoxVmSummary[]>(url)
    },

    getVmStorage: async (vmid: number, node?: string): Promise<{ storage: string }> => {
      const nodeParam = node ? `&node=${node}` : ''
      return apiRequest<{ storage: string }>(`/api/admin/proxmox/vm-storage?vmid=${vmid}${nodeParam}`)
    },
  },

  // Public template endpoints
  templates: {
    listActive: async (): Promise<TemplateListResponse[]> => {
      return apiRequest<TemplateListResponse[]>('/api/templates')
    },

    getById: async (id: string): Promise<TemplateResponse> => {
      return apiRequest<TemplateResponse>(`/api/templates/${id}`)
    },
  },
}



