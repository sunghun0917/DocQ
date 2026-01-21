<script setup lang="ts">
import { ref, computed } from 'vue';
import UserCard from '@/components/lobby/UserCard.vue';
import { useRouter } from 'vue-router'; 

const router = useRouter(); 
// [R&R] 창민: 대기실 및 콘텐츠 흐름 담당
const roomInfo = ref({
  roomId: 'A1B2',
  roomName: '매너게임 한판 하실?',
  roomType: '공개',
  maxUsers: 4,
  users: [
    { id: 1, nickname: '티라노', avatar: '🐱', isReady: true, isHost: true, isMe: true }, 
    // [테스트] 아래 유저의 isReady를 false로 두면 버튼이 비활성화됩니다.
    { id: 2, nickname: '창민짱짱돌이', avatar: '🐶', isReady: false, isHost: false, isMe: false },
    { id: 3, nickname: '머라카노', avatar: '🐸', isReady: false, isHost: false, isMe: false }, 
    { id: 4, nickname: '', avatar: '', isReady: false, isHost: false, isMe: false }, 
  ]
});

const characters = ['🐱', '🐶', '🦊', '🐰', '🐯', '🐸', '🐨', '🐷'];
const selectedChar = ref(roomInfo.value.users.find(u => u.isMe)?.avatar || '🐱');
const isHost = ref(true); 

const currentCount = computed(() => roomInfo.value.users.filter(u => u.nickname).length);

// [추가] 게임 시작 조건 검사 (최소 2명 이상 & 전원 Ready)
const isAllReady = computed(() => {
  const realUsers = roomInfo.value.users.filter(u => u.nickname);
  // 혼자서는 게임 불가(>1) AND 모든 인원이 준비 완료(every)
  return realUsers.length > 1 && realUsers.every(u => u.isReady);
});

const isCharacterTaken = (char: string) => {
  return roomInfo.value.users.some(user => user.avatar === char && !user.isMe);
};

const selectCharacter = (char: string) => {
  if (isCharacterTaken(char)) return;
  selectedChar.value = char;
  const myUser = roomInfo.value.users.find(u => u.isMe);
  if (myUser) {
    myUser.avatar = char;
  }
};

const toggleReady = () => {
  const myUser = roomInfo.value.users.find(u => u.isMe);
  if (myUser) {
    myUser.isReady = !myUser.isReady;
  }
};

const copyRoomCode = () => {
  navigator.clipboard.writeText(roomInfo.value.roomId);
  alert(`코드 복사 완료!`);
};

const handleStartGame = () => {
  // [추가] 강제 실행 방지
  if (!isAllReady.value) {
    alert("모든 플레이어가 준비해야 합니다!");
    return;
  }
  console.log("게임 시작 로직 실행");
};

const leaveRoom = () => {
  if (confirm("정말 방을 나가시겠습니까?")) {
    router.push('/'); 
  }
};
</script>

<template>
  <div class="min-h-screen bg-[#F0F2F5] flex items-center justify-center p-12 font-sans text-[#2d3436]">
    
    <div class="w-full max-w-7xl bg-white rounded-[10px] shadow-[0_20px_60px_-15px_rgba(0,0,0,0.1)] py-16 px-16 border border-white">      
      <header class="flex justify-between items-center mb-10 px-8">
        <div class="flex items-center gap-6">
          <h1 class="text-4xl font-black text-[#2d3436] tracking-tight">
            {{ roomInfo.roomName }}
          </h1>
          <span class="bg-[#ede9fe] text-[#7c3aed] px-4 py-1.5 rounded-lg text-sm font-extrabold tracking-wide">
            {{ roomInfo.roomType }}
          </span>
        </div>
        <div class="flex items-center gap-2 bg-[#f1f2f6] px-5 py-2.5 rounded-lg font-bold text-[#636e72]">
          <span>👥</span>
          <span>{{ currentCount }} / {{ roomInfo.maxUsers }}</span>
        </div>
      </header>

      <div class="grid grid-cols-12 gap-10 h-[580px]">
        
        <main class="col-span-8 bg-white p-8 rounded-[22px] border-2 border-[#dfe6e9]">
          <div class="grid grid-cols-2 grid-rows-2 gap-6 h-full">
            <UserCard 
              v-for="user in roomInfo.users" 
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
            
            <div class="grid grid-cols-3 gap-4 content-start px-2">
              <button 
                v-for="char in characters" 
                :key="char"
                @click="selectCharacter(char)"
                :disabled="isCharacterTaken(char)"
                class="aspect-square rounded-lg text-4xl flex items-center justify-center transition-all relative border-2 bg-white"
                :class="[
                  selectedChar === char 
                    ? 'border-[#6c5ce7] shadow-[0_4px_0_0_#a29bfe] -translate-y-1' 
                    : 'border-transparent hover:bg-[#f1f2f6]',
                  isCharacterTaken(char) 
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
              :class="roomInfo.users.find(u => u.isMe)?.isReady 
                ? 'bg-[#00b894] text-white shadow-[0_8px_0_0_#00896f]' 
                : 'bg-white text-[#636e72] border-4 border-[#b2bec3] shadow-[0_8px_0_0_#636e72]'"
            >
              <span class="text-sm font-extrabold opacity-80 mb-1 tracking-wider">
                {{ roomInfo.users.find(u => u.isMe)?.isReady ? 'ALL SET!' : 'ARE YOU READY?' }}
              </span>
              <span>{{ roomInfo.users.find(u => u.isMe)?.isReady ? '준비완료' : '준비하기' }}</span>
            </button>
          </div>
        </aside>
      </div>

      <footer class="mt-12 px-8 flex justify-between items-center border-t border-[#dfe6e9] pt-8">
        <div class="text-[#b2bec3] text-sm font-bold">
          DocQ - 문서 기반 퀴즈 게임
        </div>
        
        <div class="flex gap-4">
          <button @click="copyRoomCode" class="px-6 py-3 rounded-lg font-bold bg-white border-2 border-[#dfe6e9] text-[#636e72] hover:bg-[#f1f2f6] transition flex items-center gap-2">
            Code: <span class="text-[#2d3436] font-black">{{ roomInfo.roomId }}</span>
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
