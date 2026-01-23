import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export function useAuthSync() {
  const router = useRouter()
  const authStore = useAuthStore()

  function handleStorageChange(event: StorageEvent) {
    // Listen for logout events from other tabs
    if (event.key === 'vmgen_token' && event.newValue === null) {
      // Another tab logged out - sync this tab
      if (authStore.isAuthenticated) {
        authStore.logout()
        router.push('/login')
        // TODO: Replace with actual notification component
        // showNotification('You have been logged out', 'info')
        console.info('Logged out due to logout in another tab')
      }
    }
    
    // Listen for login events from other tabs (optional - for consistency)
    if (event.key === 'vmgen_token' && event.newValue !== null && !authStore.isAuthenticated) {
      // Another tab logged in - refresh auth state
      const token = localStorage.getItem('vmgen_token')
      const role = localStorage.getItem('vmgen_role')
      if (token && role) {
        // Reload page to sync state (simpler than manually updating store)
        window.location.reload()
      }
    }
  }

  onMounted(() => {
    window.addEventListener('storage', handleStorageChange)
  })

  onUnmounted(() => {
    window.removeEventListener('storage', handleStorageChange)
  })
}
