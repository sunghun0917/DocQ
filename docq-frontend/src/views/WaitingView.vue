<script setup lang="ts">
import { ref, computed, watch, onMounted, watchEffect } from 'vue';
import { useRouter, useRoute } from 'vue-router';
// [중요] 3D 뷰어 직접 임포트
import AvatarViewer from '@/components/common/AvatarViewer.vue';

// Stores & API
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { useUiStore } from '@/stores/uiStore'
import { getRoomById, getErrorMessage, generateQuiz, getQuizzes, setDefaultQuizzes } from '@/services/api'
import type { QuizResponse } from '@/types/docq-api'
import AlertModal from '@/components/common/AlertModal.vue';
import ConfirmModal from '@/components/common/ConfirmModal.vue';

// --- 1. 기본 설정 및 스토어 ---
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
const maxUsers = computed(() => roomInfo.value.capacity || 4)

// --- 2. 캐릭터 매핑 로직 ---
const characters = ['🐔', '🐶', '🐷', '🐑'];
const selectedChar = ref('❓');

const getAvatarIndex = (avatarVal: any) => {
  if (avatarVal === undefined || avatarVal === null || avatarVal === '') return -1;
  if (typeof avatarVal === 'string') {
    const num = Number(avatarVal);
    if (!isNaN(num) && num >= 0 && num < characters.length) return num;
    const idx = characters.indexOf(avatarVal);
    return idx; // -1이면 미선택(❓ 등), 0~3이면 선택된 캐릭터
  }
  const n = Number(avatarVal);
  return Number.isFinite(n) && n >= 0 && n < characters.length ? n : -1;
};

watchEffect(() => {
  if (!myUser.value || !userStore.playerId) return;
  const idx = getAvatarIndex(myUser.value.avatar);
  if (idx >= 0 && idx < characters.length) {
    selectedChar.value = characters[idx]!;
  }
});

// --- 3. 화면 표시용 유저 리스트 ---
const displayUsers = computed(() => {
  const max = maxUsers.value || 4;
  const slots = [];

  for (let i = 0; i < max; i++) {
    if (i < users.value.length) {
      const u = users.value[i]!;
      slots.push({
        ...u,
        isEmpty: false,
        avatarIndex: getAvatarIndex(u.avatar)
      });
    } else {
      slots.push({
        id: `empty-${i}`,
        nickname: 'WAITING',
        isEmpty: true,
        isReady: false,
        isHost: false,
        isMe: false,
        avatar: '',
        avatarIndex: -1
      });
    }
  }
  return slots;
});

// --- 4. 상태 변수들 ---
const isToastOpen = ref(false);
const toastMessage = ref('');
const toastType = ref<'success' | 'error'>('success');
const showExitModal = ref(false);

const isGenerating = ref(false)
const selectedFile = ref<File | null>(null);
const generatedQuizzes = ref<QuizResponse[]>([])
const isBasicQuizModalOpen = ref(false)
const processingText = ref('방장님이 문서를 고르고 있어요')
const isPdfUploadDisabled = ref(false)

const basicQuizTypes = [
  { type: 'nonsense', label: '넌센스', icon: 'sentiment_very_satisfied', color: 'bg-yellow-100 text-yellow-700' },
  { type: 'common', label: '상식', icon: 'school', color: 'bg-blue-100 text-blue-700' },
  { type: 'movie', label: '영화', icon: 'movie', color: 'bg-indigo-100 text-indigo-700' },
  { type: 'sports', label: '스포츠', icon: 'sports_soccer', color: 'bg-green-100 text-green-700' },
  { type: 'mixed', label: '랜덤', icon: 'shuffle', color: 'bg-purple-100 text-purple-700' },
];

// --- 5. 동작 함수들 ---

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    selectedFile.value = target.files[0] || null
    handleUploadPdf()
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
    if (file && file.type === 'application/pdf') {
      selectedFile.value = file;
      handleUploadPdf();
    }
    else {
      toastMessage.value = 'PDF 파일만 가능합니다.';
      toastType.value = 'error';
      isToastOpen.value = true;
    }
  }
};

