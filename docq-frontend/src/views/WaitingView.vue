<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, watchEffect } from 'vue';
import UserCard from '@/components/lobby/UserCard.vue';
import { useRouter, useRoute } from 'vue-router';
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { useUiStore } from '@/stores/uiStore'
import { getRoomById, getErrorMessage, generateQuiz, getQuizzes, setDefaultQuizzes } from '@/services/api'
import type { QuizResponse } from '@/types/docq-api'
import AlertModal from '@/components/common/AlertModal.vue';
import ConfirmModal from '@/components/common/ConfirmModal.vue';

const router = useRouter();
const route = useRoute();
const roomStore = useRoomStore()
const userStore = useUserStore()
const wsStore = useWebSocketStore()
const uiStore = useUiStore()

const roomId = computed(() => String(route.params.roomId || ''))
const roomInfo = computed(() => roomStore.roomInfo)
const users = computed(() => roomStore.users)
const myUser = computed(() => roomStore.myUser)

const roomTypeIcon = computed(() => (roomInfo.value.isPrivate ? 'lock' : 'public'))
const currentCount = computed(() => users.value.length)
const maxUsers = computed(() => roomInfo.value.capacity || 0)

// [DESIGN] 디자인용 상태
const isToastOpen = ref(false);
const toastMessage = ref('');
const toastType = ref<'success' | 'error'>('success');
const showExitModal = ref(false);

// [QUIZ] 퀴즈 관련 상태
const isGenerating = ref(false)
const selectedFile = ref<File | null>(null);
const generatedQuizzes = ref<QuizResponse[]>([])
const isQuizModalOpen = ref(false)
const isBasicQuizModalOpen = ref(false)
const processingText = ref('방장님이 문서를 고르고 있어요')
const isPdfUploadDisabled = ref(true)

const basicQuizTypes = [
  { type: 'nonsense', label: '넌센스 퀴즈', icon: 'sentiment_very_satisfied', color: 'bg-yellow-100 text-yellow-600 border-yellow-200' },
  { type: 'common', label: '상식 퀴즈', icon: 'school', color: 'bg-blue-100 text-blue-600 border-blue-200' },
  { type: 'movie', label: '영화 명대사 퀴즈', icon: 'movie', color: 'bg-indigo-100 text-indigo-600 border-indigo-200' },
  { type: 'sports', label: '스포츠 퀴즈', icon: 'sports_soccer', color: 'bg-green-100 text-green-600 border-green-200' },
  { type: 'mixed', label: '랜덤 퀴즈', icon: 'shuffle', color: 'bg-purple-100 text-purple-600 border-purple-200' },
]

// [FILE] 파일 핸들러
const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    selectedFile.value = target.files[0] || null
  }
}

const openInfoModal = (message: string, title = '알림') => {
  uiStore.openConfirm({
    title, message, confirmLabel: '확인', icon: 'info', variant: 'primary', showCancel: false
  })
}

const openErrorModal = (message: string, title = '오류') => {
  uiStore.openConfirm({
    title, message, confirmLabel: '확인', icon: 'warning', variant: 'danger', showCancel: false
  })
}

const showPdfMaintenance = () => {
  isDragging.value = false
  openInfoModal('점검중입니다.')
}

const handleUploadPdf = async () => {
  if (!selectedFile.value || !roomId.value) return
  isGenerating.value = true
  try {
    await generateQuiz(roomId.value, selectedFile.value)
    await loadQuizzes()
    selectedFile.value = null
  } catch (error) {
    toastMessage.value = '퀴즈 생성 실패: ' + getErrorMessage(error);
    toastType.value = 'error';
    isToastOpen.value = true;
  } finally {
    isGenerating.value = false
  }
}

const isDragging = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

const onDragOver = (e: DragEvent) => { e.preventDefault(); isDragging.value = true; };
const onDragLeave = (e: DragEvent) => { e.preventDefault(); isDragging.value = false; };
const onDrop = (e: DragEvent) => {
  e.preventDefault();
  isDragging.value = false;
  if (e.dataTransfer && e.dataTransfer.files.length > 0) {
    const file = e.dataTransfer.files[0];
    if (file && file.type === 'application/pdf') { selectedFile.value = file; }
    else { toastMessage.value = 'PDF 파일만 가능합니다.'; toastType.value = 'error'; isToastOpen.value = true; }
  }
};

