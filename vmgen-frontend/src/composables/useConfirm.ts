import { ref } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
}

const isOpen = ref(false)
const options = ref<ConfirmOptions | null>(null)

let resolveFn: ((confirmed: boolean) => void) | null = null

export function useConfirm() {
  function confirm(opts: ConfirmOptions): Promise<boolean> {
    options.value = {
      confirmLabel: 'Confirm',
      cancelLabel: 'Cancel',
      ...opts,
    }
    isOpen.value = true

    return new Promise<boolean>((resolve) => {
      resolveFn = resolve
    })
  }

  function resolve(confirmed: boolean) {
    if (resolveFn) {
      resolveFn(confirmed)
      resolveFn = null
    }
    isOpen.value = false
  }

  return {
    isOpen,
    options,
    confirm,
    resolve,
  }
}

