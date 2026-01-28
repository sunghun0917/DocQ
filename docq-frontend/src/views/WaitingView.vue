<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick, watchEffect } from 'vue';
import UserCard from '@/components/lobby/UserCard.vue';
import { useRouter, useRoute } from 'vue-router';
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { getRoomById, getErrorMessage, generateQuiz, getQuizzes, setDefaultQuizzes } from '@/services/api'
import type { QuizResponse } from '@/types/docq-api'
import AlertModal from '@/components/common/AlertModal.vue';
import ExitConfirmModal from '@/components/common/ExitConfirmModal.vue';

const router = useRouter();
const route = useRoute();
const roomStore = useRoomStore()
const userStore = useUserStore()
const wsStore = useWebSocketStore()

const roomId = computed(() => String(route.params.roomId || ''))
const roomInfo = computed(() => roomStore.roomInfo)
const users = computed(() => roomStore.users)
const myUser = computed(() => roomStore.myUser)

const roomTypeIcon = computed(() => (roomInfo.value.isPrivate ? 'lock' : 'public'))
const currentCount = computed(() => users.value.length)
const maxUsers = computed(() => roomInfo.value.capacity || 0)

// [HEAD] 디자인용 상태
const isToastOpen = ref(false);
const toastMessage = ref('');
const toastType = ref<'success' | 'error'>('success');
const showExitModal = ref(false);

// [Frontend] 퀴즈 관련 상태 추가
const isGenerating = ref(false)
const selectedFile = ref<File | null>(null);
const generatedQuizzes = ref<QuizResponse[]>([])
const isQuizModalOpen = ref(false)
const isBasicQuizModalOpen = ref(false)
// [Frontend] 대기 상태 메시지
const processingText = ref('방장님이 문서를 고르고 있어요')

const basicQuizTypes = [
  { type: 'nonsense', label: '넌센스 퀴즈', icon: 'sentiment_very_satisfied', color: 'bg-yellow-100 text-yellow-600 border-yellow-200' },
  { type: 'common', label: '상식 퀴즈', icon: 'school', color: 'bg-blue-100 text-blue-600 border-blue-200' },
  { type: 'mixed', label: '랜덤 퀴즈', icon: 'shuffle', color: 'bg-purple-100 text-purple-600 border-purple-200' },
]

// [Frontend] 파일 선택 핸들러
const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    selectedFile.value = target.files[0] || null
  }
}

// [Frontend] PDF 업로드 및 퀴즈 생성
const handleUploadPdf = async () => {
  if (!selectedFile.value || !roomId.value) return
  
  isGenerating.value = true
  try {
    await generateQuiz(roomId.value, selectedFile.value)
    await loadQuizzes() // [ADD] 생성 직후 목록 갱신
    selectedFile.value = null // Reset check
  } catch (error) {
    toastMessage.value = '퀴즈 생성 실패: ' + getErrorMessage(error);
    toastType.value = 'error';
    isToastOpen.value = true;
  } finally {
    isGenerating.value = false
  }
}

// [추가] 드래그 상태 관리
const isDragging = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

// [추가] 드래그 앤 드롭 핸들러
const onDragOver = (e: DragEvent) => {
  e.preventDefault();
  isDragging.value = true;
};

const onDragLeave = (e: DragEvent) => {
  e.preventDefault();
  isDragging.value = false;
};

const onDrop = (e: DragEvent) => {
  e.preventDefault();
  isDragging.value = false;
  
  if (e.dataTransfer && e.dataTransfer.files.length > 0) {
    const file = e.dataTransfer.files[0];
    if (file && file.type === 'application/pdf') {
      selectedFile.value = file;
    } else {
      toastMessage.value = 'PDF 파일만 업로드 가능합니다.';
      toastType.value = 'error';
      isToastOpen.value = true;
    }
  }
};

// [추가] 클릭 시 파일 탐색기 열기
const triggerFileInput = () => {
  fileInputRef.value?.click();
};
// [ADD] 초기화 (다시 만들기)
const resetAndUpload = async () => {
    generatedQuizzes.value = [] // 목록 초기화 (UI가 업로드 모드로 바뀜)
    // [ADD] 서버에 리셋 알림 전송 (다른 참가자 화면 전환)
    if (roomId.value) {
        wsStore.resetQuiz(roomId.value)
    }
    // 자동 파일 선택창 열기는 제거 (사용자 요청)
};

