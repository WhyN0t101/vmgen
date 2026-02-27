<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import { onMounted } from 'vue'
import AppNavbar from '@/components/layout/AppNavbar.vue'
import { useAuthSync } from './composables/useAuthSync'
import { setUnauthorizedHandler } from './api/client'

const router = useRouter()

// Multi-tab synchronization
useAuthSync()

// Handle 401 errors globally
onMounted(() => {
  setUnauthorizedHandler((message: string) => {
    const currentRoute = router.currentRoute.value
    if (!currentRoute.path.startsWith('/login') && !currentRoute.path.startsWith('/register')) {
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
  </div>
</template>

<style scoped></style>
