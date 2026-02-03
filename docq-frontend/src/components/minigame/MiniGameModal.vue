<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted } from 'vue';
import MashGame from './MashGame.vue';
import SequenceGame from './SequenceGame.vue';
import StopwatchGame from './StopWatchGame.vue';

const props = defineProps<{
  gameType: 'mash' | 'sequence' | 'stopwatch';
  players: Array<{ playerId: string; nickname: string; avatar: string; isMe: boolean }>;
  timeLimitMs?: number;
  description?: string;
  result?: {
    winnerPlayerId?: string | null;
    rewardBuff?: string | null;
    rankings?: Array<{ playerId: string; nickname: string; value: number; rank: number }>;
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
    'top-2 left-2 md:top-4 md:left-4',                                       // 0번: 좌상 (나)
    'top-2 right-2 md:top-4 md:right-4',                                      // 1번: 우상
    'bottom-2 left-2 md:bottom-4 md:left-4',                                  // 2번: 좌하
    'bottom-2 right-2 md:bottom-4 md:right-4'                                                                                // 3번: 우하
  ];
  return positions[index] || '';
};

const resultScores = computed(() => {
  const map: Record<string, number> = {};
  props.result?.rankings?.forEach(rank => {
    map[rank.playerId] = rank.value;
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
  if (props.result?.winnerPlayerId) return props.result.winnerPlayerId;
  const top = props.result?.rankings?.[0];
  return top?.playerId ?? null;
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
  <div
    class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-fade-in font-jua">

    <div class="w-full h-full max-w-6xl max-h-[900px] relative flex flex-col items-center justify-center">

      <!-- 플레이어 카드 (가로형 통일 + 내부 중앙 정렬) -->
      <!-- 플레이어 카드 (가로형 통일 + 내부 중앙 정렬) -->
      <div v-for="(player, index) in players" :key="player.playerId"
        class="absolute flex flex-row items-center justify-center gap-2 p-4 z-20 w-[340px] h-[140px] transition-all duration-300"
        :class="[
          getPlayerStyle(index),
          'player-card-cartoon'
        ]">
        <!-- 아바타: 잘림 방지를 위해 leading-none 및 z-index 추가 -->
        <div class="text-6xl drop-shadow-md shrink-0 leading-snug relative z-10 px-3">{{ player.avatar }}</div>

        <div class="flex flex-col items-center justify-center flex-grow min-w-0 relative z-10">
          <div class="text-[#5d4037] font-bold text-xl leading-tight truncate w-full text-center">{{ player.nickname }}
          </div>

          <div v-if="isResultShown" class="text-3xl font-black text-[#e65100] mt-1 drop-shadow-sm text-center">
            {{ resultScores[player.playerId] ? resultScores[player.playerId] : '0' }}
          </div>
          <div v-else class="text-[#8d6e63] text-sm mt-1 font-bold text-center">
            {{ isSubmitted && player.isMe ? '완료!' : '진행 중...' }}
          </div>
        </div>

        <div v-if="isResultShown && winnerId === player.playerId"
          class="absolute -top-6 -right-4 animate-pop rotate-12 z-20">
          <span class="text-5xl filter drop-shadow-xl">👑</span>
        </div>
      </div>

      <!-- 메인 게임 컨테이너 -->
      <div class="game-board-cartoon w-full max-w-2xl min-h-[450px] flex items-center justify-center relative z-10 p-8">

        <!-- 카운트다운 -->
        <div v-if="!isStarted"
          class="absolute inset-0 z-50 flex items-center justify-center bg-[#fff8e1]/90 rounded-[32px]">
          <div class="text-9xl font-black text-[#ff7043] animate-ping-slow drop-shadow-cartoon">{{ countdown }}</div>
        </div>

        <!-- Start 텍스트 -->
        <div v-if="showStartText" class="absolute inset-0 z-50 flex items-center justify-center pointer-events-none">
          <div class="text-9xl font-black text-[#7cb342] animate-bounce-in drop-shadow-cartoon">Go!</div>
        </div>

        <!-- 게임 설명 -->
        <div v-if="description"
          class="absolute top-6 left-1/2 -translate-x-1/2 text-lg font-bold text-[#8d6e63] w-full text-center px-4">
          {{ description }}
        </div>

        <!-- 타이머 -->
        <div v-if="showMiniTimer" class="absolute top-6 right-8 text-2xl font-black text-[#5d4037]">
          {{ miniTimeLeft }}s
        </div>

        <!-- 각 미니게임 컴포넌트 -->
        <MashGame v-if="gameType === 'mash'" :is-started="isStarted" :time-limit-ms="effectiveTimeLimitMs"
          @end="handleGameEnd" />
        <SequenceGame v-if="gameType === 'sequence'" :is-started="isStarted" :time-limit-ms="effectiveTimeLimitMs"
          @end="handleGameEnd" />
        <StopwatchGame v-if="gameType === 'stopwatch'" :is-started="isStarted" @end="handleGameEnd" />
      </div>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-jua {
  font-family: 'Jua', sans-serif;
}

/* 플레이어 카드 스타일 (종이/스티커 느낌) */
.player-card-cartoon {
  background: #fff8e1;
  border: 4px solid #bcaaa4;
  border-radius: 16px;
  box-shadow: 5px 5px 0 rgba(0, 0, 0, 0.2);
  transform: rotate(-2deg);
}

.player-card-cartoon:nth-child(even) {
  transform: rotate(2deg);
  background: #fff3e0;
  /* 살짝 다른 색 */
}

/* 게임 보드 스타일 (나무 판자 위에 종이) */
.game-board-cartoon {
  background: #ffffff;
  border: 8px solid #8d6e63;
  border-radius: 40px;
  box-shadow:
    0 15px 0 #5d4037,
    /* 입체 두께 */
    0 25px 40px rgba(0, 0, 0, 0.3);
  /* 그림자 */
}

/* 텍스트 그림자 */
.drop-shadow-cartoon {
  text-shadow: 3px 3px 0 #fff, 6px 6px 0 rgba(0, 0, 0, 0.1);
}

/* 애니메이션 */
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.animate-pop {
  animation: popIn 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes popIn {
  0% {
    transform: scale(0) rotate(0deg);
    opacity: 0;
  }

  70% {
    transform: scale(1.2) rotate(15deg);
  }

  100% {
    transform: scale(1) rotate(12deg);
    opacity: 1;
  }
}

.animate-ping-slow {
  animation: pingCustom 1s infinite;
}

@keyframes pingCustom {
  0% {
    transform: scale(1);
    opacity: 1;
  }

  100% {
    transform: scale(1.3);
    opacity: 0;
  }
}

.animate-bounce-in {
  animation: bounceIn 0.5s both;
}

@keyframes bounceIn {
  0% {
    opacity: 0;
    transform: scale(0.3);
  }

  50% {
    opacity: 1;
    transform: scale(1.1);
  }

  100% {
    transform: scale(1);
  }
}
</style>