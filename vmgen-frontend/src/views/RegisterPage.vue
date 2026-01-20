<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'

const router = useRouter()

const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMessage = ref<string | null>(null)
const errorDetails = ref<string[]>([])
const successMessage = ref<string | null>(null)
const isSubmitting = ref(false)

async function handleSubmit() {
  errorMessage.value = null
  errorDetails.value = []
  successMessage.value = null

  if (password.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match'
    return
  }

  isSubmitting.value = true
  try {
    const response = await fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: username.value,
        email: email.value,
        password: password.value,
      }),
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      errorMessage.value = errorData.message || 'Registration failed'
      errorDetails.value = Array.isArray(errorData.details) ? errorData.details : []
      return
    }

    successMessage.value = 'Account created. You can now sign in.'
    setTimeout(() => {
      router.push({ name: 'login' })
    }, 800)
  } catch (e) {
    errorMessage.value = 'Registration failed'
    errorDetails.value = []
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="min-h-[calc(100vh-72px)] flex items-center justify-center px-6 py-12">
    <BaseCard padding="lg" class="w-full max-w-md">
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-slate-100">Create account</h1>
        <p class="text-sm text-slate-400 mt-1">
          Register a new VMGen account.
        </p>
      </div>

      <form class="space-y-4" @submit.prevent="handleSubmit">
        <div>
          <label class="block text-sm text-slate-300 mb-2">Username</label>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            class="w-full rounded-lg bg-slate-900 border border-slate-800 px-3 py-2 text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="thesis-admin"
            required
          />
        </div>

        <div>
          <label class="block text-sm text-slate-300 mb-2">Email</label>
          <input
            v-model="email"
            type="email"
            autocomplete="email"
            class="w-full rounded-lg bg-slate-900 border border-slate-800 px-3 py-2 text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="admin@example.com"
            required
          />
        </div>

        <div>
          <label class="block text-sm text-slate-300 mb-2">Password</label>
          <input
            v-model="password"
            type="password"
            autocomplete="new-password"
            class="w-full rounded-lg bg-slate-900 border border-slate-800 px-3 py-2 text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="Password"
            required
          />
          <p class="text-xs text-slate-500 mt-1">
            Password must be at least 12 characters and include uppercase, lowercase, digit, and symbol
          </p>
        </div>

        <div>
          <label class="block text-sm text-slate-300 mb-2">Confirm password</label>
          <input
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            class="w-full rounded-lg bg-slate-900 border border-slate-800 px-3 py-2 text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="Repeat password"
            required
          />
        </div>

        <div v-if="errorMessage" class="text-sm text-danger-500 space-y-1">
          <p>{{ errorMessage }}</p>
          <ul v-if="errorDetails.length > 0" class="list-disc list-inside ml-2 space-y-0.5">
            <li v-for="detail in errorDetails" :key="detail">{{ detail }}</li>
          </ul>
        </div>
        <p v-if="successMessage" class="text-sm text-success-500">
          {{ successMessage }}
        </p>

        <BaseButton class="w-full" :loading="isSubmitting" :disabled="isSubmitting">
          Register
        </BaseButton>
      </form>
    </BaseCard>
  </div>
</template>


