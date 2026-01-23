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
  <div class="container mx-auto px-6 py-8">
    <h1 class="text-3xl font-bold text-slate-100 mb-6">Available Templates</h1>

    <!-- Filter Bar -->
    <BaseCard class="mb-6">
      <div class="flex flex-col md:flex-row gap-4">
        <div class="flex-1 relative">
          <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-slate-400" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search templates..."
            class="w-full pl-10 pr-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <select
          v-model="osFilter"
          class="px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
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
        class="hover:shadow-lg transition-shadow cursor-pointer"
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
</template>
