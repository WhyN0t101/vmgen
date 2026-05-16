<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import SessionList from '@/components/admin/SessionList.vue'
import UserManagement from '@/components/admin/UserManagement.vue'
import CategoryListPage from '@/views/admin/categories/CategoryListPage.vue'
import ScriptListPage from '@/views/admin/scripts/ScriptListPage.vue'
import TemplateList from '@/views/admin/templates/TemplateList.vue'
import AdminBuildPanel from '@/views/admin/builds/AdminBuildPanel.vue'
import { api } from '@/api/endpoints'
import type { AdminMetrics } from '@/types'

type AdminTab = 'sessions' | 'users' | 'categories' | 'scripts' | 'templates' | 'builds' | 'metrics'

const activeTab = ref<AdminTab>('sessions')

const metrics = ref<AdminMetrics | null>(null)
const metricsError = ref<string | null>(null)

async function fetchMetrics() {
  try {
    metrics.value = await api.admin.getMetrics()
    metricsError.value = null
  } catch (e) {
    metricsError.value = 'Failed to load metrics'
  }
}

let metricsInterval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  fetchMetrics()
  metricsInterval = setInterval(fetchMetrics, 30_000)
})

onUnmounted(() => {
  if (metricsInterval !== null) clearInterval(metricsInterval)
})

function formatLastSeen(iso: string): string {
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (diff < 60) return `${diff}s ago`
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`
  return `${Math.floor(diff / 3600)}h ago`
}

function formatBytes(bytes: number): string {
  if (bytes < 0) return 'N/A'
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`
}
</script>

<template>
  <div class="max-w-7xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <header class="space-y-2 border-b border-slate-800/70 pb-4">
        <p class="text-[11px] font-mono tracking-[0.3em] uppercase text-slate-500">
          Vmgen // Admin Console
        </p>
        <div class="flex flex-wrap items-baseline justify-between gap-3">
          <div>
            <h1 class="text-3xl font-semibold text-slate-50">Control Surface</h1>
            <p class="text-sm text-slate-400">
              Sessions, access control, templates, and the vulnerability taxonomy.
            </p>
          </div>
        </div>
      </header>

      <!-- Tabs -->
      <nav class="flex flex-wrap gap-2 border-b border-slate-800/80 pb-2">
        <button
          @click="activeTab = 'sessions'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'sessions'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Sessions
        </button>
        <button
          @click="activeTab = 'users'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'users'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          User Management
        </button>
        <button
          @click="activeTab = 'categories'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'categories'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Vulnerability Categories
        </button>
        <button
          @click="activeTab = 'templates'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'templates'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Templates
        </button>
        <button
          @click="activeTab = 'scripts'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'scripts'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Vulnerability Scripts
        </button>
        <button
          @click="activeTab = 'builds'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'builds'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Builds
        </button>
        <button
          @click="activeTab = 'metrics'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'metrics'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Metrics
        </button>
      </nav>

      <!-- Tab Content -->
      <section class="pt-2">
        <SessionList v-if="activeTab === 'sessions'" />
        <UserManagement v-else-if="activeTab === 'users'" />
        <CategoryListPage v-else-if="activeTab === 'categories'" />
        <TemplateList v-else-if="activeTab === 'templates'" />
        <AdminBuildPanel v-else-if="activeTab === 'builds'" />
        <ScriptListPage v-else-if="activeTab === 'scripts'" />

        <!-- Metrics -->
        <div v-else-if="activeTab === 'metrics'" class="space-y-6">
          <p v-if="metricsError" class="text-sm text-red-400">{{ metricsError }}</p>

          <template v-if="metrics">
            <!-- Stat cards -->
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div class="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50 space-y-1">
                <p class="text-[11px] font-mono tracking-widest uppercase text-slate-500">Queue Depth</p>
                <p class="text-3xl font-semibold text-slate-50">{{ metrics.queueDepth }}</p>
              </div>
              <div class="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50 space-y-1">
                <p class="text-[11px] font-mono tracking-widest uppercase text-slate-500">Avg Wait (min)</p>
                <p class="text-3xl font-semibold text-slate-50">{{ metrics.avgWaitMinutes.toFixed(1) }}</p>
              </div>
              <div class="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50 space-y-1">
                <p class="text-[11px] font-mono tracking-widest uppercase text-slate-500">Completions (24h)</p>
                <p class="text-3xl font-semibold text-slate-50">{{ metrics.completionRate24h }}</p>
              </div>
              <div class="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50 space-y-1">
                <p class="text-[11px] font-mono tracking-widest uppercase text-slate-500">Active Workers</p>
                <p class="text-3xl font-semibold text-slate-50">{{ metrics.workers.length }}</p>
              </div>
            </div>

            <!-- Service health -->
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div class="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50 space-y-1">
                <p class="text-[11px] font-mono tracking-widest uppercase text-slate-500">Database</p>
                <p
                  class="text-lg font-semibold font-mono"
                  :class="metrics.dbStatus === 'UP' ? 'text-emerald-400' : 'text-red-400'"
                >{{ metrics.dbStatus }}</p>
              </div>
              <div class="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50 space-y-1">
                <p class="text-[11px] font-mono tracking-widest uppercase text-slate-500">Storage Total</p>
                <p class="text-lg font-semibold text-slate-50">{{ formatBytes(metrics.storageTotalBytes) }}</p>
              </div>
              <div class="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50 space-y-1">
                <p class="text-[11px] font-mono tracking-widest uppercase text-slate-500">Storage Free</p>
                <p
                  class="text-lg font-semibold"
                  :class="metrics.storageUsableBytes >= 0 && metrics.storageTotalBytes > 0 && (metrics.storageUsableBytes / metrics.storageTotalBytes) < 0.1 ? 'text-amber-400' : 'text-slate-50'"
                >{{ formatBytes(metrics.storageUsableBytes) }}</p>
              </div>
            </div>

            <!-- Worker table -->
            <div class="overflow-x-auto rounded-xl border border-slate-700/50">
              <table class="w-full text-sm text-left">
                <thead class="bg-slate-800/80 text-slate-400 text-xs uppercase tracking-wider">
                  <tr>
                    <th class="px-4 py-3">Worker ID</th>
                    <th class="px-4 py-3">CPU %</th>
                    <th class="px-4 py-3">RAM %</th>
                    <th class="px-4 py-3">Active Jobs</th>
                    <th class="px-4 py-3">Last Seen</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-800/60">
                  <tr v-if="metrics.workers.length === 0">
                    <td colspan="5" class="px-4 py-6 text-center text-slate-500 text-xs">No workers have sent a heartbeat since the last backend restart.</td>
                  </tr>
                  <tr
                    v-for="w in metrics.workers"
                    :key="w.workerId"
                    class="bg-slate-900/40 hover:bg-slate-800/40 transition-colors"
                  >
                    <td class="px-4 py-3 font-mono text-slate-300 text-xs">{{ w.workerId }}</td>
                    <td class="px-4 py-3 text-slate-200">{{ w.cpuPercent !== null ? w.cpuPercent.toFixed(1) + '%' : '—' }}</td>
                    <td class="px-4 py-3 text-slate-200">{{ w.memoryPercent !== null ? w.memoryPercent.toFixed(1) + '%' : '—' }}</td>
                    <td class="px-4 py-3 text-slate-200">{{ w.activeJobIds.length }}</td>
                    <td class="px-4 py-3 text-slate-400 text-xs">{{ formatLastSeen(w.lastSeenAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>
