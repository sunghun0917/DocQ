<script setup lang="ts">
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { useUiStore } from '@/stores/uiStore'

const uiStore = useUiStore()
</script>

<template>
  <ConfirmModal
    :isOpen="uiStore.isConfirmOpen"
    :title="uiStore.confirmTitle"
    :message="uiStore.confirmMessage"
    :confirmLabel="uiStore.confirmLabel"
    :icon="uiStore.confirmIcon"
    :variant="uiStore.confirmVariant"
    :showCancel="uiStore.confirmShowCancel"
    :cancelLabel="uiStore.confirmCancelLabel"
    @close="uiStore.closeConfirm"
    @confirm="uiStore.confirm"
  />
</template>
