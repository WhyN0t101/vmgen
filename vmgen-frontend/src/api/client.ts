import { useAuthStore } from '@/stores/auth'

const _configuredUrl = import.meta.env.VITE_API_URL
if (!_configuredUrl && import.meta.env.PROD) {
  console.error('[vmgen] VITE_API_URL is not set. All API calls will fail in production.')
} else if (!_configuredUrl) {
  console.warn('[vmgen] VITE_API_URL is not set; falling back to http://localhost:8080 (dev only).')
}
export const API_BASE: string = _configuredUrl || 'http://localhost:8080'

// Global handler for 401 errors - will be set by App.vue
let onUnauthorized: ((message: string) => void) | null = null

export function setUnauthorizedHandler(handler: (message: string) => void) {
  onUnauthorized = handler
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
  
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  })
  
  if (!response.ok) {
    // Handle 401 Unauthorized (session expired/revoked)
    if (response.status === 401) {
      const errorData = await response.json().catch(() => ({}))
      const message = errorData.message || 'Authentication required'
      
      // Clear auth state
      authStore.logout()
      
      // Notify handler (App.vue will handle redirect)
      if (onUnauthorized) {
        onUnauthorized(message)
      }
      
      // Create a proper error object
      const error = new Error(message) as any
      error.response = { status: 401, data: errorData }
      throw error
    }
    
    // Handle other errors
    const errorData = await response.json().catch(() => ({}))
    const error = new Error(errorData.message || `API Error: ${response.statusText}`) as any
    error.response = { status: response.status, data: errorData }
    throw error
  }
  
  return response.json()
}

