import { useToastStore } from '@/stores/toast'

export type { ToastType, Toast } from '@/stores/toast'

export function useToast() {
  return useToastStore()
}
