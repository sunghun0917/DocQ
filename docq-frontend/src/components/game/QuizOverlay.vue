<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomtStore' // roomtStore? 오타 같은데 기존 파일명 따름
import GameTimer from '@/components/game/GameTimer.vue'

const gameStore = useGameStore()
const roomStore = useRoomStore()

const quiz = computed(() => gameStore.currentQuiz)
const myUserId = computed(() => roomStore.myUser?.id || '')

// 답안 제출 핸들러
const handleAnswer = (index: number) => {
  if (!myUserId.value) {
    console.error('User ID not found')
    return
  }
  gameStore.submitAnswer(myUserId.value, index)
}
</script>

<template>
  <div v-if="quiz" class="quiz-overlay-container">
    <!-- 타이머 (문제 화면 상단) -->
    <div class="timer-area">
      <GameTimer />
    </div>

    <!-- 문제 카드 -->
    <div class="question-card">
      <h2 class="question-text">{{ quiz.question }}</h2>
    </div>

    <!-- 보기 버튼 영역 -->
    <div class="options-grid">
      <button
        v-for="(option, idx) in quiz.options"
        :key="idx"
        class="option-btn"
        @click="handleAnswer(idx)"
      >
        {{ option }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.quiz-overlay-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  pointer-events: auto; /* 클릭 가능하도록 */
  padding: 20px;
}

.timer-area {
  margin-bottom: 20px;
}

.question-card {
  background: rgba(255, 255, 255, 0.95);
  padding: 30px 40px;
  border-radius: 20px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.2);
  margin-bottom: 40px;
  width: 100%;
  max-width: 800px;
  text-align: center;
}

.question-text {
  color: #2d3436;
  font-size: 1.8rem;
  font-weight: 800;
  word-break: keep-all;
  line-height: 1.4;
}

.options-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  width: 100%;
  max-width: 800px;
}

.option-btn {
  background: white;
  border: 3px solid #dfe6e9;
  border-radius: 15px;
  padding: 20px;
  font-size: 1.2rem;
  font-weight: 700;
  color: #636e72;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
}

.option-btn:hover {
  background: #74b9ff;
  border-color: #74b9ff;
  color: white;
  transform: translateY(-3px);
  box-shadow: 0 7px 14px rgba(0,0,0,0.2);
}

.option-btn:active {
  transform: translateY(1px);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
</style>
