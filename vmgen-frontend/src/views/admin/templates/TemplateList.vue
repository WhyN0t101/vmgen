<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search } from 'lucide-vue-next'
import { useTemplateList } from '@/composables/useTemplates'
import TemplateStatusBadge from '@/components/templates/TemplateStatusBadge.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'

const router = useRouter()
const statusFilter = ref<string>('')
const searchQuery = ref('')

const { data: templates, isLoading } = useTemplateList(statusFilter)

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
  
  return filtered
})

function formatDate(dateString: string) {
  return new Date(dateString).toLocaleDateString()
}

function goToDetail(id: string) {
  router.push({ name: 'admin-template-detail', params: { id } })
}
</script>

<template>
  <div class="container mx-auto px-6 py-8">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-3xl font-bold text-slate-100">Templates</h1>
      <BaseButton @click="router.push({ name: 'admin-template-register' })">
        <Plus class="w-4 h-4 mr-2" />
        Register Template
      </BaseButton>
    </div>

    <!-- Filter Bar -->
    <BaseCard class="mb-6">
      <div class="flex flex-col md:flex-row gap-4">
        <div class="flex-1 relative">
          <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-slate-400" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search by identifier or version..."
            class="w-full pl-10 pr-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <select
          v-model="statusFilter"
          class="px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">All Statuses</option>
          <option value="draft">Draft</option>
          <option value="active">Active</option>
          <option value="validating">Validating</option>
          <option value="failed">Failed</option>
          <option value="deprecated">Deprecated</option>
        </select>
      </div>
    </BaseCard>

    <!-- Table -->
    <BaseCard>
      <div v-if="isLoading" class="text-center py-12 text-slate-400">
        Loading templates...
      </div>
      
      <div v-else-if="filteredTemplates.length === 0" class="text-center py-12 text-slate-400">
        No templates found
      </div>
      
      <table v-else class="w-full">
        <thead>
          <tr class="border-b border-slate-700">
            <th class="text-left py-3 px-4 text-slate-300 font-medium">Identifier</th>
            <th class="text-left py-3 px-4 text-slate-300 font-medium">Version</th>
            <th class="text-left py-3 px-4 text-slate-300 font-medium">OS Family</th>
            <th class="text-left py-3 px-4 text-slate-300 font-medium">Status</th>
            <th class="text-left py-3 px-4 text-slate-300 font-medium">Created At</th>
            <th class="text-right py-3 px-4 text-slate-300 font-medium">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="template in filteredTemplates"
            :key="template.id"
            class="border-b border-slate-800 hover:bg-slate-800/50 cursor-pointer transition-colors"
            @click="goToDetail(template.id)"
          >
            <td class="py-3 px-4 text-slate-100 font-medium">{{ template.identifier }}</td>
            <td class="py-3 px-4 text-slate-300">{{ template.version }}</td>
            <td class="py-3 px-4 text-slate-300 capitalize">{{ template.osFamily }}</td>
            <td class="py-3 px-4">
              <TemplateStatusBadge :status="template.status" />
            </td>
            <td class="py-3 px-4 text-slate-400">{{ formatDate(template.createdAt) }}</td>
            <td class="py-3 px-4 text-right">
              <BaseButton
                variant="ghost"
                size="sm"
                @click.stop="goToDetail(template.id)"
              >
                View
              </BaseButton>
            </td>
          </tr>
        </tbody>
      </table>
    </BaseCard>
  </div>
</template>