const triggerFileInput = () => { fileInputRef.value?.click(); };

const handleUploadPdf = async () => {
  if (!selectedFile.value || !roomId.value) return
  isGenerating.value = true
  processingText.value = `[${selectedFile.value.name}] 분석 중...`

  try {
    await generateQuiz(roomId.value, selectedFile.value)
  } catch (error) {
    toastMessage.value = '퀴즈 생성 실패: ' + getErrorMessage(error);
    toastType.value = 'error';
    isToastOpen.value = true;
    isGenerating.value = false;
  }
}

const resetAndUpload = async () => {
  generatedQuizzes.value = []
  selectedFile.value = null
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

watch(() => wsStore.quizGeneratedNotice, async (notice) => {
  if (!notice) return
  if (notice.type === 'QUIZ_RESET') {
    generatedQuizzes.value = [];
    selectedFile.value = null;
    isGenerating.value = false;
    processingText.value = '방장님이 문서를 고르고 있어요';
    wsStore.clearQuizGeneratedNotice();
    return;
  }
  if (notice.type === 'QUIZ_GENERATING') {
    isGenerating.value = true;
    generatedQuizzes.value = [];
    if (notice.fileName) { processingText.value = `[${notice.fileName}] 분석 중...`; }
    else if (notice.quizType) {
      const found = basicQuizTypes.find(t => t.type === notice.quizType);
      processingText.value = `[${found ? found.label : notice.quizType}] 준비 중...`;
    }
    wsStore.clearQuizGeneratedNotice();
    return;
  }
  if (!notice.type || notice.type === 'QUIZ_GENERATED') {
    isGenerating.value = false;
    toastMessage.value = '퀴즈 준비 완료!';
    toastType.value = 'success';
    isToastOpen.value = true;
    if (roomId.value) { await loadQuizzes(); }
    wsStore.clearQuizGeneratedNotice();
  }
})

const isCharacterTaken = (charEmoji: string) => {
  const targetIdx = characters.indexOf(charEmoji);
  return users.value.some(user => {
    if (user.isMe) return false;
    const userIdx = getAvatarIndex(user.avatar);
    return userIdx === targetIdx;
  });
};

const selectCharacter = (charEmoji: string) => {
  if (myUser.value?.isReady && !myUser.value?.isHost) return;
  if (isCharacterTaken(charEmoji)) {
    toastMessage.value = '이미 선택된 캐릭터입니다!';
    toastType.value = 'error'; isToastOpen.value = true; return;
  }
  selectedChar.value = charEmoji;
  if (myUser.value && userStore.playerId) {
    const avatarId = characters.indexOf(charEmoji);
    if (avatarId !== -1) wsStore.setAvatar(roomId.value, userStore.playerId, avatarId);
  }
};

const isAllReady = computed(() => {
  const realUsers = users.value.filter(user => user.nickname);
  return realUsers.length > 1 && realUsers.every(user => user.isReady || user.isHost);
})

const isHost = computed(() => roomStore.isHost || myUser.value?.isHost || false)
const startRequested = ref(false)

const toggleReady = () => {
  if (!myUser.value || !userStore.playerId) return
  const currentIdx = getAvatarIndex(myUser.value.avatar);
  if (currentIdx === -1) {
    toastMessage.value = '캐릭터를 먼저 설정해주세요!'; toastType.value = 'error'; isToastOpen.value = true;
    return
  }
  wsStore.setReady(roomId.value, userStore.playerId, !myUser.value?.isReady)
}

const copyRoomCode = async () => {
  const code = roomInfo.value.inviteCode || roomInfo.value.roomId
  if (!code) return
  try {
    await navigator.clipboard.writeText(String(code));
    toastMessage.value = '복사되었습니다!'; toastType.value = 'success'; isToastOpen.value = true;
  } catch (err) { /* ignore */ }
};

const handleStartGame = () => {
  if (!myUser.value) return;
  if (getAvatarIndex(myUser.value.avatar) === -1) {
    toastMessage.value = '캐릭터를 먼저 선택해주세요!'; toastType.value = 'error'; isToastOpen.value = true;
    return;
  }
  if (generatedQuizzes.value.length === 0) {
    toastMessage.value = '퀴즈를 먼저 선택해주세요!'; toastType.value = 'error'; isToastOpen.value = true; return;
  }
  if (!isAllReady.value) {
    toastMessage.value = '모두 준비되어야 합니다!'; toastType.value = 'error'; isToastOpen.value = true; return;
  }
  if (!userStore.playerId || startRequested.value) return;
  startRequested.value = true;
  wsStore.startGame(roomId.value, userStore.playerId)
  setTimeout(() => { if (roomInfo.value.status === 'WAITING') startRequested.value = false; }, 2000)
};

watchEffect(() => {
  if (roomInfo.value.status && roomInfo.value.status !== 'WAITING' && roomInfo.value.status !== 'MINIGAME') {
    router.push('/game')
  }
})

const leaveRoom = () => { showExitModal.value = true; };
const handleExitConfirm = () => {
  if (userStore.playerId && roomId.value) wsStore.leaveGame(roomId.value, userStore.playerId)
  // 방 나가면 캐릭터 초기화 → 다른 방 들어갈 때 이전 방 캐릭터로 자동 로딩되지 않도록
  userStore.updateAvatar('❓')
  setTimeout(() => { wsStore.cleanup(); roomStore.leaveRoom(); router.push('/'); }, 150)
  showExitModal.value = false;
};

const clouds = ref<{ id: number; typeId: number; style: any }[]>([]);
onMounted(async () => {
  const newClouds = [];
  for (let i = 0; i < 8; i++) {
    const size = 0.5 + Math.random() * 0.5;
    const dur = 30 + Math.random() * 20;
    newClouds.push({
      id: i, typeId: (i % 3) + 1,
      style: { top: `${Math.random() * 30}%`, transform: `scale(${size})`, animationDuration: `${dur}s`, animationDelay: `-${Math.random() * 20}s` }
    });
  }
  clouds.value = newClouds;

  if (!roomId.value) return
  if (!userStore.playerId) userStore.restoreUser()
  if (!userStore.playerId) { router.push('/'); return; }
  try {
    const room = await getRoomById(roomId.value)
    roomStore.joinRoom({ roomId: room.roomId, title: room.title, capacity: room.capacity, isPrivate: room.isPrivate, status: room.status, currentCount: room.currentCount, quizCount: room.quizCount })
    await applyInitialQuizState(room.quizCount)
  } catch (error) { router.push('/'); return; }

  await wsStore.connect()
  wsStore.subscribeErrors(userStore.playerId); 
  wsStore.subscribeGameState(roomId.value); 

  wsStore.subscribeMiniGame(roomId.value);
  wsStore.joinGame(roomId.value, userStore.playerId, userStore.username)
})

// 유저 ID를 키로, 메시지 내용을 값으로 저장하는 상태
const userMessages = ref<Record<string, string>>({});

// 메시지를 띄우고 3초 뒤에 지우는 함수
const showQuickMessage = (userId: string, message: string) => {
  userMessages.value[userId] = message;

  // 3초 후에 해당 메시지 삭제
  setTimeout(() => {
    delete userMessages.value[userId];
  }, 3000);
};

// 내가 보낼 퀵메시지 목록 (8개로 확장)
const quickMessages = [
  { label: '인사', content: '하이~' },
  { label: '준비', content: '준비 안함?' },
  { label: '출발', content: '고해라' },
  { label: '환영', content: '반갑고~' },
  { label: '슬픔', content: 'ㅠ_ㅠ' },
  { label: '대기', content: '잠만!' },
  { label: '최고', content: '좋은데?' },
  { label: '웃음', content: 'ㅋㅋㅋㅋ' }
];
</script>

<template>
  <div class="page-container cartoon-theme">
    <div class="cartoon-bg">
      <div class="sky"></div>
      <div v-for="c in clouds" :key="c.id" :class="['cloud', `cloud-type-${c.typeId}`]" :style="c.style"></div>
      <div class="ground-back"></div>
      <div class="ground-front"></div>
    </div>

    <div class="w-[1080px] h-[800px] wood-panel flex flex-col !p-2 relative z-10 box-border">
      <header class="header shrink-0 h-16 mb-4 flex items-center justify-between relative px-2">
        <div class="flex items-center gap-3 z-20">
          <div
            class="flex items-center justify-center gap-2 shrink-0 rounded-full border-2 border-[#5d4037] bg-white/60 shadow-[0_3px_0_rgba(93,64,55,0.2)]"
            style="height: 40px; padding: 0 16px;">
            <span class="material-symbols-outlined" style="font-size: 20px;">group</span>
            <span style="font-family: 'Jua', sans-serif; font-weight: 900; font-size: 16px;">{{ currentCount }} / {{
              maxUsers }}</span>
          </div>
          <button v-if="roomInfo.inviteCode || roomInfo.roomId"
            class="flex items-center justify-center gap-2 shrink-0 rounded-full border-2 border-[#5d4037] bg-white/80 shadow-[0_3px_0_rgba(93,64,55,0.2)] active:translate-y-[2px]"
            style="height: 40px; padding: 0 16px; cursor: pointer;" @click="copyRoomCode">
            <span class="material-symbols-outlined" style="font-size: 20px;">link</span>
            <span style="font-family: 'Jua', sans-serif; font-weight: 900; font-size: 16px; letter-spacing: 1px;">{{
              roomInfo.inviteCode || roomInfo.roomId }}</span>
          </button>
        </div>

        <div class="absolute left-1/2 -translate-x-1/2 flex flex-col items-center pointer-events-none w-[500px]">
          <h1 class="truncate text-center w-full drop-shadow-sm font-black text-3xl text-[#5d4037]" style="margin: 0;">
            {{ roomInfo.title || '대기실' }}</h1>
        </div>

        <div class="z-20">
          <button @click="leaveRoom"
            class="flex items-center justify-center gap-2 rounded-xl border-none font-black text-white cursor-pointer active:top-[4px]"
            style="height: 40px; padding: 0 20px; background-color: #ff5252; box-shadow: 0 4px 0 #b71c1c; position: relative; font-family: 'Jua', sans-serif;">
            <span class="material-symbols-outlined" style="font-size: 20px;">logout</span>
            <span style="font-size: 16px;">EXIT</span>
          </button>
        </div>
      </header>

      <div class="flex gap-6 flex-[12] min-h-0 px-2">

        <main
          class="flex-[2] bg-white/40 rounded-[32px] border-4 border-wood-border !p-2 shadow-inner relative flex flex-col">
          <div class="grid grid-cols-2 gap-4 w-full h-full">
            <div v-for="user in displayUsers" :key="user.id"
              class="user-slot relative w-full h-full rounded-2xl border-4 transition-all duration-300 min-h-0 box-border"
              :class="[
                user.isEmpty ? 'border-dashed border-wood-light bg-white/20' : 'bg-white shadow-md',
                user.isMe ? 'border-green-500 bg-green-50' :
                  (user.isHost ? 'border-yellow-400 bg-yellow-50' : 'border-[#5d4037]')
              ]">

              <transition name="pop">
                <div v-if="userMessages[user.id]" class="absolute -top-12 -right-7 z-50">
                  <div class="speech-bubble">
                    {{ userMessages[user.id] }}
                  </div>
                </div>
              </transition>

              <div v-if="user.isEmpty"
                class="w-full h-full flex flex-col items-center justify-center text-wood-light opacity-60">
                <span class="material-symbols-outlined text-5xl mb-2 animate-pulse">person_add</span>
                <span class="font-bold text-xl">WAITING</span>
              </div>

              <div v-else class="w-full h-full relative overflow-hidden rounded-xl group">

                <div class="absolute inset-0 z-0 text-center">
                  <AvatarViewer v-if="user.avatarIndex !== -1" :avatarIndex="user.avatarIndex" :scale="2.2"
                    :cameraY="0.6" :action="user.isReady ? 'win' : 'idle'" />
                  <div v-else class="w-full h-full flex items-center justify-center text-6xl">
                    {{ user.avatar || '👤' }}
                  </div>
                </div>

                <!-- 닉네임 박스 (v-else 내부에 위치) -->
                <div
                  class="absolute bottom-4 left-1/2 -translate-x-1/2 z-10 w-[90%] h-[15%] flex justify-center pointer-events-none">
                  <div
                    class="bg-black/70 backdrop-blur-sm text-white text-center py-2 !px-28 rounded-lg font-black text-lg shadow-md border-2 border-white/30 flex items-center gap-2">
                    <span class="truncate max-w-[150px]" style="text-shadow: 1px 1px 0 #000;">{{ user.nickname
                      }}</span>
                    <span v-if="user.isMe"
                      class="text-[10px] bg-green-500 !px-1.5 py-0.5 rounded text-white font-bold shrink-0">ME</span>
                  </div>
                </div>

                <!-- 방장 표시 -->
                <div v-if="user.isHost" class="absolute top-2 left-2 z-20">
                  <span class="text-3xl filter drop-shadow-md">👑</span>
                </div>

                <!-- 레디 표시 -->
                <div v-if="user.isReady && !user.isHost"
                  class="absolute inset-0 z-20 flex items-center justify-center bg-black/20 backdrop-blur-[1px]">
                  <div
                    class="bg-red-500 text-white border-4 border-white transform -rotate-12 px-6 py-2 text-2xl font-black shadow-xl tracking-widest uppercase animate-pulse">
                    READY
                  </div>
                </div>
              </div>
            </div>
          </div>
        </main>

        <aside class="flex-1 flex flex-col gap-4 min-h-0 overflow-hidden">

          <div
            class="h-28 bg-white/70 !p-2 py-3 rounded-[32px] border-4 border-wood-border flex flex-col items-center shadow-sm shrink-0">
            <h3 class="text-lg font-black mb-2 text-wood-dark flex items-center gap-2">
              <span class="material-symbols-outlined text-leaf-green">face</span> 캐릭터 선택
            </h3>
            <div class="w-full flex-1 flex items-center justify-center gap-3">
              <button v-for="char in characters" :key="char" @click="selectCharacter(char)"
                :disabled="isCharacterTaken(char) || (myUser?.isReady && !myUser?.isHost)"
                class="h-14 w-14 rounded-xl text-3xl flex items-center justify-center border-4 bg-white transition-all relative"
                :class="selectedChar === char ? 'border-leaf-green bg-green-100 shadow-md -translate-y-1' : 'border-wood-light hover:border-leaf-green'">
                {{ char }}
                <div v-if="isCharacterTaken(char)"
                  class="absolute inset-0 bg-gray-500/30 rounded-xl flex items-center justify-center">
                  <span class="material-symbols-outlined text-gray-600 font-bold text-2xl">lock</span>
                </div>
              </button>
            </div>
          </div>

          <div
            class="flex-1 bg-white/70 p-4 rounded-[32px] border-4 border-wood-border flex flex-col gap-3 shadow-sm min-h-0">

            <div v-if="isHost" class="w-full h-full flex flex-col gap-3 !p-3">

              <div v-if="generatedQuizzes.length > 0"
                class="flex-[2] w-full bg-green-100 border-4 border-leaf-green rounded-2xl p-4 flex flex-col items-center justify-center gap-3 ">
                <div class="flex items-center gap-3">
                  <span class="material-symbols-outlined text-leaf-dark text-5xl">check_circle</span>
                  <div>
                    <p class="font-black text-leaf-dark text-2xl leading-none">준비 완료</p>
                    <p class="text-sm text-leaf-dark opacity-80 mt-1">{{ generatedQuizzes.length }}문제 생성됨</p>
                  </div>
                </div>
                <button @click="resetAndUpload"
                  class="text-sm bg-white px-4 py-2 rounded-full shadow-sm hover:bg-gray-100 text-gray-600 flex items-center gap-1 font-bold">
                  <span class="material-symbols-outlined text-base">refresh</span> 다시하기
                </button>
              </div>

              <div v-else
                class="flex-[2] w-full rounded-2xl border-4 border-dashed flex flex-col  items-center justify-center cursor-pointer transition-all relative group bg-white hover:bg-gray-50 "
                :class="isDragging ? 'border-leaf-green bg-green-50' : 'border-wood-light'" @click="triggerFileInput"
                @dragover="onDragOver" @dragleave="onDragLeave" @drop="onDrop">
                <input type="file" ref="fileInputRef" accept="application/pdf" class="hidden"
                  @change="handleFileChange" />

                <div v-if="isGenerating" class="flex flex-col items-center gap-2">
                  <span class="material-symbols-outlined animate-spin text-wood-dark text-4xl">sync</span>
                  <span class="text-lg font-bold text-wood-dark">퀴즈 생성 중...</span>
                </div>
                <div v-else class="flex flex-col items-center gap-2 group-hover:scale-105 transition-transform">
                  <span class="material-symbols-outlined text-wood-dark text-5xl opacity-40">cloud_upload</span>
                  <span class="text-xl font-black text-wood-dark opacity-60">PDF 파일 드래그</span>
                  <span class="text-sm font-bold text-wood-light">또는 클릭하여 업로드</span>
                </div>
              </div>

              <div v-if="generatedQuizzes.length === 0" class="flex gap-3 w-full h-14 shrink-0">
                <button @click="triggerFileInput" class="cartoon-btn secondary h-full text-lg flex-1">파일 선택</button>
                <button @click="isBasicQuizModalOpen = true" class="cartoon-btn primary h-full text-lg flex-1">퀴즈
                  선택</button>
              </div>

              <button @click="handleStartGame"
                class="cartoon-btn primary h-20 text-3xl font-black w-full shadow-lg shrink-0 mt-auto"
                :class="{ 'opacity-50 cursor-not-allowed bg-gray-400': !isAllReady && generatedQuizzes.length > 0 }">
                GAME START
              </button>
            </div>

            <div v-else class="w-full h-full flex flex-col gap-3 !p-3">

              <div
                class="flex-1 w-full rounded-2xl border-4 border-dashed border-wood-light bg-white/50 flex flex-col items-center justify-center text-center ">
                <div v-if="generatedQuizzes.length === 0" class="flex flex-col items-center gap-3">
                  <span class="material-symbols-outlined text-wood-light text-6xl animate-bounce">pending</span>
                  <p class="text-wood-dark font-black text-lg px-4 break-keep leading-tight">{{ processingText }}</p>
                </div>
                <div v-else class="flex flex-col items-center text-leaf-dark gap-2">
                  <span class="material-symbols-outlined text-6xl">check_circle</span>
                  <span class="font-black text-2xl">퀴즈 준비 완료!</span>
                  <span class="text-base opacity-70 font-bold bg-white/50 px-3 py-1 rounded-full">{{
                    generatedQuizzes.length }}문제</span>
                </div>
              </div>

              <button @click="toggleReady"
                class="cartoon-btn h-20 text-3xl font-black w-full shadow-lg shrink-0 mt-auto"
                :class="myUser?.isReady ? 'secondary' : 'primary'">
                {{ myUser?.isReady ? 'CANCEL' : 'READY' }}
              </button>
            </div>

          </div>

        </aside>
      </div>

      <!-- 🗨️ 퀵메시지 매크로 바 (스크롤 제거 및 디자인 최적화) -->
      <footer class="mt-auto !py-4 px-4 pb-4 shrink-0 relative z-50">
        <div
          class="w-full bg-white/40 backdrop-blur-md !p-5 rounded-[32px] border-4 border-wood-border flex gap-5 items-center justify-center shadow-lg">
          <span class="material-symbols-outlined text-wood-dark font-black text-4xl mr-2"
            style="font-size: 70px;">chat</span>
          <div class="flex flex-wrap gap-3 justify-center items-center">
            <button v-for="msg in quickMessages" :key="msg.label"
              @click="showQuickMessage(userStore.playerId, msg.content)"
              class="bg-white hover:bg-green-50 !px-6 !py-4 rounded-[24px] border-4 border-wood-border text-2xl font-black text-wood-dark shadow-[0_2px_0_#5d4037] active:translate-y-[8px] active:shadow-none transition-all whitespace-nowrap">
              {{ msg.label }}
            </button>
          </div>
        </div>
      </footer>
    </div>

    <AlertModal :isOpen="isToastOpen" :message="toastMessage" :type="toastType" @close="isToastOpen = false" />
    <ConfirmModal :isOpen="showExitModal" title="나가기" message="대기실에서 나갈까요?" confirmLabel="나가기" icon="logout"
      variant="danger" @close="showExitModal = false" @confirm="handleExitConfirm" />

    <transition name="intro-fade">
      <div v-if="isBasicQuizModalOpen"
        class="fixed inset-0 bg-black/60 backdrop-blur-sm z-[100] flex items-center justify-center p-4"
        @click.self="isBasicQuizModalOpen = false">
        <div
          class="w-[500px] bg-[#fff8e1] border-[6px] border-wood-border rounded-[30px] !p-4 shadow-2xl flex flex-col gap-4 max-h-[80vh] overflow-y-auto custom-scrollbar">
          <h2 class="text-3xl font-black text-center text-wood-text mb-2">테마 선택</h2>
          <div class="grid grid-cols-1 gap-3">
            <button v-for="item in basicQuizTypes" :key="item.type" @click="selectBasicQuiz(item.type)"
              class="group p-4 rounded-2xl border-4 border-wood-border bg-white hover:bg-green-50 hover:border-leaf-green transition-all flex items-center gap-4 text-left active:scale-95">
              <div class="w-12 h-12 rounded-xl flex items-center justify-center text-2xl" :class="item.color">
                <span class="material-symbols-outlined">{{ item.icon }}</span>
              </div>
              <div>
                <p class="text-xl font-black text-wood-dark">{{ item.label }}</p>
              </div>
            </button>
          </div>
          <button @click="isBasicQuizModalOpen = false"
            class="cartoon-btn secondary h-14 mt-4 text-xl font-bold">닫기</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&family=Nanum+Pen+Script&display=swap');

