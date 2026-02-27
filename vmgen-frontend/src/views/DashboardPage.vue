<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useBuilds } from '@/composables/useBuilds'
import { useTemplates } from '@/composables/useTemplates'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'

const router = useRouter()
const { data: builds, isLoading } = useBuilds()
const { data: templates } = useTemplates()

const stateConfig = {
  queued: { label: 'Queued', color: 'info' },
  building: { label: 'Building', color: 'info' },
  exporting: { label: 'Exporting', color: 'info' },
  ready: { label: 'Ready', color: 'success' },
  failed: { label: 'Failed', color: 'danger' },
  archived: { label: 'Archived', color: 'default' },
} as const

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
  const date = new Date(dateString)
  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function getTemplateName(templateId: string) {
  return templates.value?.find(t => t.id === templateId)?.name || templateId
}

function goToBuild(requestId: string) {
  router.push({ name: 'build-status', params: { requestId } })
}
</script>

<template>
  <div class="max-w-7xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-[11px] font-mono tracking-[0.25em] uppercase text-slate-500 mb-1">
            Module // Builds
          </p>
          <h1 class="text-2xl md:text-3xl font-semibold text-slate-50">My builds</h1>
        </div>
        <BaseButton @click="router.push({ name: 'generate' })">
          + New Build
        </BaseButton>
      </div>
      
      <div v-if="isLoading" class="space-y-4">
        <div v-for="i in 5" :key="i" class="h-20 bg-slate-900/60 rounded-lg animate-pulse" />
      </div>
      
      <div v-else-if="builds && builds.length > 0" class="space-y-4">
        <BaseCard
          v-for="build in builds"
          :key="build.request_id"
          padding="md"
          class="cursor-pointer hover:border-sky-500/50 transition-colors"
          @click="goToBuild(build.request_id)"
        >
          <div class="flex items-start justify-between gap-4">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-3 mb-3 flex-wrap">
                <span class="font-mono text-xs text-slate-500">{{ build.request_id }}</span>
                <BaseBadge :variant="stateConfig[build.state].color" size="sm">
                  {{ stateConfig[build.state].label }}
                </BaseBadge>
                <BaseBadge v-if="build.flag_validated" variant="success" size="sm">
                  ✓ Flag Validated
                </BaseBadge>
              </div>
              
              <div class="space-y-2">
                <div class="flex items-center gap-2">
                  <span class="text-sm font-medium text-slate-200">
                    {{ getTemplateName(build.spec.template_id) }}
                  </span>
                </div>
                
                <div class="flex items-center gap-4 text-sm text-slate-400">
                  <span class="flex items-center gap-1">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
                    </svg>
                    {{ build.spec.vulnerability_ids.length }} {{ build.spec.vulnerability_ids.length === 1 ? 'vulnerability' : 'vulnerabilities' }}
                  </span>
                  <span class="flex items-center gap-1">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    {{ formatTimeAgo(build.created_at) }}
                  </span>
                </div>
                
                <div class="text-xs text-slate-500 mt-1">
                  Created: {{ formatTimestamp(build.created_at) }}
                  <span v-if="build.completed_at">
                    • Completed: {{ formatTimestamp(build.completed_at) }}
                  </span>
                </div>
              </div>
            </div>
            <BaseButton variant="ghost" size="sm" @click.stop="goToBuild(build.request_id)">
              View →
            </BaseButton>
          </div>
        </BaseCard>
      </div>
      
      <div v-else class="text-center py-12">
        <p class="text-slate-400 mb-4">No builds yet</p>
        <BaseButton @click="router.push({ name: 'generate' })">
          Create Your First Build
        </BaseButton>
      </div>
    </div>
  </div>
</template>

