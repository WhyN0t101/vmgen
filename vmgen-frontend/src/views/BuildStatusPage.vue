<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useBuildStatus } from '@/composables/useBuildStatus'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import { formatTimeAgoVerbose as formatTimeAgo } from '@/utils/dateFormat'

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

const isActive = computed(() =>
  build.value?.state === 'queued' ||
  build.value?.state === 'building' ||
  build.value?.state === 'exporting'
)


function formatBytes(bytes?: number) {
  if (!bytes) return ''
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`
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
          <div class="flex items-center gap-4 text-sm text-slate-400 flex-wrap">
            <span class="font-mono">{{ build.id }}</span>
            <span>•</span>
            <span>{{ build.templateName }}</span>
            <span>•</span>
            <span>Submitted {{ formatTimeAgo(build.submittedAt) }}</span>
          </div>
        </div>

        <!-- Scripts list -->
        <BaseCard v-if="build.scripts.length > 0" padding="md">
          <p class="text-xs font-mono tracking-widest uppercase text-slate-400 mb-3">Scripts</p>
          <div class="space-y-1">
            <div
              v-for="s in build.scripts"
              :key="s.scriptId"
              class="flex items-center gap-2 text-sm text-slate-300"
            >
              <span class="text-slate-600 font-mono w-5 text-right">{{ s.executionOrder }}.</span>
              <span>{{ s.scriptName }}</span>
            </div>
          </div>
        </BaseCard>

        <!-- Active build progress -->
        <BaseCard v-if="isActive" padding="md">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-4 h-4 rounded-full border-2 border-primary-500 border-t-transparent animate-spin" />
            <span class="text-sm text-slate-300 font-medium">Build in progress…</span>
          </div>
          <p v-if="build.currentWorkerStage" class="text-xs text-slate-500 font-mono">
            {{ build.currentWorkerStage }}
          </p>
          <p v-else class="text-xs text-slate-500">Waiting for worker to pick up the job</p>
        </BaseCard>

        <!-- Ready state -->
        <BaseCard v-if="build.state === 'ready'" padding="lg">
          <div class="text-center mb-6">
            <div class="text-4xl mb-2">✓</div>
            <h2 class="text-2xl font-bold text-slate-100 mb-2">Build Complete</h2>
            <p class="text-slate-400">Your vulnerable VM is ready for download.</p>
          </div>

          <div class="space-y-4">
            <div class="bg-slate-800/60 rounded-lg p-4">
              <div class="flex items-center justify-between mb-1">
                <span class="text-slate-300 font-mono text-sm">
                  {{ build.ovaFilename ?? `vm-${build.id}.ova` }}
                </span>
                <span class="text-sm text-slate-400">{{ formatBytes(build.ovaSizeBytes) }}</span>
              </div>
              <p v-if="build.ovaSha256" class="text-xs text-slate-600 font-mono truncate mb-2">
                SHA256: {{ build.ovaSha256 }}
              </p>
              <BaseButton class="w-full mt-2">Download OVA</BaseButton>
            </div>

            <div class="bg-slate-800/60 rounded-lg p-4">
              <span class="text-slate-300 font-mono text-sm">provenance-{{ build.id }}.json</span>
              <BaseButton variant="secondary" class="w-full mt-2">Download Manifest</BaseButton>
            </div>
          </div>

          <div class="mt-6 pt-6 border-t border-slate-800 flex gap-4">
            <BaseButton variant="primary" class="flex-1">Submit Flag</BaseButton>
          </div>
        </BaseCard>

        <!-- Failed state -->
        <BaseCard v-if="build.state === 'failed'" padding="lg">
          <div class="text-center mb-6">
            <div class="text-4xl mb-2">✗</div>
            <h2 class="text-2xl font-bold text-danger-500 mb-2">Build Failed</h2>
            <p v-if="build.failureReason" class="text-slate-400 font-mono text-sm bg-slate-800/60 p-4 rounded">
              {{ build.failureReason }}
            </p>
          </div>
        </BaseCard>
      </div>

      <div v-else class="text-center py-12">
        <p class="text-slate-400">Build not found</p>
      </div>
    </div>
  </div>
</template>
