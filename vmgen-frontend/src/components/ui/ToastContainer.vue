<script setup lang="ts">
import { computed } from 'vue'
import { useToast } from '@/composables/useToast'

const { toasts, dismiss } = useToast()

const visibleToasts = computed(() => toasts.value)
</script>

<template>
  <div
    class="fixed inset-x-0 top-4 z-50 flex flex-col items-center space-y-2 px-4 pointer-events-none"
    aria-live="polite"
    aria-atomic="true"
  >
    <transition-group name="toast-fade" tag="div" class="space-y-2 w-full max-w-md">
      <div
        v-for="toast in visibleToasts"
        :key="toast.id"
        class="pointer-events-auto flex items-start gap-3 rounded-lg border px-4 py-3 shadow-lg"
        :class="{
          'bg-emerald-900/90 border-emerald-600 text-emerald-50': toast.type === 'success',
          'bg-red-900/90 border-red-600 text-red-50': toast.type === 'error',
          'bg-slate-900/95 border-slate-600 text-slate-50': toast.type === 'info',
          'bg-amber-900/90 border-amber-600 text-amber-50': toast.type === 'warning',
        }"
        role="status"
      >
        <div class="flex-1 text-sm">
          {{ toast.message }}
        </div>
        <button
          type="button"
          class="ml-2 text-xs opacity-80 hover:opacity-100 focus:outline-none"
          @click="dismiss(toast.id)"
        >
          ✕
        </button>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: all 0.15s ease-out;
}
.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>

