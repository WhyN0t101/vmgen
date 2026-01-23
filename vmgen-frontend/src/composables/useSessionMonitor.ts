import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

let warningTimeoutId: number | null = null
let expiryTimeoutId: number | null = null
const warningShown = ref(false)

export function useSessionMonitor() {
  const router = useRouter()
  const authStore = useAuthStore()

  function startMonitoring(expiresAt: string) {
    // Clear any existing timeouts
    stopMonitoring()

    const expiryTime = new Date(expiresAt).getTime()
    const now = Date.now()
    const timeUntilExpiry = expiryTime - now

    // Warn 5 minutes before expiration
    const warningTime = timeUntilExpiry - (5 * 60 * 1000)

    if (warningTime > 0) {
      warningTimeoutId = window.setTimeout(() => {
        if (!warningShown.value && authStore.isAuthenticated) {
          // Show warning notification (you can integrate with a notification system)
          console.warn('Your session will expire in 5 minutes. Save your work.')
          // TODO: Replace with actual notification component
          // showNotification('Your session will expire in 5 minutes. Save your work.', 'warning')
          warningShown.value = true
        }
      }, warningTime)
    }

    // Handle actual expiration
    if (timeUntilExpiry > 0) {
      expiryTimeoutId = window.setTimeout(() => {
        if (authStore.isAuthenticated) {
          authStore.logout()
          router.push({
            path: '/login',
            query: { redirect: router.currentRoute.value.fullPath }
          })
          // TODO: Replace with actual notification component
          // showNotification('Your session has expired. Please log in again.', 'info')
        }
      }, timeUntilExpiry)
    }
  }

  function stopMonitoring() {
    if (warningTimeoutId !== null) {
      clearTimeout(warningTimeoutId)
      warningTimeoutId = null
    }
    if (expiryTimeoutId !== null) {
      clearTimeout(expiryTimeoutId)
      expiryTimeoutId = null
    }
    warningShown.value = false
  }

  return {
    startMonitoring,
    stopMonitoring,
  }
}
