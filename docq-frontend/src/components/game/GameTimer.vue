<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'

// 전역 상태 연동
const gameStore = useGameStore()
const timeRemaining = computed(() => gameStore.timeRemaining)

// 5초 이하일 때 붉은색으로 강조
const isUrgent = computed(() => timeRemaining.value <= 5)

// 원형 진행도 계산 (30초 기준)
// 113: timer 원의 전체 둘레 길이
// stroke-dasharray: 2 * PI * r (r=18 => approx 113)
const FULL_DASH_ARRAY = 113
const progress = computed(() => {
  const total = 30 // 전체 시간
  const current = timeRemaining.value // 현재 시간
  const fraction = current / total // 현재 시간 / 전체 시간
  return (fraction * FULL_DASH_ARRAY).toFixed(0) // toFixed(0): 소수점 아래 버림
})
</script>

<template>
  <div class="timer-container" :class="{ urgent: isUrgent }">
    <!-- SVG 원형 타이머 배경 -->
    <svg class="timer-svg" viewBox="0 0 40 40">
      <circle class="timer-path-base" cx="20" cy="20" r="18" />
      <circle
        class="timer-path-remaining"
        :stroke-dasharray="`${progress} ${FULL_DASH_ARRAY}`"
        cx="20"
        cy="20"
        r="18"
      />
    </svg>
    <div class="timer-text">{{ timeRemaining }}</div>
  </div>
</template>

<style scoped>
.timer-container {
  position: relative;
  width: 80px;
  height: 80px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-family: 'Pretendard', sans-serif;
  font-weight: 900;
  font-size: 2rem;
  color: white;
  filter: drop-shadow(0 4px 6px rgba(0,0,0,0.3));
}

.timer-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg); /* 12시 방향부터 시작 */
}

.timer-path-base {
  fill: none;
  stroke: rgba(255, 255, 255, 0.2);
  stroke-width: 4px;
}

.timer-path-remaining {
  fill: none;
  stroke: #00cec9; /* 기본 청록색 */
  stroke-width: 4px;
  stroke-linecap: round;
  transition: stroke-dasharray 1s linear, stroke 0.3s;
}

/* 긴박한 상태 (5초 이하) */
.urgent .timer-path-remaining {
  stroke: #ff7675; /* 붉은색 */
}
.urgent .timer-text {
  color: #ff7675;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}
</style>
