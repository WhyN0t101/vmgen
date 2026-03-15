import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/types'

const _configuredUrl = import.meta.env.VITE_API_URL
if (!_configuredUrl && import.meta.env.PROD) {
  console.error('[vmgen] VITE_API_URL is not set. All API calls will fail in production.')
} else if (!_configuredUrl) {
  console.warn('[vmgen] VITE_API_URL is not set; falling back to http://localhost:8080 (dev only).')
}
export const API_BASE: string = _configuredUrl || 'http://localhost:8080'

// Global handlers set by App.vue
let onUnauthorized: ((message: string) => void) | null = null
let onForbidden: ((message: string) => void) | null = null

export function setUnauthorizedHandler(handler: (message: string) => void) {
  onUnauthorized = handler
}

export function setForbiddenHandler(handler: (message: string) => void) {
  onForbidden = handler
}

function buildError(status: number, statusText: string, errorData: Record<string, unknown>): ApiError {
  const message = (errorData.message as string | undefined) || `API Error: ${statusText}`
  const error = new Error(message) as ApiError
  error.response = { status, data: errorData }
  return error
}

function hasBody(response: Response): boolean {
  if (response.status === 204) return false
  const contentLength = response.headers.get('content-length')
  return contentLength !== '0'
}

/**
 * Unauthenticated HTTP request — for public endpoints (login, register, logout).
 * Does not inject Authorization headers or trigger the 401/logout flow.
 * NOTE: auth.ts imports this to avoid re-declaring API_BASE; the resulting circular
 * dependency is safe because neither module uses the other's exports at init time.
 */
export async function rawRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  }

  const response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw buildError(response.status, response.statusText, errorData)
  }

  if (!hasBody(response)) return undefined as T
  return response.json()
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const authStore = useAuthStore()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  }

  if (authStore.token) {
    headers['Authorization'] = `Bearer ${authStore.token}`
  }

  const response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))

    if (response.status === 401) {
      const message = (errorData.message as string | undefined) || 'Authentication required'
      authStore.logout()
      if (onUnauthorized) onUnauthorized(message)
      const error = new Error(message) as ApiError
      error.response = { status: 401, data: errorData }
      throw error
    }

    if (response.status === 403) {
      const message = (errorData.message as string | undefined) || 'Insufficient permissions'
      if (onForbidden) onForbidden(message)
      const error = new Error(message) as ApiError
      error.response = { status: 403, data: errorData }
      throw error
    }

    throw buildError(response.status, response.statusText, errorData)
  }

  if (!hasBody(response)) return undefined as T
  return response.json()
}
