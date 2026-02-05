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

    // [ADD] 10초가 지나면 자동 종료 (실패 처리 or 그냥 멈춤)
    if (time.value >= 10.0) {
      time.value = 10.0; // [FIX] 보정 먼저 수행 (계산에 10.00을 사용하기 위함)
      stopTimer();
      return;
    }
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

  // [수정] 7.77초와의 오차(diff)를 기본 점수(클수록 나쁨)로 전달
  const diff = Math.abs(7.77 - time.value);

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
  <div class="w-full h-full flex flex-col justify-between font-jua">
    
    <div class="flex-1 flex flex-col items-center justify-center w-full">
      <h2 class="text-4xl font-black text-[#5d4037] mb-8 drop-shadow-sm">7.77초에 멈추세요!</h2>

      <div class="text-9xl font-black mb-12 tabular-nums tracking-tighter transition-all duration-500" :class="[
        (isRunning && time > 5.0) ? 'blur-xl select-none opacity-40 text-[#8d6e63]' : '',
        isFinished
          ? (Math.abs(7.77 - time) < 0.1 ? 'text-[#2e7d32]' : 'text-[#d32f2f]')
          : 'text-[#5d4037]'
      ]">
        {{ isFinished ? time.toFixed(2) : displayTime(time) }}
        <span class="text-4xl text-[#bcaaa4] ml-2">sec</span>
      </div>
    </div>

    <div class="h-40 shrink-0 flex items-center justify-center w-full">
      
      <button v-if="isRunning" @click="stopTimer"
        class="w-32 h-32 rounded-full text-4xl font-black text-white bg-[#ef5350] shadow-[0_10px_0_0_#b71c1c] active:translate-y-2 active:shadow-none transition-all hover:bg-[#e53935] flex items-center justify-center border-4 border-[#b71c1c]">
        STOP
      </button>

      <div v-else-if="!isFinished" 
        class="w-32 h-32 rounded-full text-3xl font-black text-white bg-[#ef5350] shadow-[0_10px_0_0_#b71c1c] flex items-center justify-center border-4 border-[#b71c1c] cursor-default opacity-90">
        대기
      </div>

      <div v-else 
        class="w-32 h-32 rounded-full text-white bg-[#ef5350] shadow-[0_10px_0_0_#b71c1c] flex flex-col items-center justify-center border-4 border-[#b71c1c] cursor-default">
        <span class="text-2xl font-black leading-none">측정</span>
        <span class="text-2xl font-black leading-none">완료</span>
      </div>
      
    </div>

  </div>
</template>

<style scoped>
.font-jua {
  font-family: 'Jua', sans-serif;
}
</style>