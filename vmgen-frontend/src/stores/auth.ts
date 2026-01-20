import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))
  const role = ref<'user' | 'admin' | null>((localStorage.getItem('role') as 'user' | 'admin' | null) || null)
  
  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => (user.value?.role || role.value) === 'admin')
  
  async function login(identifier: string, password: string) {
    const response = await fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier, password }),
    })
    
    if (!response.ok) throw new Error('Login failed')
    
    const data = await response.json()
    token.value = data.token
    role.value = data.role
    // Backend response doesn't return user profile yet; keep minimal client-side identity.
    user.value = {
      id: 'unknown',
      email: identifier,
      role: data.role,
      created_at: new Date().toISOString(),
    }
    localStorage.setItem('token', data.token)
    localStorage.setItem('role', data.role)
  }
  
  function logout() {
    // Best-effort server logout; session revocation is enforced server-side.
    const t = token.value
    fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/auth/logout`, {
      method: 'POST',
      headers: t ? { Authorization: `Bearer ${t}` } : undefined,
    }).catch(() => {})
    user.value = null
    token.value = null
    role.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('role')
  }
  
  return {
    user,
    token,
    role,
    isAuthenticated,
    isAdmin,
    login,
    logout,
  }
})



