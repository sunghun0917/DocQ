<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import MashGame from './MashGame.vue';
import SequenceGame from './SequenceGame.vue';
import StopwatchGame from './StopWatchGame.vue';

const props = defineProps<{
  gameType: 'mash' | 'sequence' | 'stopwatch';
}>();

const emit = defineEmits(['close']);

// 1. 플레이어 데이터 (테스트를 위해 3명으로 줄여봄, 나중엔 Store에서 가져옴)
const players = ref([
  { id: 'p1', nickname: '티라노(나)', avatar: '🦖', isMe: true },
  { id: 'p2', nickname: '토끼', avatar: '🐰', isMe: false },
  { id: 'p3', nickname: '여우', avatar: '🦊', isMe: false },
  // { id: 'p4', nickname: '곰돌이', avatar: '🐻', isMe: false },
]);

const scores = ref<Record<string, number>>({});
const isResultShown = ref(false);
const countdown = ref(3);
const isStarted = ref(false);
const showStartText = ref(false);

// [핵심] 인덱스에 따라 위치와 스타일 클래스 반환
const getPlayerStyle = (index: number) => {
  const positions = [
    'top-4 left-4 md:top-10 md:left-10',                                       // 0번: 좌상 (나)
    'top-4 right-4 md:top-10 md:right-10 flex-row-reverse text-right',          // 1번: 우상
    'bottom-4 left-4 md:bottom-10 md:left-10',                                  // 2번: 좌하
    'bottom-4 right-4 md:bottom-10 md:right-10 flex-row-reverse text-right'     // 3번: 우하
  ];
  return positions[index] || '';
};

// 3. 게임 종료 핸들러
const handleGameEnd = (myScore: number) => {
  // 내 점수 기록
  const myPlayer = players.value.find(p => p.isMe);
  if (myPlayer) scores.value[myPlayer.id] = myScore;

  // [수정] 플레이어 리스트를 돌면서 유동적으로 점수 생성 (하드코딩 제거)
  players.value.forEach(player => {
    if (!player.isMe) {
      // 랜덤 점수 (1.000 ~ 2.000초 사이)
      const randomScore = parseFloat((Math.random() + 1).toFixed(3));
      scores.value[player.id] = randomScore;
    }
  });
  
  isResultShown.value = true;
  setTimeout(() => emit('close', myScore), 3000);
};

// 4. 승자 판독기
const winnerId = computed(() => {
  const keys = Object.keys(scores.value);
  if (keys.length === 0) return null;
  return keys.reduce((a, b) => 
    (scores.value[a] ?? Infinity) < (scores.value[b] ?? Infinity) ? a : b
  );
});

onMounted(() => {
  const timer = setInterval(() => {
    countdown.value--;
    if (countdown.value === 0) {
      clearInterval(timer);
      showStartText.value = true;
      setTimeout(() => showStartText.value = false, 1000);
      isStarted.value = true;
    }
  }, 1000);
});
</script>

<template>
  <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/90 backdrop-blur-md p-4 animate-fade-in">
    
    <div class="w-full h-full max-w-6xl max-h-[800px] relative flex flex-col items-center justify-center">

      <div 
        v-for="(player, index) in players" 
        :key="player.id"
        class="absolute flex items-center gap-3 bg-white/10 p-4 rounded-2xl border-2 border-white/20 backdrop-blur-md min-w-[200px]"
        :class="getPlayerStyle(index)"
      >
        <div class="text-4xl">{{ player.avatar }}</div>
        <div>
          <div class="text-white font-bold">{{ player.nickname }}</div>
          
          <div v-if="isResultShown" class="text-xl font-black text-yellow-400">
            {{ scores[player.id] ? Number(scores[player.id]).toFixed(3) + 's' : '' }}
          </div>
          <div v-else class="text-white/50 text-sm">플레이 중...</div>
        </div>
        
        <div v-if="isResultShown" class="animate-pop" :class="index % 2 !== 0 ? 'mr-auto' : 'ml-auto'">
          <span v-if="winnerId === player.id" class="bg-yellow-400 text-black px-3 py-1 rounded-lg font-black shadow-lg">WIN 👑</span>
          <span v-else class="bg-gray-600 text-white px-3 py-1 rounded-lg font-bold">LOSE</span>
        </div>
      </div>

      <div class="bg-white rounded-[40px] p-8 shadow-2xl w-full max-w-2xl min-h-[400px] flex items-center justify-center relative z-10">
        
        <div v-if="!isStarted" class="absolute inset-0 z-50 flex items-center justify-center bg-white/80 rounded-[40px]">
          <div class="text-9xl font-black text-[#6c5ce7] animate-ping-slow">{{ countdown }}</div>
        </div>

        <div v-if="showStartText" class="absolute inset-0 z-50 flex items-center justify-center pointer-events-none">
          <div class="text-8xl font-black text-[#00b894] animate-bounce-in">Go!</div>
        </div>

        <MashGame v-if="gameType === 'mash'" :is-started="isStarted" @end="handleGameEnd" />
        <SequenceGame v-if="gameType === 'sequence'" :is-started="isStarted" @end="handleGameEnd" />
        <StopwatchGame v-if="gameType === 'stopwatch'" :is-started="isStarted" @end="handleGameEnd" />
      </div>

    </div>
  </div>
</template>

<style scoped>
/* 애니메이션 동일 */
.animate-fade-in { animation: fadeIn 0.3s ease-out; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.animate-pop { animation: popIn 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
@keyframes popIn {
  0% { transform: scale(0); opacity: 0; }
  70% { transform: scale(1.2); }
  100% { transform: scale(1); opacity: 1; }
}

.animate-ping-slow { animation: pingCustom 1s infinite; }
@keyframes pingCustom { 0% { transform: scale(1); opacity: 1; } 100% { transform: scale(1.5); opacity: 0; } }

.animate-bounce-in { animation: bounceIn 0.5s both; }
@keyframes bounceIn { 0% { opacity: 0; transform: scale(0.3); } 50% { opacity: 1; transform: scale(1.05); } 100% { transform: scale(1); } }
</style>