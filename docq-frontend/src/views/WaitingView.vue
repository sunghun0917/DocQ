<script setup lang="ts">
import { ref, computed, onMounted, watchEffect } from 'vue';
import UserCard from '@/components/lobby/UserCard.vue';
import { useRouter, useRoute } from 'vue-router';
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { getRoomById, getErrorMessage, generateQuiz, getQuizzes, type QuizResponse } from '@/services/api'

const router = useRouter();
const route = useRoute();
const roomStore = useRoomStore()
const userStore = useUserStore()
const wsStore = useWebSocketStore()

const roomId = computed(() => String(route.params.roomId || ''))
const roomInfo = computed(() => roomStore.roomInfo)
const users = computed(() => roomStore.users)
const myUser = computed(() => roomStore.myUser)

const roomTypeLabel = computed(() => (roomInfo.value.isPrivate ? '비밀' : '공개'))
const currentCount = computed(() => users.value.length)
const maxUsers = computed(() => roomInfo.value.capacity || 0)

// Quiz Generation State
const isGenerating = ref(false)
const selectedFile = ref<File | null>(null)
const generatedQuizzes = ref<QuizResponse[]>([])
const isQuizModalOpen = ref(false)

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    selectedFile.value = target.files[0]
  }
}

const handleUploadPdf = async () => {
  if (!selectedFile.value || !roomId.value) return
  
  isGenerating.value = true
  try {
    await generateQuiz(roomId.value, selectedFile.value)
    alert('퀴즈 생성이 시작되었습니다! 잠시 후 완료 알림이 뜹니다.')
    selectedFile.value = null // Reset
    // Refresh quizzes (optional, or wait for websocket)
    await loadQuizzes()
  } catch (error) {
    alert('퀴즈 생성 실패: ' + getErrorMessage(error))
  } finally {
    isGenerating.value = false
  }
}

const loadQuizzes = async () => {
  try {
    generatedQuizzes.value = await getQuizzes(roomId.value)
  } catch (error) {
    console.error('Failed to load quizzes', error)
  }
}

const openQuizModal = async () => {
  await loadQuizzes()
  isQuizModalOpen.value = true
}

