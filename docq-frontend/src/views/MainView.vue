<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/userStore';
import { getErrorMessage } from '@/services/api';
import AlertModal from '@/components/common/AlertModal.vue';
import CreateRoomModal from '@/components/lobby/CreateRoomModal.vue';
import JoinRoomModal from '@/components/lobby/JoinRoomModal.vue';
import { CursorTrail } from '@/utils/cursorTrail';

const userStore = useUserStore();
const router = useRouter();
const nickname = ref('');
const isNicknameSet = ref(false);
const showCreateModal = ref(false);
const showJoinModal = ref(false);
const isErrorModalOpen = ref(false);
const errorMessage = ref('');
const isShaking = ref(false); 
const isHovered = ref(false);
const canvasRef = ref<HTMLCanvasElement | null>(null);
let cursorTrail: CursorTrail | null = null; 

const confirmNickname = async () => {
  if (nickname.value.trim().length < 2) {
    triggerShake(); 
    errorMessage.value = "닉네임은 최소 2글자 이상이어야 합니다.";
    isErrorModalOpen.value = true;
    return;
  }
  
  const result = await userStore.loginWithApi(nickname.value.trim());
  
  if (!result.success) {
    triggerShake(); 
    errorMessage.value = getErrorMessage((result as any).error);
    isErrorModalOpen.value = true;
    return;
  }
  isNicknameSet.value = true;
};

const triggerShake = () => {
  isShaking.value = true;
  setTimeout(() => {
    isShaking.value = false;
  }, 500);
}

const editNickname = () => {
  isNicknameSet.value = false;
};

const handleCreateRoom = () => { showCreateModal.value = true; };
const handleJoinRoom = () => { showJoinModal.value = true; };

const syncNicknameFromStore = () => {
  if (isNicknameSet.value) return;
  if (userStore.isAuthenticated && userStore.username && userStore.username !== 'Guest') {
    nickname.value = userStore.username;
    isNicknameSet.value = true;
  }
};

onMounted(() => {
  userStore.restoreUser();
  syncNicknameFromStore();
  
  if (canvasRef.value) {
    cursorTrail = new CursorTrail(canvasRef.value);
    cursorTrail.start();
  }
});

onUnmounted(() => {
  if (cursorTrail) {
    cursorTrail.destroy();
  }
});

watch(
  () => userStore.isAuthenticated,
  () => {
    syncNicknameFromStore();
  }
);
</script>

