<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  isOpen: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  icon?: string;
  variant?: 'danger' | 'primary';
  showCancel?: boolean;
  cancelLabel?: string;
}>();

const emit = defineEmits(['close', 'confirm']);

const buttonClass = computed(() => {
  return props.variant === 'primary'
    ? 'bg-emerald-500 hover:bg-emerald-600 shadow-emerald-500/30'
    : 'bg-[#e11d48] hover:bg-[#be123c] shadow-rose-500/30';
});

const iconBgClass = computed(() => {
  return props.variant === 'primary' ? 'text-emerald-500' : 'text-[#e11d48]';
});

const showCancelButton = computed(() => props.showCancel !== false);
const cancelText = computed(() => props.cancelLabel || '취소');
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="fixed inset-0 z-[9999] flex items-center justify-center px-4" role="dialog">
      <div
        class="absolute inset-0 bg-[#0f172a]/35 backdrop-blur-sm animate-fade-in"
        @click="emit('close')"
      ></div>

      <div
        class="relative bg-white w-full max-w-[360px] rounded-[18px] p-8 shadow-[0_20px_60px_-15px_rgba(15,23,42,0.3)]
               flex flex-col items-center text-center border-4 border-white ring-4 ring-[#ffe4e6] animate-pop-in"
      >
        <div class="w-16 h-16 rounded-full bg-[#fff1f2] flex items-center justify-center mb-5 shadow-inner">
          <span class="material-symbols-outlined text-[32px]" :class="iconBgClass">
            {{ icon || 'help' }}
          </span>
        </div>

        <h3 class="text-2xl font-extrabold text-[#881337] mb-2 tracking-tight">
          {{ title }}
        </h3>
        <p class="text-[#9f1239]/80 text-sm font-medium leading-relaxed mb-8">
          {{ message }}
        </p>

        <div class="flex gap-3 w-full">
          <button
            v-if="showCancelButton"
            @click="emit('close')"
            class="flex-1 h-12 rounded-2xl font-bold text-[#881337] bg-white border-2 border-[#fecdd3] hover:bg-[#fff1f2] transition-colors"
          >
            {{ cancelText }}
          </button>
          <button
            @click="emit('confirm')"
            class="h-12 rounded-2xl font-bold text-white shadow-lg transition-transform active:scale-95"
            :class="[buttonClass, showCancelButton ? 'flex-1' : 'w-full']"
          >
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.2s ease-out forwards;
}

.animate-pop-in {
  animation: popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes popIn {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>
