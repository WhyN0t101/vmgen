<script setup lang="ts">
import { Server, Globe, Database, Container } from 'lucide-vue-next'
import { computed } from 'vue'

interface Props {
  capability: string
}

const props = defineProps<Props>()

const icon = computed(() => {
  const iconMap: Record<string, any> = {
    ssh: Server,
    web_server: Globe,
    database: Database,
    docker: Container,
  }
  return iconMap[props.capability.toLowerCase()] || Server
})
</script>

<template>
  <span class="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300 rounded-md">
    <component :is="icon" class="w-3.5 h-3.5" />
    {{ capability }}
  </span>
</template>
