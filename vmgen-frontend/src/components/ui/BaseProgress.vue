<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  value: number
  max?: number
  showLabel?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  max: 100,
  showLabel: true,
})

const percentage = computed(() => Math.min((props.value / props.max) * 100, 100))
</script>

<template>
  <div class="w-full">
    <div class="flex items-center justify-between mb-2" v-if="showLabel">
      <span class="text-sm text-slate-400">{{ Math.round(percentage) }}%</span>
    </div>
    <div class="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
      <div
        class="h-full bg-primary-500 transition-all duration-300 ease-out"
        :style="{ width: `${percentage}%` }"
      />
    </div>
  </div>
</template>