.cartoon-theme {
  --text-brown: #5d4037;
  --wood-light: #d7ccc8;
  --wood-medium: #bcaaa4;
  --wood-dark: #8d6e63;
  --wood-border: #5d4037;
  --leaf-green: #7cb342;
  --leaf-dark: #558b2f;
  --font-family: 'Jua', sans-serif;
}

.text-wood-dark {
  color: var(--wood-dark);
}

.text-wood-light {
  color: var(--wood-light);
}

.text-leaf-dark {
  color: var(--leaf-dark);
}

.bg-leaf-green {
  background-color: var(--leaf-green);
}

.page-container {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background-color: #81d4fa;
  color: var(--text-brown);
  font-family: var(--font-family);
  display: flex;
  justify-content: center;
  align-items: center;
}

.cartoon-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(to bottom, #4fc3f7 0%, #b3e5fc 100%);
}

.sky {
  position: absolute;
  inset: 0;
}

.ground-back {
  position: absolute;
  bottom: 0;
  width: 100%;
  height: 35%;
  background: #9ccc65;
  transform: skewY(-2deg);
  transform-origin: bottom right;
}

.ground-front {
  position: absolute;
  bottom: -50px;
  width: 100%;
  height: 30%;
  background: #c5e1a5;
  border-top: 10px solid #aed581;
  border-radius: 50% 50% 0 0 / 10% 10% 0 0;
}

