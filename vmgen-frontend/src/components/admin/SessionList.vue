<script setup lang="ts">
import { ref } from 'vue'
import { useActiveSessions, useRevokeSession, useRevokeAllUserSessions } from '@/composables/useAdmin'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'

const { data: sessions, isLoading } = useActiveSessions()
const { mutate: revokeSession, isPending: isRevoking } = useRevokeSession()
const { mutate: revokeAll, isPending: isRevokingAll } = useRevokeAllUserSessions()

const revokingSessionId = ref<string | null>(null)
const revokingUserId = ref<string | null>(null)

function formatTimeAgo(dateString: string) {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  
  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins}m ago`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}h ago`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}d ago`
}

function formatTimestamp(dateString: string) {
  return new Date(dateString).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function handleRevokeSession(sessionId: string, userId: string) {
  // Allow revoking own session (it's just logging out, which is fine)
  const message = 'Are you sure you want to revoke this session? The user will be logged out.'
  if (confirm(message)) {
    revokingSessionId.value = sessionId
    revokeSession(sessionId, {
      onSettled: () => {
        revokingSessionId.value = null
      },
    })
  }
}

function handleRevokeAll(userId: string) {
  // Allow revoking own sessions (it's just logging out from all devices)
  const message = 'Are you sure you want to revoke all sessions for this user? They will be logged out from all devices.'
  if (confirm(message)) {
    revokingUserId.value = userId
    revokeAll(userId, {
      onSettled: () => {
        revokingUserId.value = null
      },
    })
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-2xl font-bold text-slate-100">Active Sessions</h2>
      <span v-if="sessions" class="text-sm text-slate-400">
        {{ sessions.length }} active session{{ sessions.length !== 1 ? 's' : '' }}
      </span>
    </div>

    <div v-if="isLoading" class="space-y-4">
      <div v-for="i in 3" :key="i" class="h-20 bg-slate-800/50 rounded-lg animate-pulse" />
    </div>

    <div v-else-if="sessions && sessions.length > 0" class="space-y-4">
      <BaseCard
        v-for="session in sessions"
        :key="session.sessionId"
        padding="md"
        class="hover:border-primary-500/30 transition-colors"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-3 mb-3 flex-wrap">
              <span class="font-mono text-sm text-slate-300">{{ session.username }}</span>
              <BaseBadge :variant="session.role === 'admin' ? 'danger' : 'info'" size="sm">
                {{ session.role }}
              </BaseBadge>
              <span class="text-xs text-slate-500 font-mono">{{ session.sessionId.substring(0, 8) }}...</span>
            </div>
            
            <div class="space-y-2 text-sm">
              <div class="flex items-center gap-4 text-slate-400">
                <span>User ID: <span class="font-mono text-slate-300">{{ session.userId.substring(0, 8) }}...</span></span>
              </div>
              
              <div class="flex items-center gap-4 text-slate-400">
                <span>Created: <span class="text-slate-300">{{ formatTimeAgo(session.createdAt) }}</span></span>
                <span>Last activity: <span class="text-slate-300">{{ formatTimeAgo(session.lastActivityAt) }}</span></span>
              </div>
              
              <div class="text-xs text-slate-500">
                Expires: {{ formatTimestamp(session.expiresAt) }}
              </div>
            </div>
          </div>
          
          <div class="flex flex-col gap-2">
            <BaseButton
              variant="ghost"
              size="sm"
              :loading="revokingSessionId === session.sessionId && isRevoking"
              @click="handleRevokeSession(session.sessionId, session.userId)"
            >
              Revoke
            </BaseButton>
            <BaseButton
              variant="ghost"
              size="sm"
              :loading="revokingUserId === session.userId && isRevokingAll"
              @click="handleRevokeAll(session.userId)"
            >
              Revoke All
            </BaseButton>
          </div>
        </div>
      </BaseCard>
    </div>

    <div v-else class="text-center py-12">
      <p class="text-slate-400">No active sessions</p>
    </div>
  </div>
</template>