const triggerFileInput = () => { fileInputRef.value?.click(); };

const resetAndUpload = async () => {
    generatedQuizzes.value = []
    if (roomId.value) { wsStore.resetQuiz(roomId.value) }
};

const loadQuizzes = async () => {
  try { generatedQuizzes.value = await getQuizzes(roomId.value) }
  catch (error) { console.error('Failed to load quizzes', error) }
}

const applyInitialQuizState = async (quizCount?: number) => {
  if (!quizCount || quizCount <= 0) return
  await loadQuizzes()
  isGenerating.value = false
}

const selectBasicQuiz = async (type: string) => {
  if (!roomId.value) return
  try {
    await setDefaultQuizzes(roomId.value, type)
    isBasicQuizModalOpen.value = false
  } catch (error) {
    toastMessage.value = '퀴즈 선택 실패: ' + getErrorMessage(error);
    toastType.value = 'error';
    isToastOpen.value = true;
  }
}

const displayUsers = computed(() => {
  const filled = users.value.map(u => ({ ...u, avatar: u.avatar || '' }))
  const targetSize = Math.max(maxUsers.value || 0, filled.length)
  const emptySlots = Math.max(targetSize - filled.length, 0)
  for (let i = 0; i < emptySlots; i += 1) {
    filled.push({ id: `empty-${i}`, nickname: '', avatar: '', isReady: false, isHost: false, isMe: false })
  }
  return filled
})

const characters = ['🐔', '🐶', '🐷', '🐑'];
const selectedChar = ref('❓');

watchEffect(() => {
  if (!myUser.value || !userStore.userId) return;
  if (myUser.value.avatar && characters.includes(myUser.value.avatar)) {
    selectedChar.value = myUser.value.avatar;
  }
});

watch(() => wsStore.quizGeneratedNotice, async (notice) => {
    if (!notice) return
    if (notice.type === 'QUIZ_RESET') {
        generatedQuizzes.value = [];
        isGenerating.value = !myUser.value?.isHost;
        processingText.value = '방장님이 문서를 고르고 있어요';
        wsStore.clearQuizGeneratedNotice();
        return;
    }
    if (notice.type === 'QUIZ_GENERATING') {
      isGenerating.value = true;
      generatedQuizzes.value = []; 
      if (notice.fileName) { processingText.value = `[${notice.fileName}]`; }
      else if (notice.quizType) {
          const found = basicQuizTypes.find(t => t.type === notice.quizType);
          processingText.value = `방장님이 [${found ? found.label : notice.quizType}]를 고르고 있어요`;
      }
      wsStore.clearQuizGeneratedNotice();
      return;
    }
    if (!notice.type || notice.type === 'QUIZ_GENERATED') {
      isGenerating.value = false;
      toastMessage.value = '퀴즈 생성이 완료되었습니다!';
      toastType.value = 'success';
      isToastOpen.value = true;
      if (roomId.value) { await loadQuizzes(); }
      wsStore.clearQuizGeneratedNotice();
    }
})

const isCharacterTaken = (char: string) => users.value.some(user => user.avatar === char && !user.isMe);

const selectCharacter = (char: string) => {
  if (myUser.value?.isReady && !myUser.value?.isHost) return;
  if (isCharacterTaken(char)) return;
  selectedChar.value = char;
  if (myUser.value && userStore.userId) {
    const avatarId = characters.indexOf(char)
    if (avatarId !== -1) { wsStore.setAvatar(roomId.value, userStore.userId, avatarId) }
  }
};

const isAllReady = computed(() => {
  const realUsers = users.value.filter(user => user.nickname);
  return realUsers.length > 1 && realUsers.every(user => user.isReady || user.isHost);
});

const isHost = computed(() => roomStore.isHost || myUser.value?.isHost || false)
const startRequested = ref(false)
const isStartDisabled = computed(() => !isAllReady.value || startRequested.value || roomInfo.value.status !== 'WAITING')

