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

// 알림(토스트) 상태 관리
const isAlertOpen = ref(false);
const alertMessage = ref('');
const alertType = ref<'success' | 'error'>('error');
// 흔들림 상태 관리
const isShaking = ref(false);

// 흔들림 트리거 함수
const triggerShake = () => {
  isShaking.value = true;
  setTimeout(() => {
    isShaking.value = false;
  }, 500); // 애니메이션 시간과 동일하게 설정
}
// 알림 띄우기 도우미 함수
const triggerAlert = (message: string, type: 'success' | 'error' = 'error') => {
  alertMessage.value = message;
  alertType.value = type;
  isAlertOpen.value = true;

  if (type === 'error') {
    triggerShake();
  }
}
// [수정] PDF 관련 로직 제거됨 (isReadyToCreate 조건 단순화)
const isReadyToCreate = computed(() => {
  return roomName.value.trim().length > 0;
});

const createRoom = async () => {
  if (!isReadyToCreate.value || isSubmitting.value) return;

  if (!userStore.userId) {
    triggerAlert('닉네임을 먼저 설정해주세요.')
    return
  }

  if (isSecret.value && !password.value.trim()) {
    triggerAlert('비밀방 비밀번호를 입력해주세요.')
    return
  }

  isSubmitting.value = true

  try {
    const room = await createRoomApi({
      userId: userStore.userId,
      capacity: maxUsers.value,
      title: roomName.value.trim(),
      isPrivate: isSecret.value,
      password: isSecret.value ? password.value.trim() : undefined
    })

    roomStore.createRoom({
      roomId: room.roomId,
      inviteCode: room.inviteCode,
      hostId: room.hostId,
      title: roomName.value.trim(),
      capacity: maxUsers.value,
      isPrivate: room.isPrivate,
      status: 'WAITING',
      currentCount: 0
    })

    await wsStore.connect()
    wsStore.subscribeErrors(userStore.userId)
    wsStore.subscribeGameState(room.roomId)
    wsStore.joinGame(room.roomId, userStore.userId, userStore.username)

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
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-[#881337]/20 backdrop-blur-sm p-4 animate-fade-in font-display">
    <div class="w-full max-w-sm bg-white/95 backdrop-blur-xl rounded-[18px] shadow-[0_20px_60px_-15px_rgba(225,29,72,0.3)] p-14 border-4 border-white ring-4 ring-[#ffe4e6] animate-slide-up relative flex flex-col">
      

      <h2 class="text-3xl font-extrabold text-[#881337] mb-8 text-center flex items-center justify-center gap-3">
        방 만들기
      </h2>

      <div class="flex flex-col gap-3 mb-10">
        
        <div>
          <label class="block text-[#9f1239] font-bold mb-3 ml-1 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[18px]">edit</span>
            방 제목
          </label>
          <input 
            v-model="roomName"
            type="text" 
            placeholder="예: 다 드루와" 
            class="w-full h-10 px-6 rounded-2xl border-2 border-[#fecdd3] bg-white focus:border-[#e11d48] focus:ring-4 focus:ring-[#e11d48]/10 outline-none font-bold text-xl text-[#881337] placeholder:text-[#fda4af] placeholder:font-normal transition-all"
          />
        </div>

        <div>
          <label class="block text-[#9f1239] font-bold mb-3 ml-1 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[18px]">group_add</span>
            최대 인원
          </label>

          <div class="flex gap-3">
            <button 
              v-for="num in [2, 3, 4]" 
              :key="num"
              @click="maxUsers = num"
              class="flex-1 h-10 rounded-2xl font-bold border-2 transition-all flex items-center justify-center"
              :class="maxUsers === num 
                ? 'bg-gradient-to-r from-[#e11d48] to-[#be123c] text-white border-transparent shadow-lg shadow-rose-500/30' 
                : 'bg-white text-[#fda4af] border-[#fecdd3] hover:bg-[#fff1f2] hover:border-[#e11d48]'"
            >
              <div class="flex items-center" :class="num === 4 ? '-space-x-1.5' : '-space-x-1'">
                <span 
                  v-for="i in num" 
                  :key="i" 
                  class="material-symbols-outlined text-[26px] transition-transform group-hover:scale-110"
                >
                  person
                </span>
              </div>
            </button>
          </div>
        </div>

        <div>
          <label class="block text-[#9f1239] font-bold mb-3 ml-1 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[18px]">lock_person</span>
            공개 설정
          </label>

          <div 
            class="flex h-10 rounded-2xl border-2 overflow-hidden transition-all duration-200"
            :class="isSecret 
              ? 'border-[#e11d48] ring-4 ring-[#e11d48]/10 bg-white' 
              : 'border-[#fecdd3] bg-gray-50'"
          >
            <button 
              @click="isSecret = !isSecret"
              class="w-[110px] flex flex-col items-center justify-center border-r-2 transition-colors cursor-pointer hover:bg-opacity-90 gap-0.5"
              :class="isSecret 
                ? 'bg-[#fff1f2] text-[#e11d48] border-[#e11d48]' 
                : 'bg-white text-[#9ca3af] border-[#fecdd3] hover:bg-[#fff1f2] hover:text-[#fda4af]'"
            >
              <span class="material-symbols-outlined text-[32px] transition-transform duration-200" :class="{ 'scale-110': isSecret }">
                {{ isSecret ? 'check_box' : 'check_box_outline_blank' }}
              </span>
            </button>

            <div class="flex-1 relative flex items-center justify-center">
              <input 
                v-model="password"
                type="password" 
                placeholder="비밀번호 4자리" 
                maxlength="4"
                :disabled="!isSecret"
                class="w-full h-full bg-transparent text-center font-black text-lg tracking-[0.5em] text-[#881337] outline-none placeholder:text-[#fda4af] placeholder:tracking-normal placeholder:font-bold transition-all disabled:cursor-not-allowed disabled:bg-gray-50 disabled:placeholder:text-gray-300"
                :class="{ 'opacity-50': !isSecret, 'animate-shake': isShaking && isSecret }"
              />
            </div>
          </div>
        </div>
      </div>
      <div class="h-4 w-full"></div>
      <div class="flex gap-4 mt-auto">
        <button 
          @click="$emit('close')"
          class="flex-1 h-12 rounded-2xl font-bold text-lg text-[#e11d48] bg-white border-2 border-[#fecdd3] hover:bg-[#fff1f2] transition-colors"
        >
          취소
        </button>
        
        <button 
          @click="createRoom"
          :disabled="!isReadyToCreate"
          class="flex-1 h-12 rounded-2xl font-black text-xl transition-all flex items-center justify-center gap-2 shadow-lg"
          :class="isReadyToCreate 
            ? 'text-white bg-gradient-to-r from-[#e11d48] to-[#be123c] shadow-rose-500/30 hover:shadow-[0_0_25px_rgba(225,29,72,0.4)] active:scale-[0.98] cursor-pointer' 
            : 'text-[#cbd5e1] bg-[#f1f5f9] border-2 border-transparent cursor-not-allowed shadow-none'"
        >
          <span class="flex items-center gap-2">
            방 생성하기
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
.font-display { font-family: 'Plus Jakarta Sans', sans-serif; }

.animate-fade-in { animation: fadeIn 0.3s ease-out; }
.animate-slide-up { animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1); }

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s ease-out;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
.animate-shake {
  animation: shake 0.5s cubic-bezier(.36,.07,.19,.97) both;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { transform: translateY(30px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
@keyframes shake {
  10%, 90% { transform: translate3d(-1px, 0, 0); }
  20%, 80% { transform: translate3d(2px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-4px, 0, 0); }
  40%, 60% { transform: translate3d(4px, 0, 0); }
}
</style>