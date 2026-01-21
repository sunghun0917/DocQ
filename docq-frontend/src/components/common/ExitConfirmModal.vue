<!-- <script setup lang="ts">
// 부모에게 '취소'했는지 '확인(나가기)'했는지 알려주는 이벤트 정의
const emit = defineEmits(['close', 'confirm']);
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm animate-fade-in">
    
    <div class="bg-white w-full max-w-sm rounded-[32px] p-8 text-center shadow-2xl border-4 border-white/20 animate-bounce-up">
      
      <div class="text-6xl mb-4">😢</div>
      
      <h3 class="text-2xl font-black text-[#2d3436] mb-2">정말 나가시겠어요?</h3>
      <p class="text-[#636e72] font-medium mb-8 break-keep">
        지금 나가시면 대기실의 친구들과<br>헤어지게 됩니다.
      </p>

      <div class="flex gap-3">
        <button 
          @click="$emit('close')"
          class="flex-1 h-12 rounded-xl font-bold text-[#636e72] bg-[#f1f2f6] hover:bg-[#dfe6e9] transition-colors"
        >
          아니요
        </button>
        
        <button 
          @click="$emit('confirm')"
          class="flex-1 h-12 rounded-xl font-bold text-white bg-[#ff7675] shadow-[0_4px_0_0_#d63031] hover:bg-[#ff6b6b] active:translate-y-1 active:shadow-none transition-all"
        >
          나갈래요
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
.animate-bounce-up { animation: bounceUp 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes bounceUp { 
  from { transform: scale(0.8) translateY(20px); opacity: 0; } 
  to { transform: scale(1) translateY(0); opacity: 1; } 
}
</style> -->
<script setup lang="ts">
defineProps<{
  isOpen: boolean;
}>();

const emit = defineEmits(['close', 'confirm']);
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="isOpen" class="fixed inset-0 z-[200] flex items-center justify-center px-4" role="dialog">
        
        <div class="absolute inset-0 bg-[#881337]/20 backdrop-blur-sm transition-opacity" @click="emit('close')"></div>

        <div class="relative bg-white w-full max-w-[340px] rounded-[32px] p-8 shadow-[0_20px_60px_-15px_rgba(225,29,72,0.3)] 
                    flex flex-col items-center text-center transform transition-all scale-100 border border-[#fecdd3]">
          
          <div class="w-16 h-16 rounded-full bg-[#fff1f2] text-[#e11d48] flex items-center justify-center mb-5 shadow-inner">
            <span class="material-symbols-outlined text-[32px]">logout</span>
          </div>

          <h3 class="text-2xl font-extrabold text-[#881337] mb-2 tracking-tight">
            방 나가기
          </h3>
          <p class="text-[#9f1239]/80 text-sm font-medium leading-relaxed mb-8">
            정말 대기실에서 나가시겠습니까?<br>
            게임 준비 상태가 취소됩니다.
          </p>

          <div class="flex gap-3 w-full">
            <button 
              @click="emit('close')"
              class="flex-1 h-12 rounded-2xl font-bold text-[#881337] bg-[#fff1f2] hover:bg-[#ffe4e6] transition-colors"
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
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active, .modal-leave-active { transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1); }
.modal-enter-from, .modal-leave-to { opacity: 0; transform: scale(0.9) translateY(10px); }
.modal-enter-to, .modal-leave-from { opacity: 1; transform: scale(1) translateY(0); }
</style>