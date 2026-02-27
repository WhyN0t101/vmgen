<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useBuildStatus } from '@/composables/useBuildStatus'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseProgress from '@/components/ui/BaseProgress.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'

const route = useRoute()
const requestId = computed(() => route.params.requestId as string)
const { data: build, isLoading } = useBuildStatus(requestId)

const stateConfig = {
  queued: { label: 'Queued', color: 'info', icon: '⏳' },
  building: { label: 'Building', color: 'info', icon: '⟳' },
  exporting: { label: 'Exporting', color: 'info', icon: '📦' },
  ready: { label: 'Ready', color: 'success', icon: '✓' },
  failed: { label: 'Failed', color: 'danger', icon: '✗' },
  archived: { label: 'Archived', color: 'default', icon: '📁' },
} as const

const stateInfo = computed(() => {
  if (!build.value) return null
  return stateConfig[build.value.state]
})

function formatTimeAgo(dateString: string) {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  
  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins} minute${diffMins > 1 ? 's' : ''} ago`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`
}
</script>

<template>
  <div class="max-w-4xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <div v-if="isLoading" class="space-y-4">
        <div class="h-8 bg-slate-900/60 rounded animate-pulse" />
        <div class="h-64 bg-slate-900/60 rounded animate-pulse" />
      </div>
      
      <div v-else-if="build" class="space-y-6">
        <!-- Header -->
        <div>
          <p class="text-[11px] font-mono tracking-[0.25em] uppercase text-slate-500 mb-1">
            Module // Build Status
          </p>
          <div class="flex items-center justify-between mb-2">
            <h1 class="text-2xl md:text-3xl font-semibold text-slate-50">Build status</h1>
            <BaseBadge v-if="stateInfo" :variant="stateInfo.color">
              {{ stateInfo.icon }} {{ stateInfo.label }}
            </BaseBadge>
          </div>
          <div class="flex items-center gap-4 text-sm text-slate-400">
            <span class="font-mono">{{ build.request_id }}</span>
            <span>•</span>
            <span>Created: {{ formatTimeAgo(build.created_at) }}</span>
          </div>
        </div>
        
        <!-- Progress -->
        <BaseCard v-if="build.progress !== undefined" padding="md">
          <BaseProgress :value="build.progress" />
          <p v-if="build.message" class="text-sm text-slate-400 mt-2">
            {{ build.message }}
          </p>
        </BaseCard>
        
        <!-- Ready State -->
        <BaseCard v-if="build.state === 'ready'" padding="lg">
          <div class="text-center mb-6">
            <div class="text-4xl mb-2">✓</div>
            <h2 class="text-2xl font-bold text-slate-100 mb-2">Build Complete</h2>
            <p class="text-slate-400">Your vulnerable VM is ready for download.</p>
          </div>
          
          <div class="space-y-4">
            <div class="bg-slate-800/60 rounded-lg p-4">
              <div class="flex items-center justify-between mb-2">
                <span class="text-slate-300">📦 vm-{{ build.request_id }}.ova</span>
                <span class="text-sm text-slate-400">2.4 GB</span>
              </div>
              <BaseButton class="w-full mt-2">Download OVA</BaseButton>
            </div>
            
            <div class="bg-slate-800/60 rounded-lg p-4">
              <div class="flex items-center justify-between mb-2">
                <span class="text-slate-300">📄 provenance-{{ build.request_id }}.json</span>
                <span class="text-sm text-slate-400">8 KB</span>
              </div>
              <BaseButton variant="secondary" class="w-full mt-2">Download Manifest</BaseButton>
            </div>
          </div>
          
          <div class="mt-6 pt-6 border-t border-slate-800 flex gap-4">
            <BaseButton variant="primary" class="flex-1">Submit Flag</BaseButton>
            <BaseButton variant="secondary" class="flex-1">Clone Configuration</BaseButton>
          </div>
        </BaseCard>
        
        <!-- Failed State -->
        <BaseCard v-if="build.state === 'failed'" padding="lg">
          <div class="text-center mb-6">
            <div class="text-4xl mb-2">✗</div>
            <h2 class="text-2xl font-bold text-danger-500 mb-2">Build Failed</h2>
            <p v-if="build.error_message" class="text-slate-400 font-mono text-sm bg-slate-800/60 p-4 rounded">
              {{ build.error_message }}
            </p>
          </div>
          <div class="flex gap-4">
            <BaseButton variant="primary" class="flex-1">Retry Build</BaseButton>
            <BaseButton variant="secondary" class="flex-1">Clone Configuration</BaseButton>
          </div>
        </BaseCard>
      </div>
      
      <div v-else class="text-center py-12">
        <p class="text-slate-400">Build not found</p>
      </div>
    </div>
  </div>
</template>



