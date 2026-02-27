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
  <div
    :class="[
      'bg-slate-900/75 border border-slate-800/80 rounded-xl backdrop-blur-md shadow-[0_18px_45px_rgba(15,23,42,0.85),0_0_0_1px_rgba(15,23,42,0.6)]',
      paddingClasses[padding],
    ]"
  >
    <slot />
  </div>
</template>



