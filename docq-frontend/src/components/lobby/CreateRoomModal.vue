<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/userStore'
import { useRoomStore } from '@/stores/roomStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { createRoom as createRoomApi, getErrorMessage } from '@/services/api'

const router = useRouter();
const emit = defineEmits(['close']);
const userStore = useUserStore()
const roomStore = useRoomStore()
const wsStore = useWebSocketStore()
const isSubmitting = ref(false)

// 방 정보 상태
const roomName = ref('');
const maxUsers = ref(4);
const isSecret = ref(false); // 비밀방 여부
const password = ref('');

// PDF 업로드 상태: 'idle'(대기) | 'parsing'(분석중) | 'done'(완료)
const uploadStatus = ref<'idle' | 'parsing' | 'done'>('idle');
const fileName = ref('');

const fileInput = ref<HTMLInputElement | null>(null);
const triggerFileUpload = () => {
  if (uploadStatus.value === 'idle') {
    fileInput.value?.click();
  }
};

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    const file = target.files[0];
    if (file) {
      fileName.value = file.name;
      uploadStatus.value = 'parsing';
      // Mocking
      setTimeout(() => {
        uploadStatus.value = 'done';
      }, 3000);
    }
  }
};

const isReadyToCreate = computed(() => {
  return roomName.value.trim().length > 0 && uploadStatus.value === 'done';
});

