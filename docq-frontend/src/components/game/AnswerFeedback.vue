<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'

const gameStore = useGameStore()

// 결과 상태 (null이면 표시 안 함)
const result = computed(() => gameStore.lastAnswerResult)

// 메시지 및 스타일 결정
const isCorrect = computed(() => result.value === true)

// 애니메이션용 클래스
const feedbackClass = computed(() => isCorrect.value ? 'correct' : 'wrong')
</script>

<template>
  <div v-if="result !== null" class="feedback-overlay" :class="feedbackClass">
    <div class="content">
      <div class="icon">
        {{ isCorrect ? '⭕' : '❌' }}
      </div>
      <h1 class="message">
        {{ isCorrect ? '정답입니다!' : '오답입니다...' }}
      </h1>
      <p v-if="!isCorrect" class="sub-message">
        (다음 문제로 넘어갑니다)
      </p>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.feedback-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(5px);
  animation: fadeIn 0.3s ease-out;
  font-family: 'Jua', sans-serif;
}

.correct {
  background: rgba(124, 179, 66, 0.9);
  /* Leaf Green */
}

.wrong {
  background: rgba(239, 83, 80, 0.9);
  /* Cartoon Red */
}

.content {
  text-align: center;
  color: white;
  transform: scale(0.8);
  animation: popup 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;

  background: rgba(255, 255, 255, 0.2);
  padding: 40px 60px;
  border-radius: 30px;
  border: 8px solid white;
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.2);
}

.icon {
  font-size: 6rem;
  margin-bottom: 20px;
  filter: drop-shadow(0 5px 0 rgba(0, 0, 0, 0.2));
}

.message {
  font-size: 4rem;
  font-weight: 400;
  /* Jua Default */
  text-transform: uppercase;
  text-shadow: 3px 3px 0 rgba(0, 0, 0, 0.2);
  margin: 0;
  line-height: 1.2;
}

.sub-message {
  font-size: 1.5rem;
  margin-top: 15px;
  opacity: 0.9;
  font-weight: 400;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@keyframes popup {
  to {
    transform: scale(1);
  }
}
</style>
