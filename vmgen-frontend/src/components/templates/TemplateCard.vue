<script setup lang="ts">
import type { Template } from '@/types'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import OsIcon from '@/components/ui/OsIcon.vue'

interface Props {
  template: Template
  selected?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  selected: false,
})

const emit = defineEmits<{
  select: [template: Template]
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
      <OsIcon :os-family="template.os_family" :size="28" />
      <div class="flex-1 min-w-0">
        <h3 class="font-semibold text-slate-100 mb-1">{{ template.name }}</h3>
        <div class="flex items-center gap-2 text-sm text-slate-400 mb-2">
          <span class="font-mono">{{ template.arch }}</span>
          <span>•</span>
          <span>{{ template.version }}</span>
        </div>
        <div class="flex flex-wrap gap-1.5 mb-2">
          <BaseBadge
            v-for="cap in template.capabilities.slice(0, 3)"
            :key="cap"
            variant="info"
            size="sm"
          >
            {{ cap.replace('_', ' ') }}
          </BaseBadge>
        </div>
        <p v-if="template.description" class="text-xs text-slate-500 mt-2">
          {{ template.description }}
        </p>
      </div>
    </div>
  </BaseCard>
</template>



