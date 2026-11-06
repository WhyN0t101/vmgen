import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import { rawRequest } from '@/api/client'
// rawRequest is imported instead of re-declaring API_BASE locally.
// The resulting circular dependency (client.ts ↔ auth.ts) is safe because
// neither module consumes the other's exports at module-initialisation time —
// only inside function bodies that run after both modules are fully loaded.

const AUTH_CHANNEL = 'vmgen_auth'

export const useAuthStore = defineStore('auth', () => {
  // JWT is kept in memory only — never written to localStorage or sessionStorage.
  // Page refresh requires re-authentication. This prevents XSS scripts from
  // extracting long-lived tokens.
  const user = ref<User | null>(null)
  const token = ref<string | null>(null)
  const role = ref<'user' | 'admin' | null>(null)
  const expiresAt = ref<string | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => (user.value?.role || role.value)?.toLowerCase() === 'admin')

  async function login(identifier: string, password: string) {
    const data = await rawRequest<{ token: string; role: string; expiresAt: string; userId: string }>(
      '/api/auth/login',
      { method: 'POST', body: JSON.stringify({ identifier, password }) },
    )

    const normalizedRole = (data.role || '').toLowerCase()
    const roleValue = normalizedRole === 'admin' ? 'admin' : normalizedRole === 'user' ? 'user' : null
    token.value = data.token
    role.value = roleValue
    expiresAt.value = data.expiresAt

    user.value = {
      id: data.userId,
      email: identifier,
      role: roleValue ?? 'user',
      created_at: new Date().toISOString(),
    }

    // Notify other tabs via BroadcastChannel (no storage writes)
    try {
      new BroadcastChannel(AUTH_CHANNEL).postMessage({ type: 'login' })
    } catch { /* BroadcastChannel unavailable in some environments */ }
  }

  function logout() {
    // Best-effort server logout; session revocation is enforced server-side.
    const t = token.value
    rawRequest<void>('/api/auth/logout', {
      method: 'POST',
      headers: t ? { Authorization: `Bearer ${t}` } : {},
    }).catch(() => {})

    // Clear in-memory state
    user.value = null
    token.value = null
    role.value = null
    expiresAt.value = null

    // Notify other tabs via BroadcastChannel (no storage writes)
    try {
      new BroadcastChannel(AUTH_CHANNEL).postMessage({ type: 'logout' })
    } catch { /* BroadcastChannel unavailable in some environments */ }
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