// [Frontend] 퀴즈 목록 불러오기
const loadQuizzes = async () => {
  try {
    generatedQuizzes.value = await getQuizzes(roomId.value)
  } catch (error) {
    console.error('Failed to load quizzes', error)
  }
}

const applyInitialQuizState = async (quizCount?: number) => {
  if (!quizCount || quizCount <= 0) return
  await loadQuizzes()
  isGenerating.value = false
}

// [Frontend] 퀴즈 모달 열기
const openQuizModal = async () => {
  await loadQuizzes()
  isQuizModalOpen.value = true
}

// [Frontend] 기본 퀴즈 선택 핸들러
const selectBasicQuiz = async (type: string) => {
  if (!roomId.value) return
  
  try {
    await setDefaultQuizzes(roomId.value, type)
    isBasicQuizModalOpen.value = false
    // 퀴즈 목록 갱신 (선택사항, 백엔드에서 브로드캐스트 하므로 자동 갱신될 것임)
  } catch (error) {
    toastMessage.value = '퀴즈 선택 실패: ' + getErrorMessage(error);
    toastType.value = 'error';
    isToastOpen.value = true;
  }
}


const displayUsers = computed(() => {
  const filled = users.value.map(u => ({
    ...u,
    avatar: u.avatar || ''
  }))
  
  const targetSize = Math.max(maxUsers.value || 0, filled.length)
  const emptySlots = Math.max(targetSize - filled.length, 0)

  for (let i = 0; i < emptySlots; i += 1) {
    filled.push({
      id: `empty-${i}`,
      nickname: '',
      avatar: '',
      isReady: false,
      isHost: false,
      isMe: false
    })
  }

  return filled
})

const characters = ['🐱', '🐶', '🦊', '🐰', '🐯', '🐸', '🐨', '🐷'];
const selectedChar = ref('🐱');

watchEffect(() => {
  if (myUser.value?.avatar) {
    selectedChar.value = myUser.value.avatar
  }
})

watch(
  () => wsStore.quizGeneratedNotice,
  async (notice) => {
    if (!notice) return

    // 1. [QUIZ_RESET] 방장이 "다시 만들기" 누름 -> 방장: 업로드 UI, 참가자: 대기 UI
    if (notice.type === 'QUIZ_RESET') {
        generatedQuizzes.value = []
        
        if (myUser.value?.isHost) {
            isGenerating.value = false
        } else {
            isGenerating.value = true
        }
        
        processingText.value = '방장님이 문서를 고르고 있어요'
        wsStore.clearQuizGeneratedNotice()
        return
    }

    // 2. [QUIZ_GENERATING] 실제 생성 시작 -> 모두 대기 UI
    if (notice.type === 'QUIZ_GENERATING') {
      isGenerating.value = true
      generatedQuizzes.value = [] 
      
      // 메시지 구성
      if (notice.fileName) {
          processingText.value = `[${notice.fileName}]`
      } else if (notice.quizType) {
          const found = basicQuizTypes.find(t => t.type === notice.quizType)
          const label = found ? found.label : notice.quizType
          processingText.value = `방장님이 [${label}]를 고르고 있어요`
      } else {
          processingText.value = '방장님이 문서를 고르고 있어요'
      }

      wsStore.clearQuizGeneratedNotice()
      return
    }

    // 3. [QUIZ_GENERATED] 생성 완료
    if (!notice.type || notice.type === 'QUIZ_GENERATED') {
      isGenerating.value = false // 로딩 해제
      
      const countLabel = notice.count && notice.count > 0 ? ` (${notice.count}개)` : ''
      toastMessage.value = `퀴즈 생성이 완료되었습니다!${countLabel}`
      toastType.value = 'success'
      isToastOpen.value = true
      
      // 방장은 이미 API 응답으로 갱신했을 수 있지만 참가자를 위해 재요청
      if (roomId.value) {
        await loadQuizzes()
      }
      wsStore.clearQuizGeneratedNotice()
    }
  }
)

const isCharacterTaken = (char: string) => {
  return users.value.some(user => user.avatar === char && !user.isMe);
};

