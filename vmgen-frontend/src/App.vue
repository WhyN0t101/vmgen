<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import { onMounted } from 'vue'
import AppNavbar from '@/components/layout/AppNavbar.vue'
import ToastContainer from '@/components/ui/ToastContainer.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { useAuthSync } from './composables/useAuthSync'
import { setUnauthorizedHandler } from './api/client'
import { useToast } from './composables/useToast'

const router = useRouter()
const { show } = useToast()

// Multi-tab synchronization
useAuthSync()

// Handle 401 errors globally
onMounted(() => {
  setUnauthorizedHandler((message: string) => {
    const currentRoute = router.currentRoute.value
    if (!currentRoute.path.startsWith('/login') && !currentRoute.path.startsWith('/register')) {
      show(message || 'Your session has expired. Please log in again.', 'info')
      router.push({
        path: '/login',
        query: { redirect: currentRoute.fullPath }
      })
    }
  })
})
</script>

<template>
  <div class="app-shell">
    <AppNavbar />
    <main>
      <RouterView />
    </main>
    <ToastContainer />
    <ConfirmDialog />
  </div>
</template>

<style scoped></style>
