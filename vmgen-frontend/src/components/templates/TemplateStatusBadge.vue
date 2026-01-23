<script setup lang="ts">
import { computed } from 'vue'
import type { TemplateStatus } from '@/types'

interface Props {
  status: TemplateStatus
}

const props = defineProps<Props>()

const badgeClasses = computed(() => {
  const base = 'px-2 py-1 text-xs font-medium rounded-full'
  
  const variants: Record<TemplateStatus, string> = {
    draft: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    validating: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    active: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    failed: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    deprecated: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300',
  }
  
  return `${base} ${variants[props.status]}`
})
</script>

<template>
  <span :class="badgeClasses">
    {{ status.charAt(0).toUpperCase() + status.slice(1) }}
  </span>
</template>
