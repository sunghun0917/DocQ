<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomStore'

const gameStore = useGameStore()
const roomStore = useRoomStore()

const quiz = computed(() => gameStore.currentQuiz)
const myUserId = computed(() => roomStore.myUser?.id || '')
const isMyTurn = computed(() => {
  if (!gameStore.currentTurnPlayer || !myUserId.value) return false
  return gameStore.currentTurnPlayer.userId === myUserId.value
})

// 타이머 (Progress Bar Width%)
const timePercentage = computed(() => {
  const limit = quiz.value?.timeLimit || 30
  return (gameStore.timeRemaining / limit) * 100
})

// 답안 제출 핸들러 (WebSocket 버전은 GameView에서 처리)
// 이 컴포넌트는 UI만 담당
</script>

<template>
  <div v-if="quiz && gameStore.gameStatus === 'playing'" class="quiz-overlay-container">
    
    <!-- 타이머 + 역할 메시지 -->
    <div class="status-header">
      <div v-if="isMyTurn" class="turn-message my-turn">
        당신의 턴입니다! 정답을 맞추세요!
      </div>
      <div v-else class="turn-message other-turn">
        스틸 찬스! 턴 주인이 틀리면 가장 빠른 사람에게 기회가 옵니다.
      </div>
      
      <!-- 프로그레스 바 타이머 -->
      <div class="timer-bar-container">
        <div 
          class="timer-bar" 
          :style="{ width: `${timePercentage}%` }"
          :class="{ 'urgent': gameStore.timeRemaining <= 5 }"
        ></div>
      </div>
    </div>

    <!-- 문제 카드 -->
    <div class="question-card">
      <div class="header-badge">
         <span class="round-badge">Round {{ gameStore.gameState.round || 1 }}</span>
      </div>
      <h2 class="question-text">{{ quiz.question }}</h2>
      <div class="timer-text-display">남은 시간: {{ gameStore.timeRemaining.toFixed(1) }}s</div>
    </div>

    <!-- 보기 버튼 영역 (실제 제출은 GameView에서 처리) -->
    <div class="options-grid">
      <button
        v-for="(option, idx) in quiz.options"
        :key="idx"
        class="option-btn"
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
  pointer-events: auto;
  padding: 20px;
  background: rgba(0, 0, 0, 0.3);
}

.status-header {
  width: 100%;
  max-width: 800px;
  margin-bottom: 20px;
  text-align: center;
}

.turn-message {
  font-size: 1.5rem;
  font-weight: 800;
  margin-bottom: 15px;
  text-shadow: 0 2px 4px rgba(0,0,0,0.5);
  padding: 10px;
  border-radius: 10px;
}

.my-turn {
  color: #fab1a0;
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid #fab1a0;
}

.other-turn {
  color: #74b9ff;
  background: rgba(255, 255, 255, 0.1);
}

.timer-bar-container {
  width: 100%;
  height: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 10px;
  overflow: hidden;
  box-shadow: inset 0 2px 5px rgba(0,0,0,0.2);
}

.timer-bar {
  height: 100%;
  background: #00b894;
  transition: width 1s linear, background 0.3s;
}

.timer-bar.urgent {
  background: #ff7675;
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

.timer-text-display {
  margin-top: 10px;
  font-size: 1rem;
  color: #636e72;
  font-weight: bold;
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
</style>
