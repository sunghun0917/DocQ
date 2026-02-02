<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/userStore'
import { useRoomStore } from '@/stores/roomStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { createRoom as createRoomApi, getErrorMessage } from '@/services/api'
import AlertModal from '@/components/common/AlertModal.vue'; 

const router = useRouter();
const emit = defineEmits(['close']);
const userStore = useUserStore()
const roomStore = useRoomStore()
const wsStore = useWebSocketStore()
const isSubmitting = ref(false)

// 방 정보 상태
const roomName = ref('');
const maxUsers = ref(4);
const isSecret = ref(false);
const password = ref('');

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

const isReadyToCreate = computed(() => roomName.value.trim().length > 0);

const createRoom = async () => {
  if (!isReadyToCreate.value || isSubmitting.value) return;
  if (!userStore.playerId) { triggerAlert('닉네임을 먼저 설정해주세요.'); return }
  
  isSubmitting.value = true

  try {
    const room = await createRoomApi({
      playerId: userStore.playerId,
      capacity: maxUsers.value,
      title: roomName.value.trim(),
      isPrivate: isSecret.value,
      password: isSecret.value ? password.value.trim() : undefined
    })

    roomStore.createRoom({
      roomId: room.roomId,
      inviteCode: room.inviteCode,
      hostId: room.hostPlayerId,
      title: roomName.value.trim(),
      capacity: maxUsers.value,
      isPrivate: room.isPrivate,
      status: 'WAITING',
      currentCount: 0,
      quizCount: 0
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
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center px-4 font-display">
    
    <div class="absolute inset-0 bg-black/40 backdrop-blur-sm animate-fade-in" @click="$emit('close')"></div>

    <div class="relative bg-[#fff8e1] w-[480px] h-[380px] rounded-[32px] box-border !px-8 py-12
                flex flex-col border-[6px] border-[#8d6e63] shadow-[0_12px_35px_rgba(0,0,0,0.25)] 
                animate-pop-in"
         :class="{ 'animate-shake': isShaking }">
      
      <div class="absolute top-5 left-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
      <div class="absolute top-5 right-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
      <div class="absolute bottom-5 left-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
      <div class="absolute bottom-5 right-5 w-3.5 h-3.5 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
      <div class="h-4 w-full invisible"></div>
      <h2 class="text-4xl font-display text-[#5d4037] mb-10 text-center drop-shadow-sm tracking-wide mt-2">
        방 만들기
      </h2>

      <div class="flex flex-col gap-4 mb-10 w-full">
        
        <div class="w-full">
          <label class="block text-[#8d6e63] text-xl font-bold mb-3 ml-1 flex items-center gap-2">
            <span class="material-symbols-outlined text-2xl">edit</span>
            방 제목
          </label>
          <input 
            v-model="roomName"
            type="text" 
            placeholder="예: 다 드루와" 
            class="w-full h-14 px-6 rounded-2xl border-4 border-[#d7ccc8] bg-white 
                   focus:border-[#7cb342] focus:bg-[#f1f8e9] outline-none 
                   font-bold text-xl text-[#5d4037] placeholder:text-[#d7ccc8] transition-all"
          />
        </div>

        <div class="w-full">
          <label class="block text-[#8d6e63] text-xl font-bold mb-3 ml-1 flex items-center gap-2">
            <span class="material-symbols-outlined text-2xl">group_add</span>
            최대 인원
          </label>
          
          <div class="flex gap-3 w-full">
            <button 
              v-for="num in [2, 3, 4]" 
              :key="num"
              @click="maxUsers = num"
              class="flex-1 h-14 rounded-2xl font-bold border-4 transition-all flex items-center justify-center relative top-0 active:top-1"
              :class="maxUsers === num 
                ? 'bg-[#7cb342] border-[#558b2f] text-white shadow-[0_4px_0_#558b2f] active:shadow-none' 
                : 'bg-white border-[#d7ccc8] text-[#bcaaa4] hover:border-[#8d6e63] hover:text-[#8d6e63]'"
            >
              <div class="flex items-center" :class="num === 4 ? '-space-x-2' : '-space-x-1'">
                <span v-for="i in num" :key="i" class="material-symbols-outlined text-2xl">person</span>
              </div>
            </button>
          </div>
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
          @click="createRoom"
          :disabled="!isReadyToCreate"
          class="cartoon-btn"
          :class="isReadyToCreate ? 'primary' : 'disabled'"
        >
          <span class="flex items-center gap-2">
            방 생성
          </span>
        </button>
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

/* 생성 버튼 (초록색) */
.cartoon-btn.primary {
  background-color: #7cb342;
  box-shadow: 0 5px 0 #558b2f;
}
.cartoon-btn.primary:hover { background-color: #8bc34a; }
.cartoon-btn.primary:active { top: 5px; box-shadow: 0 0 0 #558b2f; }

/* 비활성 버튼 */
.cartoon-btn.disabled {
  background-color: #e0e0e0;
  color: #bdbdbd;
  cursor: not-allowed;
  box-shadow: none;
  border: 4px solid #d7ccc8;
}

/* 애니메이션 */
.animate-fade-in { animation: fadeIn 0.2s ease-out forwards; }
.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards; }
.animate-shake { animation: shake 0.4s cubic-bezier(.36,.07,.19,.97) both; }

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
</style>