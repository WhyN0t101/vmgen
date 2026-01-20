<script setup lang="ts">
interface Props {
  padding?: 'none' | 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  padding: 'md',
})

const paddingClasses = {
  none: '',
  sm: 'p-4',
  md: 'p-6',
  lg: 'p-8',
}
</script>

<template>
  <div :class="['bg-slate-900/50 backdrop-blur-sm border border-slate-800 rounded-lg shadow-lg shadow-slate-900/50', paddingClasses[padding]]">
    <slot />
  </div>
</template>



