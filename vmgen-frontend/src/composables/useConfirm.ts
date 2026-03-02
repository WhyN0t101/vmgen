import { useConfirmStore } from '@/stores/confirm'

export type { ConfirmOptions } from '@/stores/confirm'

export function useConfirm() {
  return useConfirmStore()
}
