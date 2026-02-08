<script setup lang="ts">
import { watch } from 'vue';

const props = defineProps<{
  isOpen: boolean;
  message: string;
  type?: 'error' | 'success';
}>();

const emit = defineEmits(['close']);

// 3초 뒤 자동 닫힘 로직 (기존과 동일)
watch(() => props.isOpen, (newVal) => {
  if (newVal) {
    setTimeout(() => {
      emit('close');
    }, 3000);
  }
});
</script>

<template>
  <Teleport to="body">
    <Transition name="slide-down">
      <div 
        v-if="isOpen" 
        class="fixed top-0 left-0 w-full z-[9999] flex justify-center items-center gap-2 
               py-4 shadow-xl"
        :class="type === 'success' ? 'bg-[#20c997]' : 'bg-[#e11d48]'"
        role="alert"
      >
        <span class="material-symbols-outlined text-[24px] text-white">
          {{ type === 'success' ? 'check_circle' : 'warning' }}
        </span>

        <span class="text-white font-bold text-base tracking-wide">
          {{ message }}
        </span>
        
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 천장에서 슬라이드되어 내려오는 애니메이션 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-100%); /* 화면 밖(위쪽)에 숨어있음 */
}

.slide-down-enter-to,
.slide-down-leave-from {
  transform: translateY(0); /* 제자리로 내려옴 */
}
</style>