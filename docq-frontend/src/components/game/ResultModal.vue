<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
// [추가] 폭죽 라이브러리 임포트
// canvas-confetti란?
// 폭죽 효과를 화면에 띄워주는 라이브러리
import confetti from 'canvas-confetti';

const router = useRouter();

const gameResult = ref({
  winner: '티라노',
  players: [
    { rank: 1, nickname: '티라노', avatar: '🐱', score: 150, isMe: false },
    { rank: 2, nickname: '창민짱짱돌이', avatar: '🐶', score: 120, isMe: true },
    { rank: 3, nickname: '머라카노', avatar: '🦊', score: 80, isMe: false },
    // { rank: 4, nickname: '자바왕', avatar: '🐰', score: 45, isMe: false },
  ]
});

const winner = computed(() => gameResult.value.players.find(p => p.rank === 1));
const others = computed(() => gameResult.value.players.filter(p => p.rank > 1));

// 폭죽 효과 함수
const launchConfetti = () => {
  // 1. 중앙에서 위로 솟구치는 폭죽
  confetti({
    particleCount: 150,
    spread: 70,
    origin: { y: 0.6 },
    zIndex: 9999, // 모달보다 위에 보이도록 설정
    colors: ['#6c5ce7', '#a29bfe', '#fab1a0', '#ffeaa7'] // 우리 테마 컬러 적용
  });

  // 2. 왼쪽/오른쪽에서 날아오는 효과 추가 (시간차)
  setTimeout(() => {
    confetti({
      particleCount: 50,
      angle: 60,
      spread: 55,
      origin: { x: 0 },
      zIndex: 9999
    });
  }, 200);

  setTimeout(() => {
    confetti({
      particleCount: 50,
      angle: 120,
      spread: 55,
      origin: { x: 1 },
      zIndex: 9999
    });
  }, 400);
};

onMounted(() => {
  setTimeout(() => {
    launchConfetti();
  }, 400); //0.4초 후 폭죽
});

const goHome = () => router.push('/');
const goLobby = () => router.push('/waiting/A1B2'); 
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-fade-in">
    
    <div class="w-full max-w-3xl bg-white rounded-[40px] shadow-2xl p-8 border-4 border-white/20 relative overflow-hidden animate-bounce-up">
      
      <div class="absolute top-0 left-0 w-full h-40 bg-gradient-to-b from-[#fff0f6] to-transparent -z-0"></div>

      <div class="relative z-10 flex flex-col items-center">
        <h1 class="text-5xl font-black text-[#2d3436] mb-2 tracking-tight drop-shadow-sm">GAME OVER</h1>
        <p class="text-[#636e72] font-bold text-lg mb-8">최종 순위 발표</p>

        <div class="flex w-full gap-8">
          
          <div class="flex-1 flex flex-col items-center justify-center bg-gradient-to-b from-[#fff9db] to-white p-6 rounded-[32px] border-2 border-[#ffec99]">
            <div class="text-6xl mb-2 animate-bounce">👑</div>
            <div class="w-40 h-40 bg-[#ffeaa7] rounded-full flex items-center justify-center text-7xl shadow-inner border-4 border-white mb-4 relative">
              {{ winner?.avatar }}
              <div class="absolute -bottom-3 bg-[#fab1a0] text-white px-3 py-1 rounded-full text-sm font-black border-2 border-white shadow-sm">
                WINNER
              </div>
            </div>
            <h2 class="text-2xl font-black text-[#2d3436]">{{ winner?.nickname }}</h2>
            <p class="text-[#fdcb6e] font-extrabold text-xl">{{ winner?.score }} pts</p>
          </div>

          <div class="flex-1 flex flex-col gap-3 justify-center">
            <div 
              v-for="player in others" 
              :key="player.rank"
              class="flex items-center bg-white p-3 rounded-[20px] border-2 border-[#dfe6e9] shadow-sm"
              :class="{'border-[#74b9ff] bg-[#f1faff]': player.isMe}" 
            >
              <div class="w-8 text-xl font-black text-[#b2bec3] italic">#{{ player.rank }}</div>
              <div class="w-10 h-10 rounded-full bg-[#f1f2f6] flex items-center justify-center text-xl mr-3">
                {{ player.avatar }}
              </div>
              <div class="flex-1 font-bold text-[#2d3436] truncate">
                {{ player.nickname }}
                <span v-if="player.isMe" class="ml-1 text-[10px] bg-[#74b9ff] text-white px-1.5 py-0.5 rounded-full align-middle">ME</span>
              </div>
              <div class="font-black text-[#636e72]">
                {{ player.score }}
              </div>
            </div>
          </div>
        </div>

        <div class="flex gap-4 w-full mt-8">
          <button 
            @click="goHome"
            class="flex-1 h-14 rounded-[20px] font-bold text-lg bg-[#f1f2f6] text-[#636e72] 
                   border-2 border-transparent hover:border-[#b2bec3] hover:bg-[#dfe6e9] transition"
          >
            메인으로
          </button>
          
          <button 
            @click="goLobby"
            class="flex-[2] h-14 rounded-[20px] font-black text-xl text-white 
                   bg-[#6c5ce7] shadow-[0_6px_0_0_#5f3dc4] 
                   hover:bg-[#5f3dc4] active:shadow-none active:translate-y-[6px] 
                   transition-all flex items-center justify-center gap-2"
          >
            <span>🔄</span> 한 판 더!
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 간단한 등장 애니메이션 */
@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes bounce-up {
  0% { transform: scale(0.8) translateY(20px); opacity: 0; }
  60% { transform: scale(1.05) translateY(-5px); opacity: 1; }
  100% { transform: scale(1) translateY(0); }
}
.animate-fade-in { animation: fade-in 0.3s ease-out; }
.animate-bounce-up { animation: bounce-up 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
</style>