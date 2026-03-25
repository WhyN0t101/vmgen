<script setup lang="ts">
import { ref, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { api } from '@/api/endpoints'
import type { AdminBuildSummary, AdminBuildCredentials } from '@/types'
import { formatTimeAgo } from '@/utils/dateFormat'

const page = ref(0)
const pageSize = 20

const ACTIVE_STATES = new Set(['queued', 'building', 'exporting'])

const { data, isLoading, isError, refetch } = useQuery({
  queryKey: computed(() => ['admin', 'builds', page.value]),
  queryFn: () => api.admin.getAllBuilds(page.value, pageSize),
  refetchInterval: (query) => {
    const items = query.state.data?.content ?? []
    return items.some((b: AdminBuildSummary) => ACTIVE_STATES.has(b.state)) ? 5000 : false
  },
})

const builds = computed(() => data.value?.content ?? [])
const totalPages = computed(() => data.value?.totalPages ?? 0)
const totalElements = computed(() => data.value?.totalElements ?? 0)

// Credentials modal
const credentialsLoading = ref<string | null>(null)
const credentialsError = ref<string | null>(null)
const selectedCredentials = ref<AdminBuildCredentials | null>(null)
const credentialsBuildId = ref<string | null>(null)

async function viewCredentials(build: AdminBuildSummary) {
  credentialsLoading.value = build.id
  credentialsError.value = null
  selectedCredentials.value = null
  credentialsBuildId.value = build.id
  try {
    selectedCredentials.value = await api.admin.getBuildCredentials(build.id)
  } catch (e: any) {
    credentialsError.value = e?.message ?? 'Failed to load credentials'
  } finally {
    credentialsLoading.value = null
  }
}

function closeCredentials() {
  selectedCredentials.value = null
  credentialsBuildId.value = null
  credentialsError.value = null
}

function stateBadgeClass(state: string) {
  switch (state) {
    case 'ready':     return 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40'
    case 'building':  return 'bg-sky-500/15 text-sky-300 border-sky-500/40'
    case 'exporting': return 'bg-violet-500/15 text-violet-300 border-violet-500/40'
    case 'queued':    return 'bg-slate-500/15 text-slate-300 border-slate-500/40'
    case 'failed':    return 'bg-red-500/15 text-red-300 border-red-500/40'
    case 'archived':  return 'bg-amber-500/15 text-amber-300 border-amber-500/40'
    default:          return 'bg-slate-500/15 text-slate-400 border-slate-500/40'
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-slate-100">All Builds</h2>
        <p class="text-xs text-slate-500 mt-0.5">
          {{ totalElements }} total across all users
        </p>
      </div>
      <button
        @click="refetch()"
        class="px-3 py-1.5 text-xs font-medium text-slate-400 hover:text-slate-100 border border-slate-700 hover:border-slate-500 rounded-lg transition-colors"
      >
        Refresh
      </button>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="flex justify-center py-12">
      <div class="w-6 h-6 border-2 border-sky-500 border-t-transparent rounded-full animate-spin" />
    </div>

    <!-- Error -->
    <div v-else-if="isError" class="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">
      Failed to load builds.
    </div>

    <!-- Table -->
    <div v-else class="overflow-x-auto rounded-xl border border-slate-800">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-slate-800 text-left">
            <th class="px-4 py-3 text-xs font-medium text-slate-500 uppercase tracking-wider">State</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500 uppercase tracking-wider">User</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500 uppercase tracking-wider">Template</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500 uppercase tracking-wider">Difficulty</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500 uppercase tracking-wider">Submitted</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500 uppercase tracking-wider">Completed</th>
            <th class="px-4 py-3 text-xs font-medium text-slate-500 uppercase tracking-wider"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-800/60">
          <template v-if="builds.length === 0">
            <tr>
              <td colspan="7" class="px-4 py-10 text-center text-sm text-slate-500">No builds found.</td>
            </tr>
          </template>
          <tr
            v-for="build in builds"
            :key="build.id"
            class="hover:bg-slate-800/30 transition-colors"
          >
            <td class="px-4 py-3">
              <span
                :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border', stateBadgeClass(build.state)]"
              >
                {{ build.state }}
              </span>
            </td>
            <td class="px-4 py-3 font-mono text-xs text-slate-300">{{ build.username }}</td>
            <td class="px-4 py-3 text-slate-300 text-xs">{{ build.templateName }}</td>
            <td class="px-4 py-3 text-slate-400 text-xs capitalize">{{ build.difficulty ?? '—' }}</td>
            <td class="px-4 py-3 text-slate-400 text-xs">{{ formatTimeAgo(build.submittedAt) }}</td>
            <td class="px-4 py-3 text-slate-400 text-xs">
              <span v-if="build.completedAt">{{ formatTimeAgo(build.completedAt) }}</span>
              <span v-else-if="build.failedAt" class="text-red-400">{{ formatTimeAgo(build.failedAt) }}</span>
              <span v-else class="text-slate-600">—</span>
            </td>
            <td class="px-4 py-3">
              <button
                v-if="build.state === 'ready'"
                @click="viewCredentials(build)"
                :disabled="credentialsLoading === build.id"
                class="px-3 py-1 text-xs font-medium text-sky-400 hover:text-sky-200 border border-sky-500/30 hover:border-sky-400/60 rounded-lg transition-colors disabled:opacity-50"
              >
                <span v-if="credentialsLoading === build.id">Loading...</span>
                <span v-else>Credentials</span>
              </button>
              <span v-else-if="build.failureReason" class="text-xs text-red-400/70 max-w-[200px] truncate block" :title="build.failureReason">
                {{ build.failureReason }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div v-if="totalPages > 1" class="flex items-center justify-between pt-1">
      <button
        @click="page--"
        :disabled="page === 0"
        class="px-3 py-1.5 text-xs font-medium text-slate-400 hover:text-slate-100 border border-slate-700 hover:border-slate-500 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
      >
        Previous
      </button>
      <span class="text-xs text-slate-500">Page {{ page + 1 }} of {{ totalPages }}</span>
      <button
        @click="page++"
        :disabled="page + 1 >= totalPages"
        class="px-3 py-1.5 text-xs font-medium text-slate-400 hover:text-slate-100 border border-slate-700 hover:border-slate-500 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
      >
        Next
      </button>
    </div>

    <!-- Credentials Modal -->
    <Teleport to="body">
      <div
        v-if="credentialsBuildId && (selectedCredentials || credentialsError)"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
        @click.self="closeCredentials"
      >
        <div class="bg-slate-900 border border-slate-700 rounded-2xl p-6 w-full max-w-sm space-y-4 shadow-2xl">
          <div class="flex items-center justify-between">
            <h3 class="text-base font-semibold text-slate-100">VM Login Credentials</h3>
            <button @click="closeCredentials" class="text-slate-500 hover:text-slate-200 transition-colors text-lg leading-none">&times;</button>
          </div>

          <div v-if="credentialsError" class="rounded-lg bg-red-500/10 border border-red-500/30 p-3 text-sm text-red-300">
            {{ credentialsError }}
          </div>

          <template v-else-if="selectedCredentials">
            <p class="text-xs text-slate-500 font-mono truncate">Build {{ selectedCredentials.buildId }}</p>
            <div class="space-y-3">
              <div>
                <p class="text-xs text-slate-500 mb-1">Username</p>
                <p class="font-mono text-sm bg-slate-800 rounded-lg px-3 py-2 text-slate-200 select-all">
                  {{ selectedCredentials.sshUsername }}
                </p>
              </div>
              <div>
                <p class="text-xs text-slate-500 mb-1">Password</p>
                <p class="font-mono text-sm bg-slate-800 rounded-lg px-3 py-2 text-sky-300 select-all tracking-wider">
                  {{ selectedCredentials.vmLoginPassword }}
                </p>
              </div>
            </div>
            <p class="text-xs text-slate-600">
              This password is deterministic — derived from the build seed.
            </p>
          </template>

          <button
            @click="closeCredentials"
            class="w-full py-2 text-sm font-medium text-slate-400 hover:text-slate-100 border border-slate-700 hover:border-slate-500 rounded-xl transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
