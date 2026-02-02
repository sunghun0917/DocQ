<script setup lang="ts">
defineProps<{
  isOpen: boolean;
}>();

const emit = defineEmits(['close', 'confirm']);
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="fixed inset-0 z-[9999] flex items-center justify-center px-4 font-display" role="dialog">
      
      <div 
        class="absolute inset-0 bg-black/40 backdrop-blur-sm animate-fade-in" 
        @click="emit('close')"
      ></div>

      <div class="relative bg-[#fff8e1] w-full max-w-[400px] min-h-[200px] rounded-[32px] p-10
                  flex flex-col items-center text-center 
                  border-[6px] border-[#8d6e63] shadow-[0_12px_35px_rgba(0,0,0,0.25)] 
                  animate-pop-in">
        
        <div class="absolute top-4 left-4 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="absolute top-4 right-4 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="absolute bottom-4 left-4 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="absolute bottom-4 right-4 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="h-4 w-full invisible"></div>
        <h3 class="text-4xl font-display text-[#5d4037] mb-4 mt-2 drop-shadow-sm tracking-wide">
          방 나가기
        </h3>
        
        <p class="text-[#8d6e63] text-xl font-medium leading-relaxed mb-8 font-sans-cartoon break-keep">
          정말로 대기실에서<br>나가시겠습니까?
        </p>

        <div class="flex gap-3 w-full max-w-[260px] mx-auto">
          <button 
            @click="emit('close')"
            class="cartoon-btn secondary"
          >
            취소
          </button>
          <button 
            @click="emit('confirm')"
            class="cartoon-btn primary"
          >
            나가기
          </button>
        </div>

      </div>
    </div>
  </Teleport>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-display, .font-sans-cartoon, .cartoon-btn {
  font-family: 'Jua', sans-serif;
}

/* 카툰 버튼 공통 스타일 */
.cartoon-btn {
  flex: 1;
  height: 52px; /* 높이 적당히 조정 */
  border-radius: 16px;
  font-size: 1.3rem;
  color: white;
  transition: all 0.1s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  top: 0;
  border: none;
  cursor: pointer;
}

/* 취소 버튼 (회색 변경) */
.cartoon-btn.secondary {
  background-color: #9ca3af; /* Tailwind Gray-400 */
  box-shadow: 0 5px 0 #6b7280; /* Tailwind Gray-500 */
}
.cartoon-btn.secondary:hover {
  background-color: #d1d5db;
}
.cartoon-btn.secondary:active {
  top: 5px;
  box-shadow: 0 0 0 #6b7280;
}

/* 나가기 버튼 (빨간색) */
.cartoon-btn.primary {
  background-color: #ef5350;
  box-shadow: 0 5px 0 #c62828;
}
.cartoon-btn.primary:hover {
  background-color: #e53935;
}
.cartoon-btn.primary:active {
  top: 5px;
  box-shadow: 0 0 0 #c62828;
}

/* 애니메이션 */
.animate-fade-in { animation: fadeIn 0.2s ease-out forwards; }
.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards; }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes popIn { 
  from { opacity: 0; transform: scale(0.8) translateY(20px) rotate(-2deg); }
  to { opacity: 1; transform: scale(1) translateY(0) rotate(0deg); }
}
</style>