const displayUsers = computed(() => {
  // users.value의 avatar가 undefined일 수 있으므로 string으로 변환
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

const isCharacterTaken = (char: string) => {
  return users.value.some(user => user.avatar === char && !user.isMe);
};

const selectCharacter = (char: string) => {
  // 준비 완료 상태에서는 캐릭터 변경 불가 (방장은 예외)
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

const toggleReady = () => {
  if (!myUser.value || !userStore.userId) return
  wsStore.setReady(roomId.value, userStore.userId, !myUser.value.isReady)
}

const copyRoomCode = () => {
  const code = roomInfo.value.inviteCode || roomInfo.value.roomId
  if (!code) return
  navigator.clipboard.writeText(code);
  alert(`코드 복사 완료!`);
};

const handleStartGame = () => {
  // 퀴즈 필수 체크 (선택 사항)
  // if (generatedQuizzes.value.length === 0) {
  //    if (!confirm("퀴즈가 생성되지 않았습니다. 그래도 시작하시겠습니까?")) return;
  // }

  if (!isAllReady.value) {
    alert("모든 플레이어가 준비해야 합니다!");
    return;
  }
  if (!userStore.userId) return;
  // 서버로 게임 시작 요청 전송
  wsStore.startGame(roomId.value, userStore.userId)
};

// 방 상태가 STARTING 또는 PLAYING으로 변하면 게임 화면으로 이동
watchEffect(() => {
  const status = roomInfo.value.status
  if (status === 'STARTING' || status === 'PLAYING') {
    console.log(`🎮 Game Started! (${status}) redirecting to /game...`)
    router.push('/game')
  }
})

const leaveRoom = () => {
  if (confirm("정말 방을 나가시겠습니까?")) {
    wsStore.cleanup()
    roomStore.leaveRoom()
    router.push('/');
  }
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

  if (roomStore.roomInfo.roomId !== roomId.value) {
    try {
      const room = await getRoomById(roomId.value)
      roomStore.joinRoom({
        roomId: room.roomId,
        title: room.title,
        capacity: room.capacity,
        isPrivate: room.isPrivate,
        status: room.status,
        currentCount: room.currentCount
      })
    } catch (error) {
      alert(getErrorMessage(error))
      router.push('/')
      return
    }
  }

  await wsStore.connect()
  wsStore.subscribeErrors(userStore.userId)
  wsStore.subscribeGameState(roomId.value)
  wsStore.joinGame(roomId.value, userStore.userId, userStore.username)
})
</script>

<template>
  <div class="min-h-screen bg-[#F0F2F5] flex items-center justify-center p-12 font-sans text-[#2d3436]">
    
    <div class="w-full max-w-7xl bg-white rounded-[10px] shadow-[0_20px_60px_-15px_rgba(0,0,0,0.1)] py-16 px-16 border border-white">      
      <header class="flex justify-between items-center mb-10 px-8">
        <div class="flex items-center gap-6">
          <h1 class="text-4xl font-black text-[#2d3436] tracking-tight">
            {{ roomInfo.title || '대기실' }}
          </h1>
          <span class="bg-[#ede9fe] text-[#7c3aed] px-4 py-1.5 rounded-lg text-sm font-extrabold tracking-wide">
            {{ roomTypeLabel }}
          </span>
        </div>
        <div class="flex items-center gap-2 bg-[#f1f2f6] px-5 py-2.5 rounded-lg font-bold text-[#636e72]">
          <span>👥</span>
          <span>{{ currentCount }} / {{ maxUsers || '-' }}</span>
        </div>
      </header>

      <div class="grid grid-cols-12 gap-10 h-[580px]">
        
        <main class="col-span-8 bg-white p-8 rounded-[22px] border-2 border-[#dfe6e9]">
          <div class="grid grid-cols-2 grid-rows-2 gap-6 h-full">
            <UserCard 
              v-for="user in displayUsers" 
              :key="user.id" 
              :user="user" 
              class="w-full h-full transition-transform hover:-translate-y-1"
            />
          </div>
        </main>

        <aside class="col-span-4 flex flex-col justify-between h-full gap-8">
          
          <div class="bg-white p-8 rounded-[22px] border-2 border-[#dfe6e9] flex-1 overflow-hidden flex flex-col">
            <h3 class="text-lg font-bold mb-6 text-[#636e72] flex items-center gap-2 px-2">
              <span></span> 캐릭터 선택
            </h3>
            
            <div class="grid grid-cols-3 gap-4 content-start px-2 mb-6">
              <button 
                v-for="char in characters" 
                :key="char"
                @click="selectCharacter(char)"
                :disabled="isCharacterTaken(char) || (myUser?.isReady && !myUser?.isHost)"
                class="aspect-square rounded-lg text-4xl flex items-center justify-center transition-all relative border-2 bg-white"
                :class="[
                  selectedChar === char 
                    ? 'border-[#6c5ce7] shadow-[0_4px_0_0_#a29bfe] -translate-y-1' 
                    : 'border-transparent hover:bg-[#f1f2f6]',
                  (isCharacterTaken(char) || (myUser?.isReady && !myUser?.isHost))
                    ? 'opacity-40 cursor-not-allowed grayscale bg-slate-50' 
                    : 'cursor-pointer'
                ]"
              >
                {{ char }}
                <div v-if="isCharacterTaken(char)" class="absolute inset-0 flex items-center justify-center text-[#636e72] text-xl font-bold">
                  🔒
                </div>
              </button>
            </div>

            <!-- Quiz Management Section (Host Only) -->
            <div v-if="isHost" class="border-t pt-4">
              <h3 class="text-sm font-bold text-[#636e72] mb-3">📄 퀴즈 설정</h3>
              
              <div class="flex flex-col gap-2">
                 <input 
                   type="file" 
                   accept="application/pdf"
                   @change="handleFileChange"
                   class="mb-2 text-xs text-slate-500
                    file:mr-4 file:py-2 file:px-4
                    file:rounded-full file:border-0
                    file:text-xs file:font-semibold
                    file:bg-violet-50 file:text-violet-700
                    hover:file:bg-violet-100"
                 />
                 
                 <div class="flex gap-2">
                    <button 
                      @click="handleUploadPdf"
                      :disabled="!selectedFile || isGenerating"
                      class="flex-1 py-2 rounded-lg bg-blue-100 text-blue-700 font-bold text-xs hover:bg-blue-200 disabled:opacity-50"
                    >
                      <span v-if="isGenerating">생성 중...</span>
                      <span v-else>퀴즈 생성</span>
                    </button>
                    
                    <button 
                      @click="openQuizModal"
                      class="flex-1 py-2 rounded-lg bg-gray-100 text-gray-700 font-bold text-xs hover:bg-gray-200"
                    >
                      목록 보기
                    </button>
                 </div>
              </div>
            </div>
            <div v-else class="border-t pt-4">
               <h3 class="text-sm font-bold text-[#636e72] mb-3">📄 퀴즈 상태</h3>
               <button 
                  @click="openQuizModal"
                  class="w-full py-2 rounded-lg bg-gray-100 text-gray-700 font-bold text-xs hover:bg-gray-200"
                >
                  퀴즈 확인하기(공유)
                </button>
            </div>

          </div>
          
          <div class="flex flex-col gap-2 justify-end min-h-[6rem]">
            
            <template v-if="isHost">
              <div v-if="!isAllReady" class="text-red-500 text-xs font-bold text-center animate-pulse">
                📢 모두 준비완료가 되어야 합니다.
              </div>
              
              <button 
                @click="handleStartGame"
                :disabled="!isAllReady"
                class="w-full h-20 rounded-[24px] font-black text-2xl text-white transition-all 
                       flex items-center justify-center gap-3 border-b-0"
                :class="isAllReady 
                  ? 'bg-[#6c5ce7] shadow-[0_8px_0_0_#5f3dc4] hover:bg-[#5f3dc4] active:translate-y-[8px] active:shadow-none' 
                  : 'bg-[#b2bec3] cursor-not-allowed shadow-none opacity-50'"
              >
                GAME START
              </button>
            </template>
            
            <button 
              v-else
              @click="toggleReady"
              class="w-full h-20 rounded-[24px] font-black text-2xl transition-all 
                     active:shadow-none active:translate-y-[8px] 
                     flex flex-col items-center justify-center border-b-0"
              :class="myUser?.isReady 
                ? 'bg-[#00b894] text-white shadow-[0_8px_0_0_#00896f]' 
                : 'bg-white text-[#636e72] border-4 border-[#b2bec3] shadow-[0_8px_0_0_#636e72]'"
            >
              <span class="text-sm font-extrabold opacity-80 mb-1 tracking-wider">
                {{ myUser?.isReady ? 'ALL SET!' : 'ARE YOU READY?' }}
              </span>
              <span>{{ myUser?.isReady ? '준비완료' : '준비하기' }}</span>
            </button>
          </div>
        </aside>
      </div>

    <!-- Quiz Modal -->
    <div v-if="isQuizModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" @click.self="isQuizModalOpen = false">
       <div class="bg-white rounded-2xl p-8 w-[600px] max-h-[80vh] overflow-hidden flex flex-col shadow-2xl">
          <header class="flex justify-between items-center mb-6">
             <h2 class="text-2xl font-black text-gray-800">📋 생성된 퀴즈 목록</h2>
             <button @click="isQuizModalOpen = false" class="text-gray-400 hover:text-gray-600 font-bold text-xl">✕</button>
          </header>
          
          <div class="flex-1 overflow-y-auto pr-2 space-y-4">
             <div v-if="generatedQuizzes.length === 0" class="text-center py-12 text-gray-400 font-bold">
                 생성된 퀴즈가 없습니다.<br>
                 <span class="text-sm font-normal">방장이 PDF를 업로드하여 퀴즈를 생성해주세요.</span>
             </div>
             
             <div v-for="(quiz, idx) in generatedQuizzes" :key="quiz.quizId" class="border rounded-xl p-5 bg-gray-50 border-gray-200">
                <div class="flex gap-3 mb-3">
                   <span class="bg-[#6c5ce7] text-white rounded-lg px-2.5 py-0.5 text-sm font-black h-fit">Q{{ idx + 1 }}</span>
                   <p class="font-bold text-gray-800 leading-snug">{{ quiz.question }}</p>
                </div>
                <div class="space-y-1.5 pl-2">
                   <div v-for="(opt, oIdx) in quiz.options" :key="oIdx" 
                        class="text-sm flex items-center gap-2"
                        :class="oIdx === quiz.answerIndex ? 'text-[#00b894] font-bold' : 'text-gray-600'">
                      <span class="w-4 h-4 rounded-full border flex items-center justify-center text-[10px]"
                            :class="oIdx === quiz.answerIndex ? 'border-[#00b894] bg-[#00b894]/10' : 'border-gray-400'">
                            {{ oIdx + 1 }}
                      </span>
                      {{ opt }}
                      <span v-if="oIdx === quiz.answerIndex">✅</span>
                   </div>
                </div>
             </div>
          </div>
          
          <div class="mt-6 pt-4 border-t flex justify-end">
             <button @click="isQuizModalOpen = false" class="px-6 py-2 bg-gray-200 hover:bg-gray-300 rounded-lg font-bold text-gray-700">
                닫기
             </button>
          </div>
       </div>
    </div>

      <footer class="mt-12 px-8 flex justify-between items-center border-t border-[#dfe6e9] pt-8">
        <div class="text-[#b2bec3] text-sm font-bold">
          DocQ - 문서 기반 퀴즈 게임
        </div>
        
        <div class="flex gap-4">
          <button @click="copyRoomCode" class="px-6 py-3 rounded-lg font-bold bg-white border-2 border-[#dfe6e9] text-[#636e72] hover:bg-[#f1f2f6] transition flex items-center gap-2">
            Code: <span class="text-[#2d3436] font-black">{{ roomInfo.inviteCode || roomInfo.roomId }}</span>
          </button>
          
          <button 
            @click="leaveRoom" 
            class="px-8 py-3 rounded-lg font-bold bg-[#fff5f5] text-[#ff6b6b] border-2 border-transparent hover:border-[#ff6b6b] transition shadow-sm"
          >
            나가기
          </button>
        </div>
      </footer>
      
    </div>
  </div>
</template>
