import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSessionStore } from '@/stores/session'
import { useToast } from '@/composables/useToast'

/**
 * Track a single JWT/session expiry on the client and show a toast
 * warning before logout plus an automatic redirect.
 */
export function useSessionMonitor() {
  const router = useRouter()
  const authStore = useAuthStore()
  const sessionStore = useSessionStore()
  const { show } = useToast()

  function startMonitoring(expiresAt: string) {
    // Clear any existing timeouts
    stopMonitoring()

    const expiryTime = new Date(expiresAt).getTime()
    const now = Date.now()
    const timeUntilExpiry = expiryTime - now

    // Warn 5 minutes before expiration
    const warningTime = timeUntilExpiry - (5 * 60 * 1000)

    if (warningTime > 0) {
      sessionStore.warningTimeoutId = window.setTimeout(() => {
        if (!sessionStore.warningShown && authStore.isAuthenticated) {
          show('Your session will expire in 5 minutes. Save your work.', 'warning')
          sessionStore.warningShown = true
        }
      }, warningTime)
    }

    // Handle actual expiration
    if (timeUntilExpiry > 0) {
      sessionStore.expiryTimeoutId = window.setTimeout(() => {
        if (authStore.isAuthenticated) {
          authStore.logout()
          show('Your session has expired. Please log in again.', 'info')
          router.push({
            path: '/login',
            query: { redirect: router.currentRoute.value.fullPath }
          })
        }
      }, timeUntilExpiry)
    } else if (authStore.isAuthenticated) {
      // Token is already expired (e.g. page loaded with a stale stored session).
      authStore.logout()
      show('Your session has expired. Please log in again.', 'info')
      router.push({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
    }
  }

  function stopMonitoring() {
    sessionStore.clearTimeouts()
  }

  return {
    startMonitoring,
    stopMonitoring,
  }
}
