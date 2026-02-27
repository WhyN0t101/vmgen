<script setup lang="ts">
import BaseButton from '@/components/ui/BaseButton.vue'
import { useConfirm } from '@/composables/useConfirm'

const { isOpen, options, resolve } = useConfirm()

const handleCancel = () => resolve(false)
const handleConfirm = () => resolve(true)
</script>

<template>
  <div
    v-if="isOpen && options"
    class="fixed inset-0 z-40 flex items-center justify-center bg-black/60"
    role="dialog"
    aria-modal="true"
  >
    <div class="w-full max-w-md rounded-lg bg-slate-950/95 border border-slate-700 shadow-xl">
      <div class="border-b border-slate-800 px-5 py-3">
        <h2 class="text-sm font-semibold text-slate-100">
          {{ options.title || 'Confirm action' }}
        </h2>
      </div>
      <div class="px-5 py-4 text-sm text-slate-200">
        {{ options.message }}
      </div>
      <div class="flex justify-end gap-3 border-t border-slate-800 px-5 py-3">
        <BaseButton type="button" variant="ghost" size="sm" @click="handleCancel">
          {{ options.cancelLabel || 'Cancel' }}
        </BaseButton>
        <BaseButton type="button" variant="danger" size="sm" @click="handleConfirm">
          {{ options.confirmLabel || 'Confirm' }}
        </BaseButton>
      </div>
    </div>
  </div>
</template>

