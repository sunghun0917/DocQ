<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue';

const props = defineProps<{ isStarted: boolean; timeLimitMs?: number }>();
const emit = defineEmits(['end']);

const progress = ref(0);
const timeLeft = ref(10);
const isEnded = ref(false);
const elapsedTime = ref(0); // [추가] 최종 소요 시간 저장용
const lastKey = ref('');

// [추가] 시작 시간 기록용
let startTime = 0;
let timer: number | null = null;

const startGame = () => {
  if (props.timeLimitMs) {
    timeLeft.value = Math.max(1, Math.ceil(props.timeLimitMs / 1000));
  }
  startTime = performance.now(); // [핵심] 시작 시간 기록
  window.addEventListener('keydown', handleKeydown);
  
  timer = setInterval(() => {
    timeLeft.value--;
    if (timeLeft.value <= 0) {
      if (timer) clearInterval(timer);
      if (!isEnded.value) endGame(false);
    }
  }, 1000);
};

const handleKeydown = (e: KeyboardEvent) => {
  if (isEnded.value || e.repeat) return;

  if ((e.key === 'ArrowLeft' && lastKey.value !== 'left') || 
      (e.key === 'ArrowRight' && lastKey.value !== 'right')) {
    
    progress.value = Math.min(100, progress.value + 5); // [팁] 테스트를 위해 증가량 살짝 높임 (2->5)
    lastKey.value = e.key === 'ArrowLeft' ? 'left' : 'right';

    if (progress.value >= 100) endGame(true);
  }
};

const endGame = (success: boolean) => {
  isEnded.value = true;
  window.removeEventListener('keydown', handleKeydown);
  if (timer) clearInterval(timer);

  if (success) {
    // [핵심] 성공 시 '걸린 시간'을 계산 및 저장
    elapsedTime.value = (performance.now() - startTime) / 1000;
    emit('end', elapsedTime.value); 
  } else {
    // [핵심] 실패 시 아주 큰 숫자(999초)를 보내서 패배 처리
    emit('end', 999);
  }
};

watch(() => props.isStarted, (newValue) => {
  if (newValue) startGame();
});

onMounted(() => {
  if (props.isStarted) startGame();
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="text-center w-full h-full flex flex-col justify-center gap-8">
    <h2 class="text-3xl font-black text-[#2d3436] mb-8">좌우로 마구 연타해!! 🔥</h2>
    
    <div class="text-6xl font-black mb-4" :class="{'text-red-500': timeLeft <= 3}">
      {{ isEnded && progress >= 100 ? elapsedTime.toFixed(2) + 's' : timeLeft }}
    </div>

    <div class="w-full h-16 bg-[#dfe6e9] rounded-full overflow-hidden border-4 border-[#b2bec3] relative mb-8">
      <div 
        class="h-full bg-[#ff7675] transition-all duration-75 flex items-center justify-end pr-4"
        :style="{ width: `${progress}%` }"
      >
        <span class="text-white font-bold">{{ progress }}%</span>
      </div>
    </div>
    
    <div class="flex justify-center gap-8 text-4xl text-[#636e72]">
      <div :class="{ 'text-[#ff7675] scale-125': lastKey === 'left' }" class="transition-transform">⬅️</div>
      <div :class="{ 'text-[#ff7675] scale-125': lastKey === 'right' }" class="transition-transform">➡️</div>
    </div>
  </div>
</template>