<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/userStore'
import { useRoomStore } from '@/stores/roomStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { getRoomByInviteCode, verifyJoinRoom, getErrorMessage } from '@/services/api'
import type { RoomResponse } from '@/types/docq-api'
import AlertModal from '@/components/common/AlertModal.vue';

const router = useRouter();
const emit = defineEmits(['close']);
const userStore = useUserStore()
const roomStore = useRoomStore()
const wsStore = useWebSocketStore()
const isSubmitting = ref(false)

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') emit('close');
};

onMounted(() => window.addEventListener('keydown', handleKeydown));
onUnmounted(() => window.removeEventListener('keydown', handleKeydown));

const roomCode = ref('');
const resolvedRoom = ref<RoomResponse | null>(null)
const lastCheckedCode = ref('')
const isNicknameSet = computed(() => !!userStore.playerId);

// 알림 상태
const isAlertOpen = ref(false);
const alertMessage = ref('');
const alertType = ref<'success' | 'error'>('error');
const isShaking = ref(false);

const triggerShake = () => {
  isShaking.value = true;
  setTimeout(() => { isShaking.value = false; }, 500);
}

const triggerAlert = (message: string, type: 'success' | 'error' = 'error') => {
  alertMessage.value = message;
  alertType.value = type;
  isAlertOpen.value = true;
  if (type === 'error') triggerShake();
}

// 방참가 로직
const joinRoom = async () => {
  const inviteCode = roomCode.value.trim().toUpperCase()
  if (!inviteCode) { triggerAlert("입장 코드를 입력해주세요!"); return; }
  if (!userStore.playerId) { triggerAlert('닉네임을 먼저 설정해주세요.'); return }
  if (isSubmitting.value) return
  
  isSubmitting.value = true

  try {
    const room = await getRoomByInviteCode(inviteCode)
    resolvedRoom.value = room
    lastCheckedCode.value = inviteCode

    await verifyJoinRoom({
      roomId: room.roomId
    })

    roomStore.joinRoom({
      roomId: room.roomId,
      inviteCode: inviteCode,
      title: room.title,
      capacity: room.capacity,
      isPrivate: room.isPrivate,
      status: room.status,
      currentCount: room.currentCount,
      quizCount: room.quizCount
    })

    await wsStore.connect()
    wsStore.subscribeErrors(userStore.playerId)
    wsStore.subscribeGameState(room.roomId)
    wsStore.subscribeMiniGame(room.roomId)
    wsStore.joinGame(room.roomId, userStore.playerId, userStore.username)

    router.push(`/waiting/${room.roomId}`);
    emit('close');
  } catch (error) {
    triggerAlert(getErrorMessage(error));
  } finally {
    isSubmitting.value = false
  }
};

const checkRoomType = async () => {
  const inviteCode = roomCode.value.trim().toUpperCase()
  if (!inviteCode || inviteCode === lastCheckedCode.value) return

  try {
    const room = await getRoomByInviteCode(inviteCode)
    resolvedRoom.value = room
    lastCheckedCode.value = inviteCode
  } catch (error) {
    resolvedRoom.value = null
  }
};
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center px-4 font-display">
    
    <div class="absolute inset-0 bg-black/40 backdrop-blur-sm animate-fade-in" @click="$emit('close')"></div>

    <div class="animate-pop-in w-full max-w-[360px] h-[300px] flex justify-center">
        
      <div class="relative bg-[#fff8e1] w-full rounded-[32px] box-border !px-8 py-12
                  flex flex-col border-[6px] border-[#8d6e63] shadow-[0_12px_35px_rgba(0,0,0,0.25)]"
           :class="{ 'animate-shake': isShaking }">
        
        <div class="absolute top-5 left-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="absolute top-5 right-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="absolute bottom-5 left-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="absolute bottom-5 right-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
        <div class="w-full h-6"></div>
        <h2 class="text-4xl font-display text-[#5d4037] mb-10 text-center drop-shadow-sm tracking-wide mt-2">
          참여하기
        </h2>
        <div class="w-full h-3"></div>
        <div class="flex flex-col gap-8 mb-10 w-full">
          
          <div class="w-full">
            <label class="block text-[#8d6e63] text-xl font-bold mb-3 ml-1 flex items-center gap-2">
              <span class="material-symbols-outlined text-2xl">link</span>
              입장 코드
            </label>
            <input 
              v-model="roomCode"
              type="text" 
              placeholder="예: A1B2" 
              class="w-full h-14 px-6 rounded-2xl border-4 border-[#d7ccc8] bg-white 
                     focus:border-[#7cb342] focus:bg-[#f1f8e9] outline-none 
                     font-black text-2xl text-center text-[#5d4037] tracking-[0.2em] uppercase 
                     placeholder:text-[#d7ccc8] placeholder:font-normal placeholder:tracking-normal placeholder:text-xl transition-all"
              @keyup.enter="joinRoom"
              @blur="checkRoomType"
            />
          </div>


        </div>

        <div class="h-6 w-full invisible"></div>

        <div class="flex gap-4 mt-auto w-full">
          <button 
            @click="$emit('close')"
            class="cartoon-btn secondary"
          >
            취소
          </button>
          <button 
            @click="joinRoom"
            class="cartoon-btn primary"
          >
            입장하기
          </button>
        </div>

      </div>
    </div>

    <AlertModal 
      :isOpen="isAlertOpen" 
      :message="alertMessage" 
      :type="alertType" 
      @close="isAlertOpen = false"
    />
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-display { font-family: 'Jua', sans-serif; }

/* 카툰 버튼 공통 스타일 */
.cartoon-btn {
  flex: 1;
  height: 60px;
  border-radius: 18px;
  font-size: 1.5rem;
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
}

/* 취소 버튼 (회색) */
.cartoon-btn.secondary {
  background-color: #9ca3af;
  box-shadow: 0 5px 0 #6b7280;
}
.cartoon-btn.secondary:hover { background-color: #d1d5db; }
.cartoon-btn.secondary:active { top: 5px; box-shadow: 0 0 0 #6b7280; }

/* 입장 버튼 (초록색) */
.cartoon-btn.primary {
  background-color: #7cb342;
  box-shadow: 0 5px 0 #558b2f;
}
.cartoon-btn.primary:hover { background-color: #8bc34a; }
.cartoon-btn.primary:active { top: 5px; box-shadow: 0 0 0 #558b2f; }

/* 애니메이션 */
.animate-fade-in { animation: fadeIn 0.2s ease-out forwards; }
.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards; }
/* shake에 translate3d 사용으로 GPU 가속 유도 */
.animate-shake { animation: shake 0.5s cubic-bezier(.36,.07,.19,.97) both; transform: translate3d(0, 0, 0); backface-visibility: hidden; } 
.animate-slide-down { animation: slideDown 0.3s ease-out forwards; }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes popIn { 
  from { opacity: 0; transform: scale(0.8) translateY(20px) rotate(-2deg); }
  to { opacity: 1; transform: scale(1) translateY(0) rotate(0deg); }
}
@keyframes shake {
  10%, 90% { transform: translate3d(-2px, 0, 0); }
  20%, 80% { transform: translate3d(4px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-6px, 0, 0); }
  40%, 60% { transform: translate3d(6px, 0, 0); }
}
@keyframes slideDown {
  from { opacity: 0; transform: translateY(-10px); max-height: 0; }
  to { opacity: 1; transform: translateY(0); max-height: 200px; }
}
</style>