<template>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet">
  
  <div class="bg-[#fff1f2] min-h-screen font-display antialiased overflow-hidden selection:bg-[#e11d48]/30 relative flex flex-col justify-center items-center">
    
    <!-- 마우스 트레일 캔버스 -->
    <canvas ref="canvasRef" class="absolute inset-0 pointer-events-none z-10"></canvas>

    <div class="absolute inset-0 pointer-events-none z-0 overflow-hidden">
      <div class="absolute -top-[10%] -right-[10%] w-[800px] h-[800px] rounded-full 
                  bg-gradient-to-br from-[#e11d48]/20 to-[#fda4af]/30 
                  blur-[80px] animate-float">
      </div>
      <div class="absolute -bottom-[10%] -left-[10%] w-[800px] h-[800px] rounded-full 
                  bg-gradient-to-tr from-[#fb923c]/20 to-[#e11d48]/10 
                  blur-[80px] animate-float-delay">
      </div>
      <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full 
                  bg-rose-300/10 blur-[100px] animate-float">
      </div>
      <svg class="absolute top-[20%] left-[10%] w-20 h-20 text-[#e11d48]/20 rotate-12 animate-float-delay" fill="currentColor" viewBox="0 0 24 24">
        <rect height="24" rx="4" width="24"></rect>
      </svg>
      <svg class="absolute top-[30%] right-[15%] w-16 h-16 text-[#fb923c]/30 -rotate-12 animate-float" fill="currentColor" viewBox="0 0 24 24">
        <circle cx="12" cy="12" r="10"></circle>
      </svg>
      <svg class="absolute bottom-[20%] left-[45%] w-24 h-24 text-[#e11d48]/10 rotate-45 animate-float-delay" fill="currentColor" viewBox="0 0 24 24">
        <polygon points="12,2 22,22 2,22"></polygon>
      </svg>
    </div>

    <div class="relative z-10 flex flex-col w-full max-w-md px-6 py-8">
      
      <div class="flex flex-col gap-1 items-center justify-center mb-10 transition-all duration-500" :class="isNicknameSet ? 'scale-90' : 'scale-100'">
        <h1 
          class="animated-gradient-text text-[80px] font-extrabold tracking-tight leading-none text-center"
          @mouseenter="isHovered = true"
          @mouseleave="isHovered = false"
        >
          DocQ
        </h1>
        <div class="py-4"></div>
        <p class="text-[#9f1239]/70 text-[24px] font-medium mt-2 tracking-tight">PDF 문서 기반 퀴즈 게임</p>
      </div>
      
      <div class="w-full mb-8 mt-6 relative group">
        <label class="sr-only" for="nickname">Nickname</label>
        <div class="relative flex items-center">
          <input 
            id="nickname"
            v-model="nickname"
            type="text"
            placeholder="닉네임을 입력해 주세요"
            :disabled="isNicknameSet"
            @keyup.enter="!isNicknameSet && confirmNickname()"
            class="w-full h-20 bg-white/80 backdrop-blur-sm text-[#881337] placeholder:text-[#fda4af] 
                   px-6 rounded-full border-none 
                   shadow-[inset_2px_2px_4px_rgba(163,22,57,0.1),inset_-2px_-2px_4px_rgba(255,255,255,0.8)] 
                   focus:ring-2 focus:ring-[#e11d48] focus:shadow-lg focus:shadow-rose-500/10 
                   transition-all duration-300 text-lg text-center font-bold outline-none"
            :class="[
              isNicknameSet ? 'opacity-70 cursor-not-allowed bg-gray-50' : '',
              isShaking ? 'animate-shake ring-4 ring-red-500/50' : '' 
            ]"
          />

          <div class="absolute right-4">
            <button 
              v-if="!isNicknameSet"
              @click="confirmNickname"
              class="p-3 bg-white rounded-full shadow-sm text-slate-300 hover:text-[#e11d48] hover:bg-rose-50 transition-colors"
            >
              <span class="material-symbols-outlined text-[28px] block">arrow_forward</span>
            </button>
            <button 
              v-else
              @click="editNickname"
              class="w-12 h-12 flex items-center justify-center bg-white rounded-full shadow-md text-[#e11d48]/60 hover:text-[#e11d48] hover:bg-rose-50 transition-colors hover:scale-110 active:scale-95"
              title="닉네임 수정"
            >
              <span class="material-symbols-outlined text-[32px]">edit</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 간격 유지를 위한 spacer -->
      <div v-if="isNicknameSet" class="h-4"></div>

      <div v-if="isNicknameSet" class="flex flex-col gap-4 w-full animate-slide-up">
        <button 
          @click="handleCreateRoom"
          class="relative group w-full h-16 rounded-full bg-gradient-to-r from-[#e11d48] to-[#be123c] hover:to-[#9f1239] text-white 
                 shadow-lg shadow-rose-500/30 hover:shadow-[0_0_25px_rgba(225,29,72,0.5)] 
                 transition-all duration-300 transform active:scale-[0.98]"
        >
          <div class="flex items-center justify-center gap-3">
            <span class="material-symbols-outlined text-2xl group-hover:rotate-90 transition-transform duration-300">add_circle</span>
            <span class="text-lg font-bold tracking-wide">방 만들기</span>
          </div>
        </button>

        <button 
          @click="handleJoinRoom"
          class="relative w-full h-16 rounded-full bg-white text-[#881337] border-2 border-[#fecdd3] 
                 hover:bg-[#fff1f2] hover:border-[#e11d48] transition-all duration-300 transform active:scale-[0.98]"
        >
          <div class="flex items-center justify-center gap-3">
            <span class="material-symbols-outlined text-2xl">login</span>
            <span class="text-lg font-bold tracking-wide">참여하기</span>
          </div>
        </button>
      </div>

      <div v-if="!isNicknameSet" class="h-4 w-full"></div>

      <div v-if="!isNicknameSet" class="w-full animate-fade-in">
        <button 
          @click="confirmNickname"
          class="w-full h-14 rounded-full bg-[#e11d48] text-white font-bold text-lg 
                 shadow-lg shadow-rose-500/30 hover:bg-[#be123c] transition-all active:scale-[0.98]"
        >
          입력
        </button>
      </div>

      <div class="mt-8 text-center">
        <p class="text-md font-semibold text-[#fda4af] uppercase tracking-widest">
          • Beta •
        </p>
      </div>

    </div>

    <AlertModal 
      :isOpen="isErrorModalOpen"
      type="error"
      :message="errorMessage"
      @close="isErrorModalOpen = false"
    />
    <CreateRoomModal v-if="showCreateModal" @close="showCreateModal = false" />
    <JoinRoomModal v-if="showJoinModal" @close="showJoinModal = false" />
  </div>
</template>

<style scoped>
/* 1. 폰트 설정 */
.font-display {
  font-family: 'Plus Jakarta Sans', sans-serif;
}

/* 2. 유틸리티 클래스 */
.animate-slide-up {
  animation: slideUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
  
.animate-fade-in {
  animation: fadeIn 0.4s ease-out forwards;
}

.animate-float {
  animation: float 10s infinite ease-in-out;
}

.animate-float-delay {
  animation: float 12s infinite ease-in-out reverse;
}

.animate-shake {
  animation: shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
}

/* 4. 그라디언트 텍스트 애니메이션 */
.animated-gradient-text {
  background: linear-gradient(
    90deg,
    #881337,
    #e11d48,
    #fb923c,
    #fda4af,
    #e11d48,
    #881337
  );
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: gradientFlow 3s linear infinite;
  filter: drop-shadow(0 2px 4px rgba(136, 19, 55, 0.2));
  transition: filter 0.3s ease;
}

.animated-gradient-text:hover {
  filter: drop-shadow(0 0 12px rgba(225, 29, 72, 0.4)) 
          drop-shadow(0 0 24px rgba(225, 29, 72, 0.2));
}

/* 3. 키프레임 정의 */
@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes float {
  0% { transform: translate(0, 0); }
  50% { transform: translate(20px, -20px); }
  100% { transform: translate(0, 0); }
}

@keyframes shake {
  10%, 90% { transform: translate3d(-1px, 0, 0); }
  20%, 80% { transform: translate3d(2px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-4px, 0, 0); }
  40%, 60% { transform: translate3d(4px, 0, 0); }
}

@keyframes gradientFlow {
  0% { background-position: 0% 50%; }
  100% { background-position: 200% 50%; }
}
</style>