.wood-panel {
  background: #fff8e1;
  border: 10px solid var(--wood-border);
  border-radius: 40px;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.3), inset 0 0 40px rgba(93, 64, 55, 0.1);
}

.cartoon-btn {
  width: 100%;
  border: none;
  border-radius: 20px;
  font-family: 'Jua', sans-serif;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.1s ease;
  position: relative;
  top: 0;
}

.cartoon-btn:active {
  top: 4px;
  box-shadow: none !important;
}

.cartoon-btn.primary {
  background: #7cb342;
  box-shadow: 0 6px 0 #558b2f;
}

.cartoon-btn.secondary {
  background: #ff7043;
  box-shadow: 0 6px 0 #d84315;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: var(--wood-medium);
  border-radius: 3px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.cloud {
  position: absolute;
  background: #fff;
  left: 0;
  will-change: transform;
  animation: moveClouds linear infinite;
}

.cloud::after,
.cloud::before {
  content: '';
  position: absolute;
  background: #fff;
  border-radius: 50%;
}

.cloud-type-1 {
  width: 120px;
  height: 40px;
  border-radius: 20px;
}

.cloud-type-1::after {
  width: 50px;
  height: 50px;
  top: -25px;
  left: 15px;
}

.cloud-type-1::before {
  width: 40px;
  height: 40px;
  top: -15px;
  left: 60px;
}

.cloud-type-2 {
  width: 150px;
  height: 35px;
  border-radius: 60px;
}

.cloud-type-2::after {
  width: 45px;
  height: 35px;
  top: -15px;
  left: 25px;
  border-radius: 50% 50% 0 0;
}

.cloud-type-2::before {
  width: 60px;
  height: 40px;
  top: -20px;
  left: 65px;
  border-radius: 50% 50% 0 0;
}

.cloud-type-3 {
  width: 100px;
  height: 30px;
  border-radius: 15px;
}

.cloud-type-3::after {
  width: 70px;
  height: 70px;
  top: -35px;
  left: 10px;
}

@keyframes moveClouds {
  0% {
    transform: translateX(110vw);
  }

  100% {
    transform: translateX(-30vw);
  }
}

.intro-fade-enter-active,
.intro-fade-leave-active {
  transition: all 0.3s ease;
}

.intro-fade-enter-from,
.intro-fade-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

/* 🗨️ 말풍선 디자인 */
.speech-bubble {
  position: relative;
  background: #ffffff;
  border: 4px solid #5d4037;
  border-radius: 20px;
  padding: 20px 40px;
  font-family: 'Jua', sans-serif;
  font-weight: 900;
  font-size: 1.5rem;
  color: #5d4037;
  box-shadow: 6px 6px 0 rgba(0, 0, 0, 0.1);
  white-space: nowrap;
}

/* 말풍선 꼬리 */
.speech-bubble::after {
  content: '';
  position: absolute;
  bottom: -20px;
  left: 30px;
  border-width: 20px 20px 0 0;
  border-style: solid;
  border-color: #5d4037 transparent transparent transparent;
}

.speech-bubble::before {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 34px;
  border-width: 14px 14px 0 0;
  border-style: solid;
  border-color: #ffffff transparent transparent transparent;
  z-index: 1;
}

/* 애니메이션 */
.pop-enter-active {
  animation: bubble-bounce 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.pop-leave-active {
  transition: all 0.2s ease;
  opacity: 0;
  transform: translateY(-10px);
}

@keyframes bubble-bounce {
  0% {
    transform: scale(0);
    opacity: 0;
  }

  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>