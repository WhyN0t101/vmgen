<script setup lang="ts">
import type { TemplateListResponse } from '@/types'
import BaseCard from '@/components/ui/BaseCard.vue'
import OsIcon from '@/components/ui/OsIcon.vue'

interface Props {
  template: TemplateListResponse
  selected?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  selected: false,
})

const emit = defineEmits<{
  select: [template: TemplateListResponse]
}>()
</script>

<template>
  <BaseCard
    :class="[
      'cursor-pointer transition-all hover:border-primary-500/50 hover:shadow-primary-500/10',
      selected ? 'border-primary-500 bg-primary-500/10' : ''
    ]"
    padding="md"
    @click="emit('select', template)"
  >
    <div class="flex items-start gap-3">
      <OsIcon :os-family="template.osFamily" :size="28" />
      <div class="flex-1 min-w-0">
        <h3 class="font-semibold text-slate-100 mb-1">{{ template.identifier }}</h3>
        <div class="flex items-center gap-2 text-sm text-slate-400 mb-2">
          <span>{{ template.version }}</span>
          <span>•</span>
          <span class="capitalize">{{ template.osFamily }}</span>
        </div>
        <p v-if="template.description" class="text-xs text-slate-500 mt-2">
          {{ template.description }}
        </p>
      </div>
    </div>
  </BaseCard>
</template>
