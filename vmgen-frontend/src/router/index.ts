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
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginPage.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterPage.vue'),
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
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/AdminPanelPage.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    // Admin Scripts
    {
      path: '/admin/scripts',
      name: 'admin-scripts',
      component: () => import('@/views/admin/scripts/ScriptListPage.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/scripts/new',
      name: 'admin-script-create',
      component: () => import('@/views/admin/scripts/ScriptFormPage.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/scripts/:id/edit',
      name: 'admin-script-edit',
      component: () => import('@/views/admin/scripts/ScriptFormPage.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    // Admin Template Routes
    {
      path: '/admin/templates',
      name: 'admin-template-list',
      component: () => import('@/views/admin/templates/TemplateList.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/templates/new',
      name: 'admin-template-register',
      component: () => import('@/views/admin/templates/TemplateRegister.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/templates/:id',
      name: 'admin-template-detail',
      component: () => import('@/views/admin/templates/TemplateDetail.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/admin/templates/:id/edit',
      name: 'admin-template-edit',
      component: () => import('@/views/admin/templates/TemplateEdit.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    // Public Template Routes
    {
      path: '/templates',
      name: 'templates',
      component: () => import('@/views/templates/TemplateCatalog.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/trust',
      name: 'trust',
      component: () => import('@/views/TrustModelPage.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

// Auth guard
router.beforeEach((to, _from, next) => {
  try {
    const authStore = useAuthStore()
    
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
    
    if (to.meta.requiresAdmin && !authStore.isAdmin) {
      next({ name: 'dashboard' })
      return
    }
    
    next()
  } catch (error) {
    // Fallback: redirect to login on guard error to avoid exposing protected routes
    // eslint-disable-next-line no-console
    console.error('Router guard error:', error)
    next({ name: 'login' })
  }
})

export default router
