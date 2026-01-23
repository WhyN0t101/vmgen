<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useSessionMonitor } from '@/composables/useSessionMonitor'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const sessionMonitor = useSessionMonitor()

const identifier = ref('')
const password = ref('')
const errorMessage = ref<string | null>(null)
const isSubmitting = ref(false)

async function handleSubmit() {
  errorMessage.value = null
  isSubmitting.value = true
  try {
    await authStore.login(identifier.value, password.value)
    
    // Start session expiration monitoring
    if (authStore.expiresAt) {
      sessionMonitor.startMonitoring(authStore.expiresAt)
    }
    
    const redirect = (route.query.redirect as string | undefined) || '/dashboard'
    // Use push instead of replace to ensure navigation happens
    await router.push(redirect)
  } catch (e: any) {
    const message = e.response?.data?.message || e.message || 'Login failed'
    
    // Map backend error messages to user-friendly messages
    if (message === 'Authentication failed') {
      errorMessage.value = 'Invalid username or password'
    } else if (message.includes('Session')) {
      errorMessage.value = 'Unable to create session. Please try again.'
    } else if (!e.response) {
      errorMessage.value = 'Cannot connect to server. Please check your connection.'
    } else {
      errorMessage.value = 'An unexpected error occurred. Please try again.'
    }
  } finally {
    isSubmitting.value = false
  }
}

// Start monitoring if already logged in (e.g., page refresh)
onMounted(() => {
  if (authStore.expiresAt && authStore.isAuthenticated) {
    sessionMonitor.startMonitoring(authStore.expiresAt)
  }
})

onUnmounted(() => {
  sessionMonitor.stopMonitoring()
})
</script>

<template>
  <div class="min-h-[calc(100vh-72px)] flex items-center justify-center px-6 py-12">
    <BaseCard padding="lg" class="w-full max-w-md">
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-slate-100">Sign in</h1>
        <p class="text-sm text-slate-400 mt-1">
          Use your username or email.
        </p>
      </div>

      <form class="space-y-4" @submit.prevent="handleSubmit">
        <div>
          <label class="block text-sm text-slate-300 mb-2">Username / Email</label>
          <input
            v-model="identifier"
            type="text"
            autocomplete="username"
            class="w-full rounded-lg bg-slate-900 border border-slate-800 px-3 py-2 text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="thesis-admin or admin@example.com"
            required
          />
        </div>

        <div>
          <label class="block text-sm text-slate-300 mb-2">Password</label>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            class="w-full rounded-lg bg-slate-900 border border-slate-800 px-3 py-2 text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="••••••••••••"
            required
          />
        </div>

        <p v-if="errorMessage" class="text-sm text-danger-500">
          {{ errorMessage }}
        </p>

        <BaseButton class="w-full" :loading="isSubmitting" :disabled="isSubmitting">
          Sign in
        </BaseButton>
      </form>
    </BaseCard>
  </div>
</template>