const selectCharacter = (char: string) => {
  if (myUser.value?.isReady && !myUser.value?.isHost) return;
  if (isCharacterTaken(char)) return;
  selectedChar.value = char;
  
  if (myUser.value && userStore.userId) {
    const avatarId = characters.indexOf(char)
    if (avatarId !== -1) {
      wsStore.setAvatar(roomId.value, userStore.userId, avatarId)
    }
  }
};

const isAllReady = computed(() => {
  const realUsers = users.value.filter(user => user.nickname);
  return realUsers.length > 1 && realUsers.every(user => user.isReady || user.isHost);
});

const isHost = computed(() => roomStore.isHost || myUser.value?.isHost || false)
const startRequested = ref(false)
const isStartDisabled = computed(() =>
  !isAllReady.value || startRequested.value || roomInfo.value.status !== 'WAITING'
)

const toggleReady = () => {
  if (!myUser.value || !userStore.userId) return
  wsStore.setReady(roomId.value, userStore.userId, !myUser.value.isReady)
}

const copyRoomCode = async () => {
  const code = roomInfo.value.inviteCode || roomInfo.value.roomId
  if (!code) return

  try {
    await navigator.clipboard.writeText(code);
    toastMessage.value = '방 코드가 복사되었습니다!';
    toastType.value = 'success';
    isToastOpen.value = true;
  } catch (err) {
    toastMessage.value = '복사에 실패했습니다.';
    toastType.value = 'error';
    isToastOpen.value = true;
  }
};

const handleStartGame = () => {
  if (!isAllReady.value) {
    alert("모든 플레이어가 준비해야 합니다!");
    return;
  }
  if (!userStore.userId) return;
  if (startRequested.value) return;
  startRequested.value = true;
  wsStore.startGame(roomId.value, userStore.userId)
  window.setTimeout(() => {
    if (roomInfo.value.status === 'WAITING') {
      startRequested.value = false;
    }
  }, 2000)
};

watchEffect(() => {
  const status = roomInfo.value.status
  if (!status) return

  // 서버 상태가 STARTING/QUIZ_TIME/...로 바뀌면 게임 화면으로 이동
  if (status !== 'WAITING' && status !== 'MINIGAME') {
    console.log('🎮 Game Started! redirecting to /game...')
    router.push('/game')
  }
})

// [병합 로직] 디자인을 위해 Modal을 띄우는 함수
const leaveRoom = () => {
  showExitModal.value = true; 
};

// [병합 로직] Modal 확인 버튼 클릭 시 실행되는 실제 로직
const handleExitConfirm = () => {
  // 1. 게임 서버에 나감 알림
  if (userStore.userId && roomId.value) {
     wsStore.leaveGame(roomId.value, userStore.userId)
  }
  
  // 2. 약간의 지연 후 클라이언트 정리 및 이동 (안정성 확보)
  setTimeout(() => {
    wsStore.cleanup();
    roomStore.leaveRoom();
    router.push('/');
  }, 150)
  
  showExitModal.value = false;
};

onMounted(async () => {
  if (!roomId.value) return

  if (!userStore.userId) {
    userStore.restoreUser()
  }

  if (!userStore.userId) {
    alert('닉네임을 먼저 설정해주세요.')
    router.push('/')
    return
  }

  try {
    const room = await getRoomById(roomId.value)
    roomStore.joinRoom({
      roomId: room.roomId,
      title: room.title,
      capacity: room.capacity,
      isPrivate: room.isPrivate,
      status: room.status,
      currentCount: room.currentCount,
      quizCount: room.quizCount
    })
    await applyInitialQuizState(room.quizCount)
  } catch (error) {
    alert(getErrorMessage(error))
    router.push('/')
    return
  }

  await wsStore.connect()
  wsStore.subscribeErrors(userStore.userId)
  wsStore.subscribeGameState(roomId.value)
  wsStore.subscribeMiniGame(roomId.value)
  wsStore.joinGame(roomId.value, userStore.userId, userStore.username)
})
</script>

