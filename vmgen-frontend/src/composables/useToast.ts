import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface Toast {
  id: number
  message: string
  type: ToastType
}

const toasts = ref<Toast[]>([])
let nextId = 1

export function useToast() {
  function show(message: string, type: ToastType = 'info', durationMs = 5000) {
    const id = nextId++
    toasts.value.push({ id, message, type })

    if (durationMs > 0) {
      window.setTimeout(() => dismiss(id), durationMs)
    }
  }

  function dismiss(id: number) {
    const index = toasts.value.findIndex(t => t.id === id)
    if (index !== -1) {
      toasts.value.splice(index, 1)
    }
  }

  return {
    toasts,
    show,
    dismiss,
  }
}

