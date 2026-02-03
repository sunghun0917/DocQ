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

const confirmBtnClass = computed(() => {
  return props.variant === 'primary' 
    ? 'cartoon-btn primary' 
    : 'cartoon-btn danger';
});

const iconClass = computed(() => {
  return props.variant === 'primary' ? 'text-[#7cb342]' : 'text-[#ef5350]';
});

const showCancelButton = computed(() => props.showCancel !== false);
const cancelText = computed(() => props.cancelLabel || '취소');
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="fixed inset-0 z-[9999] flex items-center justify-center px-4 font-jua" role="dialog">
      <div
        class="absolute inset-0 bg-black/40 backdrop-blur-sm animate-fade-in"
        @click="emit('close')"
      ></div>

      <div
        class="relative bg-[#fff8e1] w-full max-w-[400px] rounded-[24px] !p-4 shadow-[0_12px_35px_rgba(0,0,0,0.25)]
               flex flex-col items-center text-center border-[6px] border-[#8d6e63] animate-pop-in"
      >
        <!-- 장식용 나사 -->
        <div class="absolute top-4 left-4 w-3 h-3 rounded-full bg-[#bcaaa4] border border-[#5d4037]"></div>
        <div class="absolute top-4 right-4 w-3 h-3 rounded-full bg-[#bcaaa4] border border-[#5d4037]"></div>
        <div class="absolute bottom-4 left-4 w-3 h-3 rounded-full bg-[#bcaaa4] border border-[#5d4037]"></div>
        <div class="absolute bottom-4 right-4 w-3 h-3 rounded-full bg-[#bcaaa4] border border-[#5d4037]"></div>

        <div class="w-20 h-20 rounded-full bg-white border-4 border-[#d7ccc8] flex items-center justify-center mb-5 shadow-sm">
          <span class="material-symbols-outlined text-[40px]" :class="iconClass">
            {{ icon || 'help' }}
          </span>
        </div>

        <h3 class="text-3xl text-[#5d4037] mb-3 tracking-wide drop-shadow-sm jua-font">
          {{ title }}
        </h3>
        <p class="text-[#8d6e63] text-lg font-medium leading-relaxed mb-8 break-keep px-2">
          {{ message }}
        </p>

        <div class="flex gap-4 w-full">
          <button
            v-if="showCancelButton"
            @click="emit('close')"
            class="cartoon-btn secondary flex-1"
          >
            {{ cancelText }}
          </button>
          <button
            @click="emit('confirm')"
            class="flex-1"
            :class="confirmBtnClass"
          >
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-jua { font-family: 'Jua', sans-serif; }
.jua-font { font-family: 'Jua', sans-serif; }

/* 카툰 버튼 스타일 */
.cartoon-btn {
  height: 54px;
  border-radius: 16px;
  font-size: 1.35rem;
  color: white;
  font-family: 'Jua', sans-serif;
  transition: all 0.1s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  top: 0;
  border: none;
  cursor: pointer;
  box-shadow: 0 5px 0 rgba(0,0,0,0.2);
}

.cartoon-btn:active { top: 5px; box-shadow: 0 0 0 rgba(0,0,0,0.2); }

/* Primary (Green) */
.cartoon-btn.primary {
  background-color: #7cb342;
  box-shadow: 0 5px 0 #558b2f;
}
.cartoon-btn.primary:hover { background-color: #8bc34a; }
.cartoon-btn.primary:active { box-shadow: 0 0 0 #558b2f; }

/* Danger (Red) */
.cartoon-btn.danger {
  background-color: #ef5350;
  box-shadow: 0 5px 0 #c62828;
}
.cartoon-btn.danger:hover { background-color: #ff7043; } /* Hover시 살짝 오렌지빛 */
.cartoon-btn.danger:active { box-shadow: 0 0 0 #c62828; }

/* Secondary (Cancel/Gray) */
.cartoon-btn.secondary {
  background-color: #ffe0b2; 
  color: #5d4037;
  box-shadow: 0 5px 0 #ffcc80;
}
.cartoon-btn.secondary:hover { background-color: #ffcc80; }
.cartoon-btn.secondary:active { box-shadow: 0 0 0 #ffcc80; }

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
    transform: scale(0.9) translateY(10px) rotate(-2deg);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0) rotate(0deg);
  }
}
</style>
