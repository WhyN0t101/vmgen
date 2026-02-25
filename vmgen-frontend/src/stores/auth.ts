import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('vmgen_token'))
  const storedRole = localStorage.getItem('vmgen_role')?.toLowerCase()
  const initialRole: 'user' | 'admin' | null =
    storedRole === 'admin' ? 'admin' : storedRole === 'user' ? 'user' : null
  const role = ref<'user' | 'admin' | null>(initialRole)
  const expiresAt = ref<string | null>(localStorage.getItem('vmgen_expires_at'))
  
  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => (user.value?.role || role.value)?.toLowerCase() === 'admin')
  
  async function login(identifier: string, password: string) {
    const response = await fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier, password }),
    })
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      const error = new Error(errorData.message || 'Login failed') as any
      error.response = { status: response.status, data: errorData }
      throw error
    }
    
    const data = await response.json()
    const normalizedRole = (data.role || '').toLowerCase()
    const roleValue = normalizedRole === 'admin' ? 'admin' : normalizedRole === 'user' ? 'user' : null
    token.value = data.token
    role.value = roleValue
    expiresAt.value = data.expiresAt
    
    // Backend response doesn't return user profile yet; keep minimal client-side identity.
    user.value = {
      id: 'unknown',
      email: identifier,
      role: roleValue ?? 'user',
      created_at: new Date().toISOString(),
    }
    
    // TODO: SECURITY — localStorage is XSS-vulnerable. For production, migrate JWT
    // to httpOnly secure cookies (requires backend cookie-setting endpoint). See AGENT_GUIDELINES Rule 12.
    localStorage.setItem('vmgen_token', data.token)
    localStorage.setItem('vmgen_role', roleValue ?? normalizedRole ?? '')
    if (data.expiresAt) {
      localStorage.setItem('vmgen_expires_at', data.expiresAt)
    }
    
    // Trigger storage event for multi-tab sync
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'vmgen_token',
      newValue: data.token
    }))
  }
  
  function logout() {
    // Best-effort server logout; session revocation is enforced server-side.
    const t = token.value
    fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/auth/logout`, {
      method: 'POST',
      headers: t ? { Authorization: `Bearer ${t}` } : undefined,
    }).catch(() => {})
    
    // Clear state
    user.value = null
    token.value = null
    role.value = null
    expiresAt.value = null
    
    // Clear localStorage
    localStorage.removeItem('vmgen_token')
    localStorage.removeItem('vmgen_role')
    localStorage.removeItem('vmgen_expires_at')
    
    // Trigger storage event for multi-tab sync
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'vmgen_token',
      newValue: null
    }))
  }
  
  return {
    user,
    token,
    role,
    expiresAt,
    isAuthenticated,
    isAdmin,
    login,
    logout,
  }
})



