import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomePage.vue'),
    },
    {
      path: '/generate',
      name: 'generate',
      component: () => import('@/views/GeneratePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/builds/:requestId',
      name: 'build-status',
      component: () => import('@/views/BuildStatusPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardPage.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

// Auth guard
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    // For now, allow access (mock auth)
    // In production: next({ name: 'login', query: { redirect: to.fullPath } })
    next()
  } else {
    next()
  }
})

export default router
