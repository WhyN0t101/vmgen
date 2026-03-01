<script setup lang="ts">
import { computed } from 'vue'
import type { TemplateListResponse } from '@/types'
import TemplateCard from './TemplateCard.vue'

interface Props {
  templates?: TemplateListResponse[]
  loading?: boolean
  modelValue?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: string | undefined]
}>()

const selectedId = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

function handleSelect(template: TemplateListResponse) {
  selectedId.value = template.id
}
</script>

<template>
  <div>
    <h2 class="text-lg font-semibold text-slate-100 mb-4">Choose Base Template</h2>

    <div v-if="loading" class="space-y-3">
      <div v-for="i in 3" :key="i" class="h-32 bg-slate-800/50 rounded-lg animate-pulse" />
    </div>

    <div v-else-if="templates && templates.length > 0" class="space-y-3">
      <TemplateCard
        v-for="template in templates"
        :key="template.id"
        :template="template"
        :selected="selectedId === template.id"
        @select="handleSelect"
      />
    </div>

    <div v-else class="text-center py-8 text-slate-500">
      No templates available
    </div>
  </div>
</template>
