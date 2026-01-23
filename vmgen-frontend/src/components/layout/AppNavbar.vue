<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BaseButton from '@/components/ui/BaseButton.vue'

const router = useRouter()
const authStore = useAuthStore()

function logout() {
  authStore.logout()
  router.push({ name: 'home' })
}
</script>

<template>
  <nav class="border-b border-slate-800 bg-slate-900/50 backdrop-blur-sm">
    <div class="container mx-auto px-6 py-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-8">
          <router-link to="/" class="flex items-center gap-2 text-xl font-bold text-slate-100 hover:text-primary-400 transition-colors">
            <svg class="w-6 h-6 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
            <span>VMGen</span>
          </router-link>
          <div class="hidden md:flex gap-6">
            <router-link
              to="/generate"
              class="text-slate-400 hover:text-slate-100 transition-colors"
              active-class="text-primary-500"
            >
              Generate
            </router-link>
            <router-link
              to="/dashboard"
              class="text-slate-400 hover:text-slate-100 transition-colors"
              active-class="text-primary-500"
            >
              Dashboard
            </router-link>
            <router-link
              to="/templates"
              class="text-slate-400 hover:text-slate-100 transition-colors"
              active-class="text-primary-500"
            >
              Templates
            </router-link>
            <router-link
              v-if="authStore.isAdmin"
              to="/admin"
              class="text-slate-400 hover:text-slate-100 transition-colors"
              active-class="text-primary-500"
            >
              Admin
            </router-link>
            <router-link
              v-if="authStore.isAdmin"
              to="/admin/templates"
              class="text-slate-400 hover:text-slate-100 transition-colors"
              active-class="text-primary-500"
            >
              Manage Templates
            </router-link>
          </div>
        </div>
        <div class="flex items-center gap-4">
          <span v-if="authStore.user" class="text-sm text-slate-400">
            {{ authStore.user.email }}
          </span>
          <BaseButton
            v-if="!authStore.isAuthenticated"
            variant="ghost"
            size="sm"
            @click="router.push({ name: 'register' })"
          >
            Register
          </BaseButton>
          <BaseButton
            v-if="!authStore.isAuthenticated"
            variant="secondary"
            size="sm"
            @click="router.push({ name: 'login' })"
          >
            Login
          </BaseButton>
          <BaseButton v-if="authStore.isAuthenticated" variant="ghost" size="sm" @click="logout">
            Logout
          </BaseButton>
        </div>
      </div>
    </div>
  </nav>
</template>