<template>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet">
  
  <div class="min-h-screen bg-[#fff1f2] flex items-center justify-center p-8 font-display text-[#881337] relative overflow-hidden">
    
    <div class="absolute inset-0 pointer-events-none z-0 overflow-hidden">
      <div class="absolute -top-[10%] -right-[10%] w-[800px] h-[800px] rounded-full 
                  bg-gradient-to-br from-[#e11d48]/10 to-[#fda4af]/20 
                  blur-[80px] animate-float">
      </div>
      <div class="absolute -bottom-[10%] -left-[10%] w-[800px] h-[800px] rounded-full 
                  bg-gradient-to-tr from-[#fb923c]/15 to-[#e11d48]/10 
                  blur-[80px] animate-float-delay">
      </div>
    </div>
    
    <div class="w-full max-w-7xl bg-white/60 backdrop-blur-xl rounded-[12px] shadow-[0_20px_60px_-15px_rgba(225,29,72,0.15)] py-12 px-12 border border-white relative z-10">       
      
      <header class="flex justify-between items-center mb-10 px-4">
        <div class="flex items-center gap-6">
          <span class="bg-[#fff1f2] text-[#e11d48] border border-[#fecdd3] px-3 py-1.5 rounded-full text-sm font-bold tracking-wide shadow-sm flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[18px]">
              {{ roomTypeIcon }}
            </span>
            <span>
              {{ roomInfo.isPrivate ? '비밀방' : '공개방' }}
            </span>
          </span>
          <h1 class="text-4xl font-extrabold text-[#881337] tracking-tight drop-shadow-sm">
            {{ roomInfo.title || '대기실' }}
          </h1>
        </div>
        
        <div class="flex items-center gap-2 bg-white px-5 py-2.5 rounded-2xl font-bold text-[#e11d48] shadow-sm border border-[#ffe4e6]">
          <span class="material-symbols-outlined text-[20px]">group</span>
          <span>{{ currentCount }} / {{ maxUsers || '-' }}</span>
        </div>
      </header>

      <div class="grid grid-cols-12 gap-8 h-[600px]">
        
        <main class="col-span-8 bg-white/50 rounded-[12px] border border-[#fecdd3] p-8 shadow-inner">
          <div class="grid grid-cols-2 grid-rows-2 gap-6 h-full">
            <UserCard 
              v-for="user in displayUsers" 
              :key="user.id" 
              :user="user" 
              class="w-full h-full transition-transform hover:-translate-y-1"
            />
          </div>
        </main>

        <aside class="col-span-4 flex flex-col justify-between h-full gap-6">
          
          <div class="bg-white/50 p-8 rounded-[12px] border border-[#fecdd3] flex-1 flex flex-col shadow-sm overflow-hidden">
            <h3 class="text-lg font-bold mb-6 text-[#9f1239] flex items-center gap-2">
              <span class="material-symbols-outlined text-[#e11d48]">face</span>
              캐릭터 선택
            </h3>
            
            <div class="grid grid-cols-4 gap-3 content-start mb-6">
              <button 
                v-for="char in characters" 
                :key="char"
                @click="selectCharacter(char)"
                :disabled="isCharacterTaken(char) || (myUser?.isReady && !myUser?.isHost)"
                class="aspect-square rounded-2xl text-3xl flex items-center justify-center transition-all relative border-2 bg-white"
                :class="[
                  selectedChar === char 
                    ? 'border-[#e11d48] bg-[#fff1f2] shadow-[0_0_0_4px_rgba(225,29,72,0.1)] -translate-y-1' 
                    : 'border-transparent hover:bg-[#fff1f2] hover:scale-105',
                  (isCharacterTaken(char) || (myUser?.isReady && !myUser?.isHost))
                    ? 'opacity-40 cursor-not-allowed grayscale bg-gray-50' 
                    : 'cursor-pointer'
                ]"
              >
                {{ char }}
                <div v-if="isCharacterTaken(char)" class="absolute inset-0 flex items-center justify-center bg-white/50 rounded-2xl">
                  <span class="material-symbols-outlined text-gray-400 text-xl font-bold">lock</span>
                </div>
              </button>
            </div>

            <div class="border-t border-[#fecdd3] pt-6 mt-auto">
              <h3 class="text-lg font-bold text-[#9f1239] mb-4 flex items-center gap-2">
                <span class="material-symbols-outlined text-xl">quiz</span>
                퀴즈 설정
              </h3>

              <div v-if="isHost" class="flex flex-col gap-4">
                  
                  <!-- [CASE 1] 퀴즈가 없을 때: 업로드 UI -->
                  <template v-if="generatedQuizzes.length === 0">
                    <div 
                      @dragover="onDragOver"
                      @dragleave="onDragLeave"
                      @drop="onDrop"
                      @click="triggerFileInput"
                      class="relative w-full h-32 rounded-2xl border-2 border-dashed transition-all duration-300 flex flex-col items-center justify-center cursor-pointer group overflow-hidden"
                      :class="[
                        isDragging 
                          ? 'border-[#e11d48] bg-[#fff1f2] scale-[1.02]' 
                          : selectedFile
                            ? 'border-[#00b894] bg-[#f0fff4]'
                            : 'border-[#fecdd3] bg-white hover:border-[#e11d48] hover:bg-[#fff1f2]'
                      ]"
                    >
                      <input 
                        ref="fileInputRef"
                        type="file" 
                        accept="application/pdf"
                        @change="handleFileChange"
                        class="hidden"
                      />

                      <div v-if="!selectedFile" class="flex flex-col items-center text-center p-4 pointer-events-none">
                        <div class="w-10 h-10 rounded-full bg-[#fff1f2] flex items-center justify-center mb-2 group-hover:scale-110 transition-transform">
                          <span class="material-symbols-outlined text-[#e11d48] text-2xl">cloud_upload</span>
                        </div>
                        <p class="text-sm font-bold text-[#881337]">PDF 드래그 또는 클릭</p>
                        <p class="text-xs text-[#fda4af] mt-1 font-medium">퀴즈로 만들 문서를 올려주세요</p>
                      </div>

                      <div v-else class="flex flex-col items-center text-center p-4 w-full animate-bounce-short">
                        <div class="w-10 h-10 rounded-full bg-[#00b894]/10 flex items-center justify-center mb-2">
                          <span class="material-symbols-outlined text-[#00b894] text-2xl">description</span>
                        </div>
                        <p class="text-sm font-black text-[#00b894] truncate max-w-[90%]">{{ selectedFile.name }}</p>
                        <p class="text-[10px] text-[#00b894]/70 mt-1 font-bold">변경하려면 클릭하세요</p>
                        
                        <button 
                          @click.stop="selectedFile = null" 
                          class="absolute top-2 right-2 text-[#00b894] hover:text-red-500 transition-colors p-1"
                        >
                          <span class="material-symbols-outlined text-lg">close</span>
                        </button>
                      </div>
                    </div>
                    <div class="flex gap-3 mt-4">
                      <button 
                        @click="handleUploadPdf"
                        :disabled="!selectedFile || isGenerating"
                        class="flex-1 h-14 rounded-xl font-bold text-sm transition-all flex items-center justify-center gap-2 shadow-sm border-b-4 active:border-b-0 active:translate-y-1"
                        :class="(!selectedFile || isGenerating) 
                          ? 'bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed' 
                          : 'bg-[#e11d48] text-white border-[#9f1239] hover:bg-[#be123c]'"
                      >
                        <span v-if="isGenerating" class="flex items-center gap-2">
                          <span class="material-symbols-outlined text-xl animate-spin">progress_activity</span>
                          생성 중...
                        </span>
                        <span v-else class="flex items-center gap-2">
                          <span class="material-symbols-outlined text-xl group-hover:scale-110 transition-transform">auto_fix</span>
                          퀴즈 생성
                        </span>
                      </button>
                      
                      <button 
                        @click="isBasicQuizModalOpen = true"
                        class="flex-1 h-14 rounded-2xl bg-white text-[#e11d48] border-2 border-[#fecdd3] border-b-[5px] font-black text-base hover:bg-[#fff1f2] hover:border-[#e11d48] flex items-center justify-center gap-2 active:border-b-2 active:translate-y-[3px] transition-all shadow-sm group"
                      >
                        <span class="material-symbols-outlined text-xl group-hover:scale-110 transition-transform">menu_book</span>
                        기본 퀴즈
                      </button>
                    </div>
                  </template>

                  <!-- [CASE 2] 퀴즈가 있을 때: 완료 UI & 다시하기 -->
                  <div v-else class="flex flex-col gap-4 animate-fade-in">
                    <div class="w-full h-32 rounded-2xl border-2 border-[#00b894] bg-[#f0fff4] flex flex-col items-center justify-center overflow-hidden relative">
                       <div class="flex flex-col items-center animate-bounce-short">
                         <div class="w-10 h-10 rounded-full bg-[#00b894]/10 flex items-center justify-center mb-2">
                           <span class="material-symbols-outlined text-[#00b894] text-2xl">check_circle</span>
                         </div>
                         <p class="text-sm font-black text-[#00b894]">퀴즈 준비 완료!</p>
                         <p class="text-[10px] text-[#00b894]/70 mt-1 font-bold">생성된 문제수: {{ generatedQuizzes.length }}개</p>
                       </div>
                    </div>

                    <div class="flex gap-3">
                       <!-- 다시 만들기 버튼 (확장됨 & 자동열기) -->
                       <button 
                         @click="resetAndUpload" 
                         class="flex-1 h-14 rounded-2xl bg-white border-2 border-gray-200 text-gray-500 hover:text-red-500 hover:border-red-200 hover:bg-red-50 font-bold text-base flex items-center justify-center gap-2 transition-all shadow-sm"
                       >
                         <span class="material-symbols-outlined text-xl">refresh</span>
                         퀴즈 다시 만들기
                       </button>
                    </div>
                  </div>
              </div>

              <div v-else class="flex flex-col gap-4 animate-fade-in">
                  
                  <!-- [CASE 1] 퀴즈 생성 완료 -->
                  <div v-if="generatedQuizzes.length > 0">
                    <div class="w-full h-32 rounded-2xl border-2 border-[#00b894] bg-[#f0fff4] flex flex-col items-center justify-center overflow-hidden relative animate-slide-up">
                       <div class="flex flex-col items-center animate-bounce-short">
                         <div class="w-10 h-10 rounded-full bg-[#00b894]/10 flex items-center justify-center mb-2">
                           <span class="material-symbols-outlined text-[#00b894] text-2xl">check_circle</span>
                         </div>
                         <p class="text-sm font-black text-[#00b894]">퀴즈 준비 완료!</p>
                         <p class="text-[10px] text-[#00b894]/70 mt-1 font-bold">방장님의 시작을 기다려주세요</p>
                       </div>
                    </div>
                    <!-- 참가자는 버튼 없음 -->
                  </div>

                  <!-- [CASE 2] 퀴즈 대기 중 -->
                  <div v-else 
                    class="w-full h-32 rounded-2xl border-2 border-dashed border-[#fecdd3] bg-[#fff1f2]/50 flex flex-col items-center justify-center relative"
                  >
                     <div class="flex flex-col items-center text-center p-4">
                        <div class="w-10 h-10 rounded-full bg-white flex items-center justify-center mb-2 animate-pulse">
                           <span class="material-symbols-outlined text-[#fda4af] text-2xl">hourglass_top</span>
                        </div>
                        <p class="text-sm font-bold text-[#881337]">퀴즈 생성 대기 중...</p>
                        <p class="text-xs text-[#fda4af] mt-1 font-medium">{{ processingText }}</p>
                     </div>
                  </div>
              </div>

            </div>
          </div>
          
          <div class="flex flex-col gap-3 justify-end min-h-[6rem]">
            
            <template v-if="isHost">
              <div v-if="!isAllReady" class="text-[#e11d48] text-sm font-bold text-center animate-pulse bg-[#fff1f2] py-2 rounded-lg">
                <span class="material-symbols-outlined text-sm">warning</span>
                모두 준비 완료가 되어야 시작할 수 있습니다
              </div>
              
              <button 
                @click="handleStartGame"
                :disabled="isStartDisabled"
                class="w-full h-20 rounded-[12px] font-black text-2xl text-white transition-all 
                       flex items-center justify-center gap-3 shadow-lg"
                :class="!isStartDisabled 
                  ? 'bg-gradient-to-r from-[#e11d48] to-[#be123c] shadow-rose-500/30 hover:shadow-[0_0_25px_rgba(225,29,72,0.4)] hover:scale-[1.02] active:scale-[0.98]' 
                  : 'bg-gray-300 cursor-not-allowed shadow-none opacity-50'"
              >
                <span class="material-symbols-outlined text-3xl">play_circle</span>
                GAME START
              </button>
            </template>
            
            <button 
              v-else
              @click="toggleReady"
              class="w-full h-20 rounded-[12px] font-black text-2xl transition-all 
                     active:scale-[0.98] flex flex-col items-center justify-center shadow-lg"
              :class="myUser?.isReady 
                ? 'bg-[#e11d48] text-white shadow-rose-500/30 ring-4 ring-rose-100' 
                : 'bg-white text-[#9f1239] border-2 border-[#e11d48] hover:bg-[#fff1f2]'"
            >
              <span class="material-symbols-outlined text-3xl mb-1 animate-pulse">
                {{ myUser?.isReady ? 'pause' : 'play_arrow' }}
              </span>

              <span>{{ myUser?.isReady ? '준비취소' : '준비하기' }}</span>
            </button>
          </div>
        </aside>
      </div>

      <footer class="mt-10 px-4 flex justify-between items-center border-t border-[#fecdd3] pt-8">
        <div class="text-[#fda4af] text-sm font-bold flex items-center gap-2">
          <span class="material-symbols-outlined">description</span>
          DocQ - PDF 문서 기반 퀴즈 게임
        </div>
        
        <div class="flex gap-4">
          <button @click="copyRoomCode" class="px-6 py-3 rounded-xl font-bold bg-white border border-[#fecdd3] text-[#9f1239] hover:bg-[#fff1f2] transition flex items-center gap-2 shadow-sm">
            <span class="material-symbols-outlined">add_link</span>
            <span class="text-[#e11d48] font-black tracking-wider">{{ roomInfo.inviteCode || roomInfo.roomId }}</span>
          </button>
          
          <button 
            @click="leaveRoom" 
            class="px-8 py-3 rounded-xl font-bold bg-[#fff1f2] text-[#e11d48] border border-transparent hover:bg-[#ffe4e6] transition shadow-sm flex items-center gap-2"
          >
            <span class="material-symbols-outlined">logout</span>
            나가기
          </button>
          <AlertModal 
            :isOpen="isToastOpen" 
            :message="toastMessage" 
            :type="toastType" 
            @close="isToastOpen = false"
          />
          <ExitConfirmModal 
            :isOpen="showExitModal"
            @close="showExitModal = false"
            @confirm="handleExitConfirm"
          />
        </div>
      </footer>
      
    </div>

    <div v-if="isQuizModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-[#881337]/20 backdrop-blur-sm p-4 animate-fade-in" @click.self="isQuizModalOpen = false">
       <div class="bg-white/95 backdrop-blur rounded-[12px] p-8 w-[600px] max-h-[80vh] overflow-hidden flex flex-col shadow-[0_20px_60px_-15px_rgba(225,29,72,0.2)] border border-white animate-slide-up">
          <header class="flex justify-between items-center mb-6 border-b border-[#fecdd3] pb-4">
             <h2 class="text-2xl font-extrabold text-[#881337] flex items-center gap-2">
               <span class="material-symbols-outlined text-[#e11d48]">format_list_bulleted</span>
               생성된 퀴즈 목록
             </h2>
             <button @click="isQuizModalOpen = false" class="text-[#fda4af] hover:text-[#e11d48] transition-colors rounded-full p-1 hover:bg-[#fff1f2]">
               <span class="material-symbols-outlined text-2xl font-bold">close</span>
             </button>
          </header>
          
          <div class="flex-1 overflow-y-auto pr-2 space-y-4 custom-scrollbar">
             <div v-if="generatedQuizzes.length === 0" class="text-center py-12 flex flex-col items-center gap-2">
                 <span class="material-symbols-outlined text-6xl text-[#fecdd3]">quiz</span>
                 <p class="text-[#9f1239] font-bold text-lg">생성된 퀴즈가 없습니다.</p>
                 <span class="text-[#fda4af] text-sm font-medium">방장이 PDF를 업로드하여 퀴즈를 생성해주세요.</span>
             </div>
             
             <div v-for="(quiz, idx) in generatedQuizzes" :key="quiz.quizId" class="border rounded-2xl p-5 bg-white border-[#fecdd3] hover:border-[#e11d48] transition-colors shadow-sm">
                <div class="flex gap-3 mb-3">
                   <span class="bg-[#e11d48] text-white rounded-lg px-2.5 py-0.5 text-sm font-black h-fit shadow-sm">Q{{ idx + 1 }}</span>
                   <p class="font-bold text-[#881337] leading-snug">{{ quiz.question }}</p>
                </div>
                <div class="space-y-2 pl-2">
                   <div v-for="(opt, oIdx) in quiz.options" :key="oIdx" 
                        class="text-sm flex items-center gap-2 transition-all p-2 rounded-lg"
                        :class="oIdx === quiz.answerIndex ? 'bg-[#f0fff4] text-[#166534] font-bold ring-1 ring-[#22c55e]' : 'text-[#64748b] bg-[#f8fafc]'">
                      <span class="w-5 h-5 rounded-full border flex items-center justify-center text-[10px]"
                            :class="oIdx === quiz.answerIndex ? 'border-[#166534] bg-[#22c55e] text-white' : 'border-gray-300 bg-white'">
                           {{ oIdx + 1 }}
                      </span>
                      {{ opt }}
                      <span v-if="oIdx === quiz.answerIndex" class="material-symbols-outlined text-sm">check_circle</span>
                   </div>
                </div>
             </div>
          </div>
          
          <div class="mt-6 pt-4 border-t border-[#fecdd3] flex justify-end">
             <button @click="isQuizModalOpen = false" class="px-6 py-3 bg-[#fff1f2] hover:bg-[#ffe4e6] text-[#e11d48] rounded-xl font-bold transition-colors">
               닫기
             </button>
          </div>
       </div>
   </div>

   <!-- 기본 퀴즈 선택 모달 -->
   <div v-if="isBasicQuizModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-[#881337]/20 backdrop-blur-sm p-4 animate-fade-in" @click.self="isBasicQuizModalOpen = false">
      <div class="bg-white/95 backdrop-blur rounded-[12px] p-8 w-[500px] flex flex-col shadow-[0_20px_60px_-15px_rgba(225,29,72,0.2)] border border-white animate-slide-up">
         <header class="flex justify-between items-center mb-6 border-b border-[#fecdd3] pb-4">
            <h2 class="text-2xl font-extrabold text-[#881337] flex items-center gap-2">
              <span class="material-symbols-outlined text-[#e11d48]">menu_book</span>
              기본 퀴즈 선택
            </h2>
            <button @click="isBasicQuizModalOpen = false" class="text-[#fda4af] hover:text-[#e11d48] transition-colors rounded-full p-1 hover:bg-[#fff1f2]">
              <span class="material-symbols-outlined text-2xl font-bold">close</span>
            </button>
         </header>
         
         <div class="grid gap-4">
            <button 
              v-for="item in basicQuizTypes" 
              :key="item.type"
              @click="selectBasicQuiz(item.type)"
              class="flex items-center gap-4 p-4 rounded-xl border-2 transition-all hover:scale-[1.02] active:scale-[0.98] text-left"
              :class="item.color"
            >
               <div class="w-12 h-12 rounded-full bg-white/50 flex items-center justify-center shadow-sm">
                 <span class="material-symbols-outlined text-2xl">{{ item.icon }}</span>
               </div>
               <div>
                 <p class="font-black text-lg">{{ item.label }}</p>
                 <p class="text-sm opacity-80 font-medium">기본 제공되는 {{ item.label }} 게임하기</p>
               </div>
            </button>
         </div>
      </div>
   </div>
 </div>

</template>

<style scoped>
.font-display {
  font-family: 'Plus Jakarta Sans', sans-serif;
}

.animate-float {
  animation: float 10s infinite ease-in-out;
}

.animate-float-delay {
  animation: float 12s infinite ease-in-out reverse;
}

@keyframes float {
  0% { transform: translate(0, 0); }
  50% { transform: translate(20px, -20px); }
  100% { transform: translate(0, 0); }
}

/* 모달 애니메이션 */
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
.animate-slide-up { animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1); }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { transform: translateY(30px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

/* 스크롤바 커스텀 */
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: #fff1f2;
  border-radius: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #fecdd3;
  border-radius: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #fb7185;
}
</style>