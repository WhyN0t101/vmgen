import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))
  
  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin')
  
  async function login(email: string, password: string) {
    // Mock login for now
    const response = await fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8000'}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    }).catch(() => {
      // Mock login if API is not available
      return {
        ok: true,
        json: async () => ({
          token: 'mock-token-' + Date.now(),
          user: {
            id: 'user-123',
            email,
            role: 'user' as const,
            created_at: new Date().toISOString(),
          },
        }),
      } as Response
    })
    
    if (!response.ok) throw new Error('Login failed')
    
    const data = await response.json()
    token.value = data.token
    user.value = data.user
    localStorage.setItem('token', data.token)
  }
  
  function logout() {
    user.value = null
    token.value = null
    localStorage.removeItem('token')
  }
  
  return {
    user,
    token,
    isAuthenticated,
    isAdmin,
    login,
    logout,
  }
})



