<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search } from 'lucide-vue-next'
import { useActiveTemplates } from '@/composables/useTemplates'
import BaseCard from '@/components/ui/BaseCard.vue'
import OsIcon from '@/components/ui/OsIcon.vue'

const searchQuery = ref('')
const debouncedSearch = ref('')
const osFilter = ref<string>('')
const currentPage = ref(0)

// Debounce search input: waits 300 ms after the user stops typing before re-fetching.
watch(searchQuery, (val, _old, onCleanup) => {
  const timer = setTimeout(() => { debouncedSearch.value = val }, 300)
  onCleanup(() => clearTimeout(timer))
})

// Reset to page 0 whenever the filters change.
watch([debouncedSearch, osFilter], () => { currentPage.value = 0 })

const { data: templates, isLoading } = useActiveTemplates({
  search: debouncedSearch,
  osFamily: osFilter,
  page: currentPage,
})

// Derive a readable display name from the identifier (e.g. "ubuntu-22-04" → "Ubuntu 22.04")
function formatIdentifier(id: string): string {
  return id.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}
</script>

<template>
  <div class="max-w-6xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <div>
        <p class="text-[11px] font-mono tracking-[0.25em] uppercase text-slate-500 mb-1">
          Module // Templates
        </p>
        <h1 class="text-2xl md:text-3xl font-semibold text-slate-50">Available templates</h1>
        <p class="text-sm text-slate-400 mt-1">Base VM images you can build vulnerable environments from.</p>
      </div>

      <!-- Filter Bar -->
      <div class="flex flex-col md:flex-row gap-3">
        <div class="flex-1 relative">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search by name or description…"
            class="w-full pl-9 pr-4 py-2 bg-slate-950/60 border border-slate-800 rounded-lg text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-sky-500/70"
          />
        </div>
        <select
          v-model="osFilter"
          class="px-4 py-2 bg-slate-950/60 border border-slate-800 rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-sky-500/70"
        >
          <option value="">All OS families</option>
          <option value="linux">Linux</option>
          <option value="ubuntu">Ubuntu</option>
          <option value="debian">Debian</option>
          <option value="kali-linux">Kali Linux</option>
          <option value="fedora">Fedora</option>
          <option value="centos">CentOS</option>
          <option value="windows">Windows</option>
        </select>
      </div>

      <!-- Loading skeleton -->
      <div v-if="isLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div v-for="i in 6" :key="i" class="h-32 bg-slate-900/60 rounded-xl animate-pulse" />
      </div>

      <!-- Empty -->
      <div v-else-if="!templates?.content?.length" class="text-center py-16 text-slate-500">
        No templates found
      </div>

      <!-- Grid -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <BaseCard
          v-for="template in templates.content"
          :key="template.id"
          padding="md"
          class="hover:border-sky-500/40 transition-colors"
        >
          <div class="flex items-start gap-3">
            <OsIcon :os-family="template.osFamily" :size="32" class="mt-0.5 shrink-0" />
            <div class="flex-1 min-w-0">
              <div class="flex items-start justify-between gap-2 mb-0.5">
                <h3 class="font-semibold text-slate-100 leading-snug">
                  {{ formatIdentifier(template.identifier) }}
                </h3>
                <span class="text-[10px] font-mono text-slate-500 bg-slate-800 px-1.5 py-0.5 rounded shrink-0">
                  {{ template.version }}
                </span>
              </div>
              <p class="text-xs text-slate-500 mb-2 capitalize">{{ template.osFamily }}</p>
              <p v-if="template.description" class="text-xs text-slate-400 leading-relaxed line-clamp-2">
                {{ template.description }}
              </p>
              <p v-else class="text-xs text-slate-600 italic">No description provided.</p>
            </div>
          </div>
        </BaseCard>
      </div>

      <!-- Pagination controls -->
      <div
        v-if="!isLoading && templates && templates.totalPages > 1"
        class="flex items-center justify-center gap-4"
      >
        <button
          :disabled="currentPage === 0"
          @click="currentPage--"
          class="px-3 py-1.5 text-sm bg-slate-800 text-slate-300 rounded-lg hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >← Prev</button>
        <span class="text-xs text-slate-500">
          Page {{ currentPage + 1 }} of {{ templates.totalPages }}
        </span>
        <button
          :disabled="templates.last"
          @click="currentPage++"
          class="px-3 py-1.5 text-sm bg-slate-800 text-slate-300 rounded-lg hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >Next →</button>
      </div>

      <!-- Count footer -->
      <p v-if="!isLoading && templates && templates.totalElements > 0" class="text-xs text-slate-600 text-right">
        {{ templates.totalElements }} template{{ templates.totalElements !== 1 ? 's' : '' }} available
      </p>
    </div>
  </div>
</template>
