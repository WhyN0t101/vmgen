<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search } from 'lucide-vue-next'
import { useActiveTemplates } from '@/composables/useTemplates'
import BaseCard from '@/components/ui/BaseCard.vue'

const { data: templates, isLoading } = useActiveTemplates()
const searchQuery = ref('')
const osFilter = ref<string>('')

const filteredTemplates = computed(() => {
  if (!templates.value) return []
  
  let filtered = templates.value
  
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    filtered = filtered.filter(t => 
      t.identifier.toLowerCase().includes(query) ||
      t.version.toLowerCase().includes(query)
    )
  }
  
  if (osFilter.value) {
    filtered = filtered.filter(t => t.osFamily === osFilter.value)
  }
  
  return filtered
})
</script>

<template>
  <div class="max-w-6xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <div>
        <p class="text-[11px] font-mono tracking-[0.25em] uppercase text-slate-500 mb-1">
          Module // Templates
        </p>
        <h1 class="text-2xl md:text-3xl font-semibold text-slate-50">Available templates</h1>
      </div>

      <!-- Filter Bar -->
      <BaseCard class="mb-2">
        <div class="flex flex-col md:flex-row gap-4">
          <div class="flex-1 relative">
            <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-slate-400" />
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Search templates..."
              class="w-full pl-10 pr-4 py-2 bg-slate-950/60 border border-slate-800 rounded-lg text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-sky-500/70"
            />
          </div>
          <select
            v-model="osFilter"
            class="px-4 py-2 bg-slate-950/60 border border-slate-800 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-sky-500/70"
          >
            <option value="">All OS</option>
            <option value="linux">Linux</option>
            <option value="windows">Windows</option>
          </select>
        </div>
      </BaseCard>

      <!-- Template Grid -->
      <div v-if="isLoading" class="text-center py-12 text-slate-400">
        Loading templates...
      </div>

      <div v-else-if="filteredTemplates.length === 0" class="text-center py-12 text-slate-400">
        No templates found
      </div>

      <div
        v-else
        class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
      >
        <BaseCard
          v-for="template in filteredTemplates"
          :key="template.id"
          class="cursor-pointer hover:border-sky-500/40 transition-colors"
        >
          <div class="p-6">
            <h3 class="text-xl font-semibold text-slate-100 mb-1">
              {{ template.identifier }}
            </h3>
            <p class="text-sm text-slate-400 mb-2">{{ template.version }}</p>
            <p class="text-xs text-slate-500 capitalize">{{ template.osFamily }}</p>
          </div>
        </BaseCard>
      </div>
    </div>
  </div>
</template>
