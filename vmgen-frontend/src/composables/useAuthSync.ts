import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const AUTH_CHANNEL = 'vmgen_auth'

/**
 * Keep authentication state in sync across multiple browser tabs using
 * BroadcastChannel. No localStorage is involved — messages are delivered
 * only to other tabs in the same origin during the same browser session.
 */
export function useAuthSync() {
  const router = useRouter()
  const authStore = useAuthStore()
  const { show } = useToast()

  let channel: BroadcastChannel | null = null

  function handleMessage(event: MessageEvent) {
    if (event.data?.type === 'logout' && authStore.isAuthenticated) {
      authStore.logout()
      show('You have been logged out in another tab.', 'info')
      router.push('/login')
    }
  }

  onMounted(() => {
    try {
      channel = new BroadcastChannel(AUTH_CHANNEL)
      channel.addEventListener('message', handleMessage)
    } catch { /* BroadcastChannel unavailable in some environments */ }
  })

  onUnmounted(() => {
    channel?.close()
  })
}