const createRoom = async () => {
  if (!isReadyToCreate.value || isSubmitting.value) return;

  if (!userStore.userId) {
    alert('닉네임을 먼저 설정해주세요.')
    return
  }

  if (isSecret.value && !password.value.trim()) {
    alert('비밀방 비밀번호를 입력해주세요.')
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
    alert(getErrorMessage(error))
  } finally {
    isSubmitting.value = false
  }
};
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-[#881337]/20 backdrop-blur-sm p-4 animate-fade-in font-display">
    
    <div class="w-full max-w-4xl bg-white/90 backdrop-blur-xl rounded-[18px] shadow-[0_20px_60px_-15px_rgba(225,29,72,0.2)] p-10 border border-white animate-slide-up relative flex flex-col">
      
      <button @click="$emit('close')" class="absolute top-8 right-8 text-[#fda4af] hover:text-[#e11d48] transition-colors rounded-full p-1 hover:bg-[#fff1f2]">
        <span class="material-symbols-outlined text-3xl font-bold">close</span>
      </button>

      <h2 class="text-3xl font-extrabold text-[#881337] mb-8 text-center flex items-center justify-center gap-3">
        <span class="material-symbols-outlined text-4xl text-[#e11d48]">add_circle</span>
        방 만들기
      </h2>

      <div class="flex flex-col md:flex-row gap-10 mb-8 items-stretch">
        
        <div class="flex-1 flex flex-col gap-6">
          
          <div>
            <label class="block text-[#9f1239] font-bold mb-3 ml-1 flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[18px]">edit</span>
              방 제목
            </label>
            <input 
              v-model="roomName"
              type="text" 
              placeholder="예: 다 드루와" 
              class="w-full h-14 px-5 rounded-2xl border-2 border-[#fecdd3] bg-white focus:border-[#e11d48] focus:ring-4 focus:ring-[#e11d48]/10 outline-none font-bold text-lg text-[#881337] placeholder:text-[#fda4af] placeholder:font-normal transition-all"
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
                class="flex-1 h-14 rounded-2xl font-bold border-2 transition-all flex items-center justify-center"
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

          <div class="bg-[#fff1f2] p-6 rounded-3xl border border-[#fecdd3]">
            <div class="flex items-center justify-between mb-2">
              <span class="text-[#9f1239] font-bold flex items-center gap-2">
                <span class="material-symbols-outlined text-[#e11d48]">
                  {{ isSecret ? 'lock' : 'lock_open' }}
                </span>
                비밀방 설정
              </span>
            
              <button 
                @click="isSecret = !isSecret"
                class="w-14 h-8 rounded-full transition-colors relative shadow-inner"
                :class="isSecret ? 'bg-[#e11d48]' : 'bg-[#e2e8f0]'"
              >
                <div 
                  class="w-6 h-6 bg-white rounded-full absolute top-1 transition-all shadow-sm flex items-center justify-center"
                  :class="isSecret ? 'left-7' : 'left-1'"
                >
                  <span v-if="isSecret" class="material-symbols-outlined text-[#e11d48] text-[14px] font-bold">check</span>
                </div>
              </button>
            </div>
            
            <div v-if="isSecret" class="animate-fade-in mt-4">
              <input 
                v-model="password"
                type="password" 
                placeholder="비밀번호 4자리" 
                class="w-full h-12 px-4 rounded-xl border-2 border-[#fecdd3] focus:border-[#e11d48] focus:ring-2 focus:ring-[#e11d48]/10 outline-none font-bold text-[#881337] text-center tracking-widest bg-white placeholder:text-[#fda4af] placeholder:tracking-normal"
                maxlength="4"
              />
            </div>
          </div>
          <p class="text-xs text-[#fda4af] mt-3 text-center font-medium flex items-center justify-center gap-1">
            <span class="material-symbols-outlined text-[14px]">info</span>
            PDF 크기 및 페이지 수를 고려해주세요.
          </p>
          
        </div>

        <div class="flex-1 flex flex-col">
          <label class="block text-[#9f1239] font-bold mb-3 ml-1 flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[18px]">description</span>
            퀴즈 생성용 PDF
          </label>
          
          <div 
            @click="triggerFileUpload"
            class="w-full h-full min-h-[300px] rounded-[32px] border-4 border-dashed transition-all duration-300 flex flex-col items-center justify-center relative overflow-hidden group cursor-pointer"
            :class="[
              uploadStatus === 'idle' ? 'border-[#fecdd3] bg-[#fff1f2]/50 hover:bg-[#fff1f2] hover:border-[#e11d48]' : '',
              uploadStatus === 'parsing' ? 'border-[#e11d48] bg-[#fff1f2]' : '',
              uploadStatus === 'done' ? 'border-[#00b894] bg-[#f0fff4]' : ''
            ]"
          >
            <input 
              ref="fileInput"
              type="file" 
              accept=".pdf"
              class="hidden"
              @change="handleFileChange" 
            />

            <div v-if="uploadStatus === 'idle'" class="text-center group-hover:scale-105 transition-transform duration-300">
              <span class="material-symbols-outlined text-7xl mb-4 text-[#e11d48]/40 group-hover:text-[#e11d48] transition-colors">cloud_upload</span>
              <p class="text-[#9f1239] font-bold text-lg">PDF를 업로드해주세요</p>
              <p class="text-[#fda4af] text-sm mt-2 font-medium">클릭하여 파일 선택</p>
            </div>

            <div v-if="uploadStatus === 'parsing'" class="text-center flex flex-col items-center">
              <div class="w-16 h-16 border-4 border-[#fda4af] border-t-[#e11d48] rounded-full animate-spin mb-6"></div>
              <p class="text-[#e11d48] font-black text-lg animate-pulse">퀴즈 생성 중...</p>
              <p class="text-[#9f1239]/70 text-sm mt-1 font-bold truncate max-w-[200px]">{{ fileName }}</p>
            </div>

            <div v-if="uploadStatus === 'done'" class="text-center animate-bounce-up">
              <span class="material-symbols-outlined text-7xl mb-4 text-[#00b894]">check_circle</span>
              <p class="text-[#00b894] font-black text-lg">준비가 완료되었습니다!</p>
              <p class="text-[#00b894] text-sm mt-1 font-bold opacity-70 truncate max-w-[200px]">{{ fileName }}</p>
            </div>
          </div>
        </div>

      </div>

   <div class="flex gap-4 mt-auto">
        <button 
          @click="$emit('close')"
          class="flex-1 h-16 rounded-2xl font-bold text-lg text-[#e11d48] bg-white border-2 border-[#fecdd3] hover:bg-[#fff1f2] transition-colors"
        >
          취소
        </button>
        
        <button 
          @click="createRoom"
          :disabled="!isReadyToCreate"
          class="flex-1 h-16 rounded-2xl font-black text-xl transition-all flex items-center justify-center gap-2 shadow-lg"
          :class="isReadyToCreate 
            ? 'text-white bg-gradient-to-r from-[#e11d48] to-[#be123c] shadow-rose-500/30 hover:shadow-[0_0_25px_rgba(225,29,72,0.4)] active:scale-[0.98] cursor-pointer' 
            : 'text-[#cbd5e1] bg-[#f1f5f9] border-2 border-transparent cursor-not-allowed shadow-none'"
        >
          <span v-if="uploadStatus === 'parsing'" class="flex items-center gap-2">
            <span class="material-symbols-outlined animate-spin">progress_activity</span>
            분석 중...
          </span>
          <span v-else class="flex items-center gap-2">
            방 생성하기
            <span class="material-symbols-outlined">rocket_launch</span>
          </span>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.font-display { font-family: 'Plus Jakarta Sans', sans-serif; }

.animate-fade-in { animation: fadeIn 0.3s ease-out; }
.animate-slide-up { animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1); }
.animate-bounce-up { animation: bounceUp 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275); }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { transform: translateY(30px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
@keyframes bounceUp { 0% { transform: scale(0.8); opacity: 0; } 60% { transform: scale(1.05); } 100% { transform: scale(1); opacity: 1; } }
</style>