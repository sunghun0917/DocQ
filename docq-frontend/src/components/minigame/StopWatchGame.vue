<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue';

// [수정] 부모로부터 시작 신호를 받음
const props = defineProps<{ isStarted: boolean }>();
const emit = defineEmits(['end']);

const time = ref(0);
const isRunning = ref(false);
const isFinished = ref(false);
let intervalId: number | null = null;
let startTime = 0;

// [핵심] 타이머 시작 로직 (버튼 클릭 없이 신호에 의해 실행)
const startTimer = () => {
  if (isRunning.value || isFinished.value) return;

  isRunning.value = true;
  startTime = performance.now();

  const update = () => {
    if (!isRunning.value) return;
    time.value = (performance.now() - startTime) / 1000;
    intervalId = requestAnimationFrame(update);
  };

  intervalId = requestAnimationFrame(update);
};

// StopwatchGame.vue

const stopTimer = () => {
  if (!isRunning.value) return;

  isRunning.value = false;
  isFinished.value = true;
  if (intervalId) cancelAnimationFrame(intervalId);

  // [수정] 점수(100, 80...)가 아니라 '5.00초와의 오차(diff)'를 계산
  // 예: 5.02초에 멈춤 -> 0.02 전달 (작을수록 1등)
  const diff = Math.abs(5.00 - time.value);

  // 부모에게 오차값 전달
  emit('end', diff);
};

const displayTime = (t: number) => {
  return t.toFixed(2);
};

watch(() => props.isStarted, (newValue) => {
  if (newValue) {
    startTimer();
  }
});

// 혹시 모를 상황을 대비한 마운트 시 체크
onMounted(() => {
  if (props.isStarted) startTimer();
});

onUnmounted(() => {
  if (intervalId) cancelAnimationFrame(intervalId);
});
</script>

<template>
  <div class="text-center w-full font-jua">
    <h2 class="text-4xl font-black text-[#5d4037] mb-8 drop-shadow-sm">5.00초에 멈춰라! ⏱️</h2>

    <div class="text-9xl font-black mb-12 tabular-nums tracking-tighter transition-all duration-500" :class="[
      // 1. 블러 효과 적용 조건: 게임 중이고(isRunning) 3초가 넘었을 때
      (isRunning && time > 3.0) ? 'blur-xl select-none opacity-40 text-[#8d6e63]' : '',

      // 2. 게임 종료 후 색상 처리
      isFinished
        ? (Math.abs(5.00 - time) < 0.1 ? 'text-[#2e7d32]' : 'text-[#d32f2f]')
        : 'text-[#5d4037]'
    ]">
      {{ isFinished ? time.toFixed(2) : displayTime(time) }}
      <span class="text-4xl text-[#bcaaa4] ml-2">sec</span>
    </div>

  </div>
  <div class="h-24 flex items-center justify-center">
    <button v-if="isRunning" @click="stopTimer"
      class="px-12 py-6 text-4xl font-black text-white bg-[#ef5350] rounded-full shadow-[0_10px_0_0_#b71c1c] active:translate-y-2 active:shadow-none transition-all hover:bg-[#e53935]">
      STOP!
    </button>

    <div v-else-if="!isFinished" class="text-[#7cb342] font-black text-2xl animate-bounce">
      준비하세요...
    </div>

    <div v-else class="text-[#5d4037] font-black text-2xl">
      측정 완료!
    </div>
  </div>
</template>

<style scoped>
.font-jua {
  font-family: 'Jua', sans-serif;
}
</style>