const toggleReady = () => {
  if (!myUser.value || !userStore.userId) return
  if (myUser.value?.avatar === '❓' || !characters.includes(myUser.value?.avatar || '')) {
    toastMessage.value = '캐릭터를 먼저 선택해주세요!'; toastType.value = 'error'; isToastOpen.value = true;
    return
  }
  wsStore.setReady(roomId.value, userStore.userId, !myUser.value?.isReady)
}

const copyRoomCode = async () => {
  const code = roomInfo.value.inviteCode || roomInfo.value.roomId
  if (!code) return
  try {
    await navigator.clipboard.writeText(String(code));
    toastMessage.value = '방 코드가 복사되었습니다!'; toastType.value = 'success'; isToastOpen.value = true;
  } catch (err) {
    toastMessage.value = '복사에 실패했습니다.'; toastType.value = 'error'; isToastOpen.value = true;
  }
};

const handleStartGame = () => {
  if (!myUser.value) return;
  if (myUser.value?.avatar === '❓' || !characters.includes(myUser.value?.avatar || '')) {
    toastMessage.value = '캐릭터를 먼저 선택해주세요!'; toastType.value = 'error'; isToastOpen.value = true;
    return
  }
  if (generatedQuizzes.value.length === 0) { openInfoModal('퀴즈를 선택해주세요.'); return; }
  if (!isAllReady.value) { openInfoModal('모두 준비해야 합니다.'); return; }
  if (!userStore.userId || startRequested.value) return;
  startRequested.value = true;
  wsStore.startGame(roomId.value, userStore.userId)
  setTimeout(() => { if (roomInfo.value.status === 'WAITING') startRequested.value = false; }, 2000)
};

watchEffect(() => {
  if (roomInfo.value.status && roomInfo.value.status !== 'WAITING' && roomInfo.value.status !== 'MINIGAME') {
    router.push('/game')
  }
})

const leaveRoom = () => { showExitModal.value = true; };
const handleExitConfirm = () => {
  if (userStore.userId && roomId.value) wsStore.leaveGame(roomId.value, userStore.userId)
  setTimeout(() => { wsStore.cleanup(); roomStore.leaveRoom(); router.push('/'); }, 150)
  showExitModal.value = false;
};

// [DESIGN] 구름 생성 로직
const clouds = ref<{ id: number; typeId: number; style: any }[]>([]);
const generateClouds = () => {
  const cloudCount = 12;
  const newClouds = [];
  for (let i = 0; i < cloudCount; i++) {
    const sizeScale = 0.6 + Math.random() * 0.8;
    const duration = 25 + Math.random() * 35;
    const delay = -(Math.random() * duration);
    newClouds.push({ id: i, typeId: Math.floor(Math.random() * 3) + 1,
      style: { top: `${Math.random() * 20}%`, opacity: 0.7 + Math.random() * 0.3, transform: `scale(${sizeScale})`, animationDuration: `${duration}s`, animationDelay: `${delay}s` }
    });
  }
  clouds.value = newClouds;
};

onMounted(async () => {
  if (!roomId.value) return
  if (!userStore.userId) userStore.restoreUser()
  if (!userStore.userId) { openInfoModal('닉네임을 먼저 설정해주세요.'); router.push('/'); return; }
  try {
    const room = await getRoomById(roomId.value)
    roomStore.joinRoom({ roomId: room.roomId, title: room.title, capacity: room.capacity, isPrivate: room.isPrivate, status: room.status, currentCount: room.currentCount, quizCount: room.quizCount })
    await applyInitialQuizState(room.quizCount)
  } catch (error) { openErrorModal(getErrorMessage(error)); router.push('/'); return; }
  await wsStore.connect()
  wsStore.subscribeErrors(userStore.userId); wsStore.subscribeGameState(roomId.value); wsStore.subscribeMiniGame(roomId.value);
  wsStore.joinGame(roomId.value, userStore.userId, userStore.username)
  generateClouds();
})
</script>

