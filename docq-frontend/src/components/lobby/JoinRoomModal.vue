<script setup lang="ts">
import { ref, computed } from 'vue';
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

const roomCode = ref('');
const password = ref('');
const isPrivateRoom = ref(false);
const resolvedRoom = ref<RoomResponse | null>(null)
const lastCheckedCode = ref('')
const isNicknameSet = computed(() => !!userStore.userId);
// [추가] 알림(토스트) 상태 관리
const isAlertOpen = ref(false);
const alertMessage = ref('');
const alertType = ref<'success' | 'error'>('error');
const isShaking = ref(false);
// [추가] 알림 띄우기 도우미 함수
const triggerAlert = (message: string, type: 'success' | 'error' = 'error') => {
  alertMessage.value = message;
  alertType.value = type;
  isAlertOpen.value = true;
  if (type === 'error') {
    triggerShake();
  }
}
const triggerShake = () => {
  isShaking.value = true;
  setTimeout(() => {
    isShaking.value = false;
  }, 500);
}
// 방참가 or 방취소 로직
const joinRoom = async () => {
  const inviteCode = roomCode.value.trim().toUpperCase()
  if (!inviteCode) {
    triggerAlert("입장 코드를 입력해주세요!"); // [수정] alert -> triggerAlert
    return;
  }

  if (!userStore.userId) {
    triggerAlert('닉네임을 먼저 설정해주세요.'); // [수정]
    return
  }

  if (isSubmitting.value) return
  isSubmitting.value = true

  try {
    const room = await getRoomByInviteCode(inviteCode)
    resolvedRoom.value = room
    lastCheckedCode.value = inviteCode
    isPrivateRoom.value = room.isPrivate

    if (room.isPrivate && !password.value.trim()) {
      triggerAlert("비밀번호를 입력해주세요!"); // [수정]
      return;
    }

    await verifyJoinRoom({
      roomId: room.roomId,
      password: room.isPrivate ? password.value.trim() : undefined
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
    wsStore.subscribeErrors(userStore.userId)
    wsStore.subscribeGameState(room.roomId)
    wsStore.joinGame(room.roomId, userStore.userId, userStore.username)

    router.push(`/waiting/${room.roomId}`);
    emit('close');
  } catch (error) {
    triggerAlert(getErrorMessage(error)); // [수정]
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
    isPrivateRoom.value = room.isPrivate
  } catch (error) {
    resolvedRoom.value = null
    isPrivateRoom.value = false
  }
};
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-[#881337]/20 backdrop-blur-sm p-4 animate-fade-in font-display">
    
    <div class="w-full max-w-sm bg-white/95 backdrop-blur-xl rounded-[18px] shadow-[0_20px_60px_-15px_rgba(225,29,72,0.2)] p-14 border-4 border-white ring-4 ring-[#ffe4e6] animate-slide-up relative">


      <h2 class="text-3xl font-extrabold text-[#881337] mb-2 text-center flex items-center justify-center gap-3">
        참여하기
      </h2>

      <div class="space-y-8 mb-10">
        <div>
          <label class="block text-[#9f1239] font-bold mb-3 ml-1 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[18px]">link</span>
            입장 코드
          </label>
          <input 
            v-model="roomCode"
            type="text" 
            placeholder="예: A1B2" 
            class="w-full h-10 px-6 rounded-xl border-2 border-[#fecdd3] bg-white focus:border-[#e11d48] focus:ring-4 focus:ring-[#e11d48]/10 outline-none font-black text-xl text-center text-[#881337] tracking-[0.2em] uppercase transition-all placeholder:font-normal placeholder:tracking-normal placeholder:text-xl placeholder:text-[#fda4af]"
            :class="{ 'animate-shake ring-4 ring-red-500/50 border-red-500': isShaking }"
            @keyup.enter="joinRoom"
            @blur="checkRoomType"
          />
        </div>

        <div v-if="isPrivateRoom" class="animate-slide-down bg-[#fff1f2] p-6 rounded-3xl border border-[#fecdd3]">
          <label class="block text-[#9f1239] font-bold mb-3 ml-1 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[18px]">lock</span>
            비밀번호
          </label>
          <input 
            v-model="password"
            type="password" 
            placeholder="비밀번호 4자리" 
            maxlength="4"
            class="w-full h-10 px-6 rounded-2xl border-2 border-[#fecdd3] bg-white focus:border-[#e11d48] focus:ring-2 focus:ring-[#e11d48]/10 outline-none font-black text-xl text-[#881337] text-center tracking-widest transition-all placeholder:text-[#fda4af] placeholder:font-normal placeholder:tracking-normal"
            :class="{ 'animate-shake ring-4 ring-red-500/50 border-red-500': isShaking }"
            @keyup.enter="joinRoom"
          />
        </div>
      </div>
      <div v-if="!isNicknameSet" class="h-4 w-full"></div>

      <div class="flex gap-4">
        <button 
          @click="$emit('close')"
          class="flex-1 h-12 rounded-2xl font-bold text-lg text-[#e11d48] bg-white border-2 border-[#fecdd3] hover:bg-[#fff1f2] transition-colors"
        >
          취소
        </button>
        <button 
          @click="joinRoom"
          class="flex-[2] h-12 rounded-2xl font-black text-xl text-white bg-gradient-to-r from-[#e11d48] to-[#be123c] shadow-lg shadow-rose-500/30 hover:shadow-[0_0_25px_rgba(225,29,72,0.4)] active:scale-[0.98] transition-all flex items-center justify-center gap-2"
        >
          입장하기
          <span class="material-symbols-outlined">arrow_forward</span>
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
.font-display { font-family: 'Plus Jakarta Sans', sans-serif; }

.animate-fade-in { animation: fadeIn 0.2s ease-out; }
.animate-slide-up { animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.animate-slide-down { animation: slideDown 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
/* [추가] 흔들림 애니메이션 */
.animate-shake {
  animation: shake 0.5s cubic-bezier(.36,.07,.19,.97) both;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@keyframes slideDown {
  from { 
    opacity: 0; 
    transform: translateY(-10px);
    max-height: 0;
  }
  to { 
    opacity: 1; 
    transform: translateY(0);
    max-height: 200px;
  }
}

@keyframes shake {
  10%, 90% { transform: translate3d(-1px, 0, 0); }
  20%, 80% { transform: translate3d(2px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-4px, 0, 0); }
  40%, 60% { transform: translate3d(4px, 0, 0); }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@keyframes slideDown {
  from { 
    opacity: 0; 
    transform: translateY(-10px);
    max-height: 0;
  }
  to { 
    opacity: 1; 
    transform: translateY(0);
    max-height: 200px;
  }
}
</style>