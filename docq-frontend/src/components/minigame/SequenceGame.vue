<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue';

const props = defineProps<{ isStarted: boolean; timeLimitMs?: number }>();
const emit = defineEmits(['end']);

const directions = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];

const targetSequence = ref<string[]>(
  Array.from({ length: 18 }, () => directions[Math.floor(Math.random() * 4)] as string)
);

const currentIndex = ref(0);
const isWrong = ref(false);
const isEnded = ref(false);
const timeLeft = ref(10);
let startTime = 0;
let timer: number | null = null;

const startGame = () => {
  if (isEnded.value) return;
  startTime = performance.now();
  window.addEventListener('keydown', handleKeydown);

  const limitMs = props.timeLimitMs ?? 10000;
  timeLeft.value = Math.max(1, Math.ceil(limitMs / 1000));
  if (timer) clearInterval(timer);
  timer = window.setInterval(() => {
    timeLeft.value -= 1;
    if (timeLeft.value <= 0) {
      endGame(false);
    }
  }, 1000);
};

const handleKeydown = (e: KeyboardEvent) => {
  if (isEnded.value || currentIndex.value >= targetSequence.value.length) return;

  // 1. 정답 로직
  if (e.key === targetSequence.value[currentIndex.value]) {
    currentIndex.value++;

    // 성공 체크
    if (currentIndex.value === targetSequence.value.length) {
      endGame(true);
    }
  }
  // 2. 오답 로직 (단, 방향키를 눌렀을 때만)
  else if (directions.includes(e.key)) {
    isWrong.value = true;
    setTimeout(() => isWrong.value = false, 300);

    // [수정 3] 페널티 추가! 틀리면 3칸 뒤로 후퇴 (최소 0까지만)
    // 이 한 줄이 게임 난이도를 확 올립니다.
    currentIndex.value = Math.max(0, currentIndex.value - 2);
  }
};

const endGame = (success: boolean) => {
  if (isEnded.value) return;
  isEnded.value = true;
  window.removeEventListener('keydown', handleKeydown);
  if (timer) {
    clearInterval(timer);
    timer = null;
  }

  if (success) {
    const elapsedTime = (performance.now() - startTime) / 1000;
    emit('end', elapsedTime);
  } else {
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

const icons: Record<string, string> = { ArrowUp: '⬆', ArrowDown: '⬇', ArrowLeft: '⬅', ArrowRight: '➡' };
</script>

<template>
  <div class="text-center w-full h-full min-h-[400px] flex flex-col justify-center items-center gap-8 font-jua">
    <h2 class="text-4xl font-black text-[#5d4037] mb-12 drop-shadow-sm">
      방향을 빠르게 입력하세요!
    </h2>

    <div class="grid grid-cols-6 gap-4 mb-8 transition-transform w-full max-w-xl mx-auto"
      :class="{ 'animate-shake': isWrong }">
      <div v-for="(key, idx) in targetSequence" :key="idx"
        class="aspect-square rounded-2xl flex items-center justify-center text-4xl border-4 transition-all duration-200"
        :class="[
          // 완료됨: 초록색 배경, 흐릿하게, 그림자 없음
          idx < currentIndex
            ? 'bg-[#c5e1a5] border-[#558b2f] text-[#33691e] opacity-50 scale-90'
            : '',

          // 현재 차례: 흰 배경, 주황색 테두리, 그림자, 확대
          idx === currentIndex
            ? 'bg-white border-[#ff7043] scale-110 shadow-cartoon-orange z-10 text-[#e64a19]'
            : '',

          // 아직 안 함: 기본 나무색 테두리, 흰 배경
          idx > currentIndex
            ? 'bg-[#fff8e1] border-[#bcaaa4] text-[#8d6e63]'
            : ''
        ]">
        <span class="!text-5xl font-bold">
          {{ icons[key] }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.font-jua {
  font-family: 'Jua', sans-serif;
}

.shadow-cartoon-orange {
  box-shadow: 0 8px 0 #d84315, 0 15px 20px rgba(0, 0, 0, 0.2);
}

.animate-shake {
  animation: shake 0.3s cubic-bezier(.36, .07, .19, .97) both;
}

@keyframes shake {

  10%,
  90% {
    transform: translate3d(-2px, 0, 0);
  }

  20%,
  80% {
    transform: translate3d(4px, 0, 0);
  }

  30%,
  50%,
  70% {
    transform: translate3d(-6px, 0, 0);
  }

  40%,
  60% {
    transform: translate3d(6px, 0, 0);
  }
}
</style>