<template>
  <div class="page-container cartoon-theme">
    <div class="cartoon-bg">
      <div class="sky"></div>
      <div v-for="cloud in clouds" :key="cloud.id" :class="['cloud', `cloud-type-${cloud.typeId}`]" :style="cloud.style"></div>
      <div class="ground-back"></div>
      <div class="ground-front"></div>
      <div class="decoration tree-1"></div>
      <div class="decoration tree-2"></div>
    </div>

    <div
  class="w-full max-w-[1400px] h-[88vh] md:h-[90vh] wood-panel flex flex-col
         px-6 md:px-10 py-6 md:py-8 relative z-10 animate-fade-in overflow-hidden"
>

      <header class="flex justify-between items-center mb-8 px-4">
        <div class="flex items-center gap-6">
          <div class="icon-circle h-14 w-14">
            <span class="leaf-icon material-symbols-outlined text-3xl">{{ roomTypeIcon }}</span>
          </div>
          <div>
            <span class="text-xs font-bold text-wood-dark opacity-75 uppercase tracking-wider">{{ roomInfo.isPrivate ? 'Private' : 'Public' }} Room</span>
            <h1 class="text-2xl md:text-3xl lg:text-4xl font-black text-wood-text drop-shadow-sm tracking-tight leading-none uppercase">
              {{ roomInfo.title || '대기실' }}
            </h1>
          </div>
        </div>
        
        <div class="flex items-center gap-3 bg-white/60 border-4 border-wood-border px-6 py-3 rounded-2xl font-black text-2xl text-wood-dark shadow-sm">
          <span class="material-symbols-outlined text-[24px]">group</span>
          <span>{{ currentCount }} / {{ maxUsers || '-' }}</span>
        </div>
      </header>

      <div class="grid grid-cols-12 gap-6 md:gap-8 flex-1 min-h-0">
        <main class="col-span-8 bg-white/40 rounded-[32px] md:rounded-[40px] border-4 border-wood-border p-5 md:p-7 shadow-inner">
          <div class="grid grid-cols-2 grid-rows-2 gap-5 md:gap-7 h-full">
            <UserCard v-for="user in displayUsers" :key="user.id" :user="user" class="w-full h-full transform transition-transform hover:scale-[1.02]" />
          </div>
        </main>

        <aside class="col-span-4 flex flex-col gap-8">
          <div class="bg-white/70 p-7 rounded-[40px] border-4 border-wood-border flex flex-col shadow-sm">
            <h3 class="text-xl font-black mb-4 text-wood-dark flex items-center gap-2">
              <span class="material-symbols-outlined text-leaf-green text-2xl">face</span>
              캐릭터 선택
            </h3>
            <div class="grid grid-cols-4 gap-4">
              <button v-for="char in characters" :key="char" @click="selectCharacter(char)"
                :disabled="isCharacterTaken(char) || (myUser?.isReady && !myUser?.isHost)"
                class="aspect-square rounded-2xl text-4xl flex items-center justify-center transition-all relative border-4 bg-white"
                :class="[ selectedChar === char ? 'border-leaf-green bg-green-50 -translate-y-1 shadow-[0_4px_0_0_rgba(85,139,47,1)]' : 'border-wood-light hover:border-leaf-green hover:bg-green-50 shadow-[0_2px_0_0_rgba(141,110,99,0.3)]' ]">
                {{ char }}
                <div v-if="isCharacterTaken(char)" class="absolute inset-0 flex items-center justify-center bg-gray-500/10 rounded-2xl">
                  <span class="material-symbols-outlined text-gray-400 text-2xl font-black">lock</span>
                </div>
              </button>
            </div>
          </div>

          <div class="bg-white/70 p-7 rounded-[40px] border-4 border-wood-border flex-1 flex flex-col shadow-sm">
            <h3 class="text-xl font-black text-wood-dark mb-4 flex items-center gap-2">
              <span class="material-symbols-outlined text-leaf-green text-2xl">quiz</span>
              퀴즈 설정
            </h3>

            <div v-if="isHost" class="flex flex-col gap-6">
              <template v-if="generatedQuizzes.length === 0">
                <template v-if="isPdfUploadDisabled">
                  <div @click="showPdfMaintenance" class="relative w-full h-32 rounded-3xl border-4 border-dashed border-wood-light bg-white/40 flex flex-col items-center justify-center text-center cursor-not-allowed hover:bg-white/60 transition-all">
                    <div class="w-12 h-12 rounded-full bg-white border-4 border-wood-border flex items-center justify-center mb-2">
                      <span class="material-symbols-outlined text-wood-dark text-3xl">cloud_upload</span>
                    </div>
                    <p class="text-xl font-black text-wood-dark">PDF 업로드</p>
                    <p class="text-sm text-wood-dark/60 font-bold opacity-80 mt-1">🚧 시스템 점검중</p>
                  </div>
                  <button @click="isBasicQuizModalOpen = true" class="cartoon-btn secondary h-16 text-xl font-black">
                    기본 퀴즈 선택
                  </button>
                </template>
              </template>

              <div v-else class="flex flex-col gap-4">
                <div class="w-full h-32 rounded-3xl border-4 border-leaf-green bg-green-50 flex flex-col items-center justify-center shadow-inner">
                   <div class="w-12 h-12 rounded-full bg-leaf-green flex items-center justify-center mb-2 shadow-md">
                     <span class="material-symbols-outlined text-white text-3xl font-black">check</span>
                   </div>
                   <p class="text-xl font-black text-leaf-dark">준비 완료!</p>
                   <p class="text-sm text-leaf-dark/70 font-bold">{{ generatedQuizzes.length }}개의 문제</p>
                </div>
                <button @click="resetAndUpload" class="cartoon-btn secondary h-16 text-xl">
                  <span class="material-symbols-outlined text-2xl">refresh</span>
                  퀴즈 다시 만들기
                </button>
              </div>
            </div>

            <div v-else class="flex flex-col gap-4">
              <div v-if="generatedQuizzes.length > 0" class="w-full h-32 rounded-3xl border-4 border-leaf-green bg-green-50 flex flex-col items-center justify-center shadow-inner">
                 <div class="w-12 h-12 rounded-full bg-leaf-green flex items-center justify-center mb-2">
                   <span class="material-symbols-outlined text-white text-3xl font-black">check</span>
                 </div>
                 <p class="text-xl font-black text-leaf-dark">준비 완료!</p>
                 <p class="text-sm text-leaf-dark/70 font-bold">방장님의 시작 대기중</p>
              </div>
              <div v-else class="w-full h-32 rounded-3xl border-4 border-dashed border-wood-light bg-white/40 flex flex-col items-center justify-center text-center">
                 <div class="w-12 h-12 rounded-full bg-white border-4 border-wood-border flex items-center justify-center mb-2 animate-bounce">
                    <span class="material-symbols-outlined text-wood-dark text-3xl">pending</span>
                 </div>
                 <p class="text-xl font-black text-wood-dark">퀴즈 생성중...</p>
                 <p class="text-xs text-wood-dark/60 font-bold mt-1 px-4 leading-tight">{{ processingText }}</p>
              </div>
            </div>
          </div>

          <div class="flex flex-col gap-4 mt-auto">
            <template v-if="isHost">
              <div v-if="!isAllReady" class="text-red-500 text-sm font-black text-center py-3 px-4 bg-red-50 border-4 border-red-200 rounded-2xl animate-pulse">
                플레이어가 더 필요하거나 모두 준비 완료여야 합니다!
              </div>
              <button @click="handleStartGame" :disabled="isStartDisabled" class="cartoon-btn primary h-20 text-3xl font-black" :class="{ 'opacity-50 grayscale cursor-not-allowed': isStartDisabled }">
                GAME START
              </button>
            </template>
            <button v-else @click="toggleReady" class="cartoon-btn h-16 md:h-20 text-2xl md:text-3xl font-black" :class="myUser?.isReady ? 'secondary' : 'primary'">
              {{ myUser?.isReady ? '준비취소' : '준비하기' }}
            </button>
          </div>
        </aside>
      </div>

      <footer class="mt-10 flex justify-between items-end border-t-4 border-wood-border/20 pt-8 px-4">
        <div class="flex flex-col gap-1">
          <h2 class="text-wood-text text-2xl font-black opacity-90 tracking-tighter">DOCQ</h2>
          <p class="text-wood-dark font-bold text-xs opacity-60 uppercase tracking-widest">Interactive PDF Quiz Game</p>
        </div>
        <div class="flex gap-4">
          <button @click="copyRoomCode" class="cartoon-btn secondary w-auto px-8 h-12 text-xl font-black">
            <span class="material-symbols-outlined text-2xl">share</span>
            INVITE CODE
          </button>
          <button @click="leaveRoom" class="cartoon-btn secondary w-auto px-8 h-12 text-xl font-black bg-orange-500 box-shadow-orange">
            <span class="material-symbols-outlined text-2xl">logout</span>
            LEAVE
          </button>
        </div>
      </footer>
    </div>

    <!-- Modals -->
    <AlertModal :isOpen="isToastOpen" :message="toastMessage" :type="toastType" @close="isToastOpen = false" />
    <ConfirmModal :isOpen="showExitModal" title="방 나가기" message="정말 대기실에서 나가시겠습니까?" confirmLabel="나가기" icon="logout" variant="danger" @close="showExitModal = false" @confirm="handleExitConfirm" />
    
    <!-- 기본 퀴즈 선택 모달 -->
    <transition name="intro-fade">
      <div v-if="isBasicQuizModalOpen" class="fixed inset-0 bg-black/60 backdrop-blur-sm z-[100] flex items-center justify-center p-6" @click.self="isBasicQuizModalOpen = false">
        <div class="w-full max-w-2xl bg-[#fff8e1] border-[6px] border-wood-border rounded-[40px] p-10 flex flex-col gap-8 shadow-2xl">
          <div class="text-center">
            <h2 class="text-4xl font-black text-wood-text mb-2">기본 퀴즈 테마</h2>
            <p class="text-wood-dark font-bold opacity-70">준비된 다양한 퀴즈 테마를 즐겨보세요!</p>
          </div>
          <div class="grid grid-cols-2 gap-6">
            <button v-for="item in basicQuizTypes" :key="item.type" @click="selectBasicQuiz(item.type)" class="group p-6 rounded-3xl border-4 border-wood-border bg-white hover:bg-green-50 hover:border-leaf-green transition-all text-left flex items-center gap-6 active:translate-y-1">
              <div class="w-16 h-16 rounded-2xl flex items-center justify-center text-4xl group-hover:scale-110 transition-transform" :class="item.color">
                <span class="material-symbols-outlined text-4xl">{{ item.icon }}</span>
              </div>
              <div>
                <p class="text-2xl font-black text-wood-dark">{{ item.label }}</p>
                <p class="text-sm font-bold opacity-60">재미있는 {{ item.label }} 시작하기</p>
              </div>
            </button>
          </div>
          <button @click="isBasicQuizModalOpen = false" class="cartoon-btn secondary h-16 mt-4">닫기</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&family=Nanum+Pen+Script&family=Bungee&display=swap');

.cartoon-theme {
  --text-brown: #5d4037; --text-light: #efebe9; --wood-light: #d7ccc8; --wood-medium: #bcaaa4; --wood-dark: #8d6e63;
  --wood-border: #5d4037; --leaf-green: #7cb342; --leaf-dark: #558b2f; --gem-yellow: #ffca28; --font-family: 'Jua', sans-serif;
}

.text-wood-dark { color: var(--wood-dark); }
.text-wood-text { color: var(--text-brown); }
.text-leaf-green { color: var(--leaf-green); }
.text-leaf-dark { color: var(--leaf-dark); }
.bg-leaf-green { background-color: var(--leaf-green); }
.bg-wood-light { background-color: var(--wood-light); }
.border-wood-border { border-color: var(--wood-border); }
.border-wood-light { border-color: var(--wood-light); }
.border-leaf-green { border-color: var(--leaf-green); }

.page-container {
  position: relative; width: 100vw; height: 100vh; overflow: hidden;
  background-color: #81d4fa; color: var(--text-brown); font-family: var(--font-family);
  display: flex; justify-content: center; align-items: center;
}

/* Background */
.cartoon-bg { position: absolute; inset: 0; z-index: 0; background: linear-gradient(to bottom, #4fc3f7 0%, #b3e5fc 100%); }
.sky { position: absolute; inset: 0; }
.ground-back { position: absolute; bottom: 0; width: 100%; height: 35%; background: #9ccc65; transform: skewY(-2deg); transform-origin: bottom right; }
.ground-front { position: absolute; bottom: -50px; width: 100%; height: 30%; background: #c5e1a5; border-top: 10px solid #aed581; border-radius: 50% 50% 0 0 / 10% 10% 0 0; }

/* Wood Panel */
.wood-panel {
  background: #fff8e1; border: 10px solid var(--wood-border); border-radius: 60px;
  box-shadow: 0 30px 0 rgba(93, 64, 55, 0.2), 0 50px 80px rgba(0,0,0,0.3);
}

.wood-text { text-shadow: 4px 4px 0px rgba(255,255,255,1), 8px 8px 0px rgba(0,0,0,0.05); color: var(--text-brown); }

/* Buttons */
.cartoon-btn {
  width: 100%; border: none; border-radius: 30px;
  font-family: 'Jua', sans-serif; color: white;
  cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 12px;
  transition: all 0.1s ease; position: relative; top: 0;
}
.cartoon-btn.primary { background: #7cb342; box-shadow: 0 10px 0 #558b2f; }
.cartoon-btn.primary:hover { background: #8bc34a; }
.cartoon-btn.primary:active { top: 8px; box-shadow: 0 2px 0 #558b2f; }

.cartoon-btn.secondary { background: #ff7043; box-shadow: 0 10px 0 #d84315; }
.cartoon-btn.secondary:hover { background: #ff8a65; }
.cartoon-btn.secondary:active { top: 8px; box-shadow: 0 2px 0 #d84315; }

.box-shadow-orange { box-shadow: 0 10px 0 #e65100 !important; }

/* Icon Circle */
.icon-circle {
  width: 72px; height: 72px; background: var(--leaf-green); border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  border: 4px solid var(--leaf-dark); color: white;
  box-shadow: inset 0 -6px 0 rgba(0,0,0,0.2), 0 6px 12px rgba(0,0,0,0.1);
}
.leaf-icon { font-size: 36px; }

/* Clouds */
.cloud { position: absolute; background: #fff; left: 110%; animation: moveClouds linear infinite; }
.cloud::after, .cloud::before { content: ''; position: absolute; background: #fff; border-radius: 50%; }
.cloud-type-1 { width: 150px; height: 50px; border-radius: 30px; }
.cloud-type-1::after { width: 60px; height: 60px; top: -30px; left: 20px; }
.cloud-type-1::before { width: 50px; height: 50px; top: -20px; left: 70px; }
.cloud-type-2 { width: 180px; height: 45px; border-radius: 60px; }
.cloud-type-2::after { width: 55px; height: 40px; top: -20px; left: 30px; border-radius: 50% 50% 0 0; }
.cloud-type-2::before { width: 70px; height: 50px; top: -25px; left: 75px; border-radius: 50% 50% 0 0; }
.cloud-type-3 { width: 120px; height: 40px; border-radius: 20px; box-shadow: 40px -15px 0 8px #fff; }
.cloud-type-3::after { width: 80px; height: 80px; top: -40px; left: 15px; }
.cloud-type-3::before { width: 70px; height: 70px; top: -25px; left: 65px; }

@keyframes moveClouds { 0% { left: 110%; } 100% { left: -30%; } }

/* Utils */
.animate-fade-in { animation: fadeIn 0.8s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }

.intro-fade-enter-active, .intro-fade-leave-active { transition: all 0.4s ease; }
.intro-fade-enter-from, .intro-fade-leave-to { opacity: 0; transform: scale(0.9); }

/* Scrollbar */
::-webkit-scrollbar { width: 12px; }
::-webkit-scrollbar-track { background: rgba(93, 64, 55, 0.05); border-radius: 10px; }
::-webkit-scrollbar-thumb { background: var(--wood-light); border: 3px solid #fff8e1; border-radius: 10px; }
::-webkit-scrollbar-thumb:hover { background: var(--wood-medium); }
</style>