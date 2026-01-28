import { defineStore } from 'pinia'
import { ref } from 'vue'

type ConfirmVariant = 'danger' | 'primary'

export const useUiStore = defineStore('ui', () => {
  const isConfirmOpen = ref(false)
  const confirmTitle = ref('')
  const confirmMessage = ref('')
  const confirmLabel = ref('')
  const confirmIcon = ref<string | undefined>(undefined)
  const confirmVariant = ref<ConfirmVariant>('danger')
  const confirmShowCancel = ref(true)
  const confirmCancelLabel = ref<string | undefined>(undefined)
  const confirmAction = ref<(() => void) | null>(null)

  const openConfirm = (payload: {
    title: string
    message: string
    confirmLabel: string
    icon?: string
    variant?: ConfirmVariant
    showCancel?: boolean
    cancelLabel?: string
    onConfirm?: () => void
  }) => {
    confirmTitle.value = payload.title
    confirmMessage.value = payload.message
    confirmLabel.value = payload.confirmLabel
    confirmIcon.value = payload.icon
    confirmVariant.value = payload.variant ?? 'danger'
    confirmShowCancel.value = payload.showCancel !== false
    confirmCancelLabel.value = payload.cancelLabel
    confirmAction.value = payload.onConfirm ?? null
    isConfirmOpen.value = true
  }

  const closeConfirm = () => {
    isConfirmOpen.value = false
    confirmAction.value = null
  }

  const confirm = () => {
    const action = confirmAction.value
    closeConfirm()
    action?.()
  }

  return {
    isConfirmOpen,
    confirmTitle,
    confirmMessage,
    confirmLabel,
    confirmIcon,
    confirmVariant,
    confirmShowCancel,
    confirmCancelLabel,
    openConfirm,
    closeConfirm,
    confirm
  }
})
