<script setup lang="ts">
import { ref, computed } from 'vue'
import { useActiveSessions, useUpdateUserRole } from '@/composables/useAdmin'
import { useAuthStore } from '@/stores/auth'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'

const authStore = useAuthStore()
const { data: sessions } = useActiveSessions()
const { mutate: updateRole, isPending: isUpdating } = useUpdateUserRole()

const updatingUserId = ref<string | null>(null)

// Extract unique users from sessions
const users = computed(() => {
  if (!sessions.value) return []
  
  const userMap = new Map<string, { userId: string; username: string; role: 'user' | 'admin'; sessionCount: number }>()
  
  sessions.value.forEach(session => {
    const existing = userMap.get(session.userId)
    const isAdminRole = (session.role || '').toLowerCase() === 'admin'
    const normalizedRole = isAdminRole ? 'admin' : 'user'
    if (existing) {
      existing.sessionCount++
      if (isAdminRole) {
        existing.role = 'admin'
      }
    } else {
      userMap.set(session.userId, {
        userId: session.userId,
        username: session.username,
        role: normalizedRole,
        sessionCount: 1,
      })
    }
  })
  
  return Array.from(userMap.values())
})

// Find current admin's userId from sessions (if they have an active session)
const currentUserId = computed(() => {
  if (!sessions.value || !authStore.isAdmin) return null
  
  // Find admin sessions that might match current user
  // We match by checking if username matches email pattern or if it's the only admin session
  const adminSessions = sessions.value.filter(s => (s.role || '').toLowerCase() === 'admin')
  
  // If there's only one admin session, it's likely the current user
  if (adminSessions.length === 1) {
    return adminSessions[0].userId
  }
  
  // Try to match by username/email (username might be email or username)
  const currentEmail = authStore.user?.email || ''
  const matchingSession = adminSessions.find(s => 
    s.username === currentEmail || s.username.toLowerCase() === currentEmail.toLowerCase()
  )
  
  return matchingSession?.userId || null
})

// Count admins
const adminCount = computed(() => {
  if (!users.value) return 0
  return users.value.filter(u => (u.role || '').toLowerCase() === 'admin').length
})

function handleRoleChange(userId: string, currentRole: 'user' | 'admin', newRole: 'user' | 'admin') {
  // Prevent self-demotion
  if (currentUserId.value === userId && newRole === 'user') {
    alert('You cannot demote yourself. Ask another administrator to do this.')
    return
  }
  
  // Warn if demoting last admin
  if (currentRole === 'admin' && newRole === 'user' && adminCount.value <= 1) {
    alert('Cannot demote the last administrator account.')
    return
  }
  
  const action = newRole === 'admin' ? 'promote to admin' : 'demote to user'
  if (confirm(`Are you sure you want to ${action}? All their sessions will be revoked and they will need to log in again.`)) {
    updatingUserId.value = userId
    updateRole(
      { userId, role: newRole },
      {
        onSettled: () => {
          updatingUserId.value = null
        },
      }
    )
  }
}

const isCurrentUser = (userId: string) => currentUserId.value === userId
const isLastAdmin = (user: { role: 'user' | 'admin' }) => (user.role || '').toLowerCase() === 'admin' && adminCount.value <= 1
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-2xl font-bold text-slate-100">User Management</h2>
      <span v-if="users" class="text-sm text-slate-400">
        {{ users.length }} user{{ users.length !== 1 ? 's' : '' }}
      </span>
    </div>

    <div v-if="!sessions" class="space-y-4">
      <div v-for="i in 3" :key="i" class="h-20 bg-slate-800/50 rounded-lg animate-pulse" />
    </div>

    <div v-else-if="users && users.length > 0" class="space-y-4">
      <BaseCard
        v-for="user in users"
        :key="user.userId"
        padding="md"
        class="hover:border-primary-500/30 transition-colors"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-3 mb-3 flex-wrap">
              <span class="font-semibold text-slate-200">{{ user.username }}</span>
              <BaseBadge :variant="user.role === 'admin' ? 'danger' : 'info'" size="sm">
                {{ user.role }}
              </BaseBadge>
              <span class="text-xs text-slate-500">{{ user.sessionCount }} active session{{ user.sessionCount !== 1 ? 's' : '' }}</span>
            </div>
            
            <div class="text-sm text-slate-400">
              <span class="font-mono text-xs">{{ user.userId.substring(0, 8) }}...</span>
            </div>
          </div>
          
          <div class="flex flex-col gap-2">
            <BaseButton
              v-if="user.role === 'user'"
              variant="secondary"
              size="sm"
              :loading="updatingUserId === user.userId && isUpdating"
              @click="handleRoleChange(user.userId, user.role, 'admin')"
            >
              Promote to Admin
            </BaseButton>
            <div v-else class="flex flex-col gap-1">
              <BaseButton
                variant="danger"
                size="sm"
                :disabled="isCurrentUser(user.userId) || isLastAdmin(user)"
                :loading="updatingUserId === user.userId && isUpdating"
                @click="handleRoleChange(user.userId, user.role, 'user')"
              >
                Demote to User
              </BaseButton>
              <p v-if="isCurrentUser(user.userId)" class="text-xs text-warning-500 text-center">
                Cannot demote yourself
              </p>
              <p v-else-if="isLastAdmin(user)" class="text-xs text-warning-500 text-center">
                Last admin
              </p>
            </div>
          </div>
        </div>
      </BaseCard>
    </div>

    <div v-else class="text-center py-12">
      <p class="text-slate-400">No users found</p>
      <p class="text-sm text-slate-500 mt-2">Users will appear here when they have active sessions</p>
    </div>
  </div>
</template>
