<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'

// 게임 스토어 가져오기
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
.feedback-overlay {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(5px);
  animation: fadeIn 0.3s ease-out;
}

.correct {
  background: rgba(46, 204, 113, 0.85); /* Green */
}

.wrong {
  background: rgba(231, 76, 60, 0.85); /* Red */
}

.content {
  text-align: center;
  color: white;
  transform: scale(0.8);
  animation: popup 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
}

.icon {
  font-size: 8rem;
  margin-bottom: 20px;
  filter: drop-shadow(0 5px 15px rgba(0,0,0,0.3));
}

.message {
  font-size: 3rem;
  font-weight: 900;
  text-transform: uppercase;
  text-shadow: 0 4px 10px rgba(0,0,0,0.3);
}

.sub-message {
  font-size: 1.2rem;
  margin-top: 10px;
  opacity: 0.9;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes popup {
  to { transform: scale(1); }
}
</style>
