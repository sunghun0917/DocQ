<script setup lang="ts">
defineProps<{
  isOpen: boolean;
}>();

const emit = defineEmits(['close', 'confirm']);
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="fixed inset-0 z-[200] flex items-center justify-center px-4" role="dialog">
        
      <div 
        class="absolute inset-0 bg-[#881337]/30 backdrop-blur-sm animate-fade-in" 
        @click="emit('close')"
      ></div>

      <div class="relative bg-white w-full max-w-[340px] rounded-[18px] p-8 shadow-[0_20px_60px_-15px_rgba(225,29,72,0.3)] 
                  flex flex-col items-center text-center border-4 border-white ring-4 ring-[#ffe4e6] animate-pop-in">
        
        <div class="w-16 h-16 rounded-full bg-[#fff1f2] text-[#e11d48] flex items-center justify-center mb-5 shadow-inner">
          <span class="material-symbols-outlined text-[32px]">logout</span>
        </div>

        <h3 class="text-2xl font-extrabold text-[#881337] mb-2 tracking-tight">
          방 나가기
        </h3>
        <p class="text-[#9f1239]/80 text-sm font-medium leading-relaxed mb-8">
          정말 대기실에서<br>나가시겠습니까?
        </p>

        <div class="flex gap-3 w-full">
          <button 
            @click="emit('close')"
            class="flex-1 h-12 rounded-2xl font-bold text-[#881337] bg-white border-2 border-[#fecdd3] hover:bg-[#fff1f2] transition-colors"
          >
            취소
          </button>
          <button 
            @click="emit('confirm')"
            class="flex-1 h-12 rounded-2xl font-bold text-white bg-[#e11d48] hover:bg-[#be123c] shadow-lg shadow-rose-500/30 transition-transform active:scale-95"
          >
            나가기
          </button>
        </div>

      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* 배경용: 부드럽게 나타나기 */
.animate-fade-in {
  animation: fadeIn 0.2s ease-out forwards;
}

/* 모달용: 통통 튀며 나타나기 (기존 느낌 유지) */
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