<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useBuilds } from '@/composables/useBuilds'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import { formatTimeAgo, formatTimestamp } from '@/utils/dateFormat'

const router = useRouter()
const { data: buildsPage, isLoading } = useBuilds()
const builds = computed(() => buildsPage.value?.content ?? [])

const stateConfig = {
  queued: { label: 'Queued', color: 'info' },
  building: { label: 'Building', color: 'info' },
  exporting: { label: 'Exporting', color: 'info' },
  ready: { label: 'Ready', color: 'success' },
  failed: { label: 'Failed', color: 'danger' },
  archived: { label: 'Archived', color: 'default' },
} as const
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

      <div v-else-if="builds.length > 0" class="space-y-4">
        <BaseCard
          v-for="build in builds"
          :key="build.id"
          padding="md"
          class="cursor-pointer hover:border-sky-500/50 transition-colors"
          @click="router.push({ name: 'build-status', params: { requestId: build.id } })"
        >
          <div class="flex items-start justify-between gap-4">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-3 mb-3 flex-wrap">
                <span class="font-mono text-xs text-slate-500">{{ build.id }}</span>
                <BaseBadge :variant="stateConfig[build.state].color" size="sm">
                  {{ stateConfig[build.state].label }}
                </BaseBadge>
              </div>

              <div class="space-y-2">
                <div class="text-sm font-medium text-slate-200">
                  {{ build.templateName }}
                </div>

                <div class="flex items-center gap-4 text-sm text-slate-400">
                  <span class="flex items-center gap-1">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
                    </svg>
                    {{ build.scripts.length }} {{ build.scripts.length === 1 ? 'script' : 'scripts' }}
                  </span>
                  <span v-if="build.difficulty" class="capitalize">{{ build.difficulty }}</span>
                  <span class="flex items-center gap-1">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    {{ formatTimeAgo(build.submittedAt) }}
                  </span>
                </div>

                <div class="text-xs text-slate-500 mt-1">
                  Submitted: {{ formatTimestamp(build.submittedAt) }}
                  <span v-if="build.completedAt">
                    • Completed: {{ formatTimestamp(build.completedAt) }}
                  </span>
                  <span v-if="build.failedAt">
                    • Failed: {{ formatTimestamp(build.failedAt) }}
                  </span>
                </div>
              </div>
            </div>
            <BaseButton
              variant="ghost"
              size="sm"
              @click.stop="router.push({ name: 'build-status', params: { requestId: build.id } })"
            >
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
