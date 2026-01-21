  <script setup lang="ts">
  import { ref, computed, onMounted, watchEffect } from 'vue';
  import UserCard from '@/components/lobby/UserCard.vue';
  import { useRouter, useRoute } from 'vue-router';
  import { useRoomStore } from '@/stores/roomStore'
  import { useUserStore } from '@/stores/userStore'
  import { useWebSocketStore } from '@/stores/websocketStore'
  import { getRoomById, getErrorMessage } from '@/services/api'
  import AlertModal from '@/components/common/AlertModal.vue'; // 1. 컴포넌트 불러오기
  import ExitConfirmModal from '@/components/common/ExitConfirmModal.vue'; // 1. 컴포넌트 불러오기

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

  const isToastOpen = ref(false);
  const toastMessage = ref('');
  const toastType = ref<'success' | 'error'>('success');
  
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

  const copyRoomCode = async () => {
    const code = roomInfo.value.inviteCode || roomInfo.value.roomId
    if (!code) return

    try {
      await navigator.clipboard.writeText(code);
      
      // 성공 시 토스트 띄우기 (녹색 바)
      toastMessage.value = '방 코드가 복사되었습니다!';
      toastType.value = 'success';
      isToastOpen.value = true;
      
    } catch (err) {
      // 실패 시 에러 토스트 (빨간 바)
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
    // 서버로 게임 시작 요청 전송
    wsStore.startGame(roomId.value, userStore.userId)
  };

  // 방 상태가 PLAYING으로 변하면 게임 화면으로 이동
  watchEffect(() => {
    if (roomInfo.value.status === 'PLAYING') {
      console.log('🎮 Game Started! redirecting to /game...')
      router.push('/game')
    }
  })

  // [추가] 나가기 모달 상태
  const showExitModal = ref(false);

  // [수정] 나가기 버튼 클릭 시 모달 열기
  const leaveRoom = () => {
    showExitModal.value = true; 
  };

  // [추가] 실제 나가기 로직 (모달에서 '확인' 눌렀을 때 실행)
  const handleExitConfirm = () => {
    wsStore.cleanup();
    roomStore.leaveRoom();
    router.push('/');
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
            <h1 class="text-4xl font-extrabold text-[#881337] tracking-tight drop-shadow-sm">
              {{ roomInfo.title || '대기실' }}
            </h1>
            <span class="bg-[#fff1f2] text-[#e11d48] border border-[#fecdd3] px-3 py-1.5 rounded-full text-sm font-bold tracking-wide shadow-sm flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[18px]">
                {{ roomTypeIcon }}
              </span>
              
              <span>
                {{ roomInfo.isPrivate ? '비밀방' : '공개방' }}
              </span>
            </span>
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
            
            <div class="bg-white/50 p-8 rounded-[12px] border border-[#fecdd3] flex-1 flex flex-col shadow-sm">
              <h3 class="text-lg font-bold mb-6 text-[#9f1239] flex items-center gap-2">
                <span class="material-symbols-outlined text-[#e11d48]">face</span>
                캐릭터 선택
              </h3>
              
              <div class="grid grid-cols-4 gap-3 content-start">
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
            </div>
            
            <div class="flex flex-col gap-3 justify-end min-h-[6rem]">
              
              <template v-if="isHost">
                <div v-if="!isAllReady" class="text-[#e11d48] text-xs font-bold text-center animate-pulse bg-[#fff1f2] py-2 rounded-lg">
                  모두 준비 완료가 되어야 시작할 수 있습니다
                </div>
                
                <button 
                  @click="handleStartGame"
                  :disabled="!isAllReady"
                  class="w-full h-20 rounded-[12px] font-black text-2xl text-white transition-all 
                        flex items-center justify-center gap-3 shadow-lg"
                  :class="isAllReady 
                    ? 'bg-gradient-to-r from-[#e11d48] to-[#be123c] shadow-rose-500/30 hover:shadow-[0_0_25px_rgba(225,29,72,0.4)] hover:scale-[1.02] active:scale-[0.98]' 
                    : 'bg-gray-300 cursor-not-allowed shadow-none opacity-50'"
                >
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
  </style>