<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted } from 'vue';
import MashGame from './MashGame.vue';
import SequenceGame from './SequenceGame.vue';
import StopwatchGame from './StopWatchGame.vue';

const props = defineProps<{
  gameType: 'mash' | 'sequence' | 'stopwatch';
  players: Array<{ id: string; nickname: string; avatar: string; isMe: boolean }>;
  timeLimitMs?: number;
  description?: string;
  result?: {
    winnerUserId?: string | null;
    rewardBuff?: string | null;
    rankings?: Array<{ userId: string; nickname: string; value: number; rank: number }>;
  };
}>();

const emit = defineEmits<{
  (e: 'submit', score: number): void;
  (e: 'close'): void;
}>();

const isSubmitted = ref(false);
const countdown = ref(3);
const isStarted = ref(false);
const showStartText = ref(false);
const miniTimeLeft = ref(10);
let miniTimer: number | null = null;

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

const resultScores = computed(() => {
  const map: Record<string, number> = {};
  props.result?.rankings?.forEach(rank => {
    map[rank.userId] = rank.value;
  });
  return map;
});

const isResultShown = computed(() => !!props.result?.rankings?.length);

const effectiveTimeLimitMs = computed(() => {
  if (props.gameType === 'mash') {
    return 10000;
  }
  return props.timeLimitMs;
});

const showMiniTimer = computed(() =>
  (props.gameType === 'mash' || props.gameType === 'sequence') &&
  isStarted.value &&
  !isResultShown.value
);

const winnerId = computed(() => {
  if (props.result?.winnerUserId) return props.result.winnerUserId;
  const top = props.result?.rankings?.[0];
  return top?.userId ?? null;
});

const handleGameEnd = (myScore: number) => {
  if (isSubmitted.value) return;
  isSubmitted.value = true;
  emit('submit', myScore);
};

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

watch([isStarted, effectiveTimeLimitMs], ([started, limit]) => {
  if (!started || props.gameType === 'stopwatch') return;
  if (miniTimer) clearInterval(miniTimer);
  miniTimeLeft.value = Math.max(1, Math.ceil((limit ?? 10000) / 1000));
  miniTimer = window.setInterval(() => {
    miniTimeLeft.value -= 1;
    if (miniTimeLeft.value <= 0) {
      if (miniTimer) clearInterval(miniTimer);
      miniTimer = null;
    }
  }, 1000);
});

watch(isResultShown, (shown) => {
  if (shown && miniTimer) {
    clearInterval(miniTimer);
    miniTimer = null;
  }
});

onUnmounted(() => {
  if (miniTimer) clearInterval(miniTimer);
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
            {{ resultScores[player.id] ? resultScores[player.id] + '점' : '' }}
          </div>
          <div v-else class="text-white/50 text-sm">
            {{ isSubmitted && player.isMe ? '결과 집계 중...' : '플레이 중...' }}
          </div>
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

        <div v-if="description" class="absolute top-4 left-1/2 -translate-x-1/2 text-sm font-bold text-slate-500">
          {{ description }}
        </div>

        <div v-if="showMiniTimer" class="absolute top-4 right-6 text-xl font-black text-slate-600">
          {{ miniTimeLeft }}s
        </div>

        <MashGame
          v-if="gameType === 'mash'"
          :is-started="isStarted"
          :time-limit-ms="effectiveTimeLimitMs"
          @end="handleGameEnd"
        />
        <SequenceGame
          v-if="gameType === 'sequence'"
          :is-started="isStarted"
          :time-limit-ms="effectiveTimeLimitMs"
          @end="handleGameEnd"
        />
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