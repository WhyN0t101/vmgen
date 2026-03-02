import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSessionStore = defineStore('session', () => {
  const warningTimeoutId = ref<number | null>(null)
  const expiryTimeoutId = ref<number | null>(null)
  const warningShown = ref(false)

  function clearTimeouts() {
    if (warningTimeoutId.value !== null) {
      clearTimeout(warningTimeoutId.value)
      warningTimeoutId.value = null
    }
    if (expiryTimeoutId.value !== null) {
      clearTimeout(expiryTimeoutId.value)
      expiryTimeoutId.value = null
    }
    warningShown.value = false
  }

  return {
    warningTimeoutId,
    expiryTimeoutId,
    warningShown,
    clearTimeouts,
  }
})
