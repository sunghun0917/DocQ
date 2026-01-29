<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomStore'

const gameStore = useGameStore()
const roomStore = useRoomStore()

const quiz = computed(() => gameStore.currentQuiz)
const myUserId = computed(() => roomStore.myUser?.id || '')
// Access currentTurnUserId via gameState since the getter was removed from store
const isMyTurn = computed(() => gameStore.gameState.currentTurnUserId === myUserId.value)

// Local state for submission feedback since store doesn't support it yet
const localSelectedAnswer = ref<number | null>(null)

// Computed property to match template expectation
const mySubmission = computed(() => 
  localSelectedAnswer.value !== null ? { answer: localSelectedAnswer.value } : null
)

const hasSubmitted = computed(() => localSelectedAnswer.value !== null)
const optionsAvailable = computed(() => (quiz.value?.options?.length ?? 0) > 0)
const canAnswer = computed(() => {
  if (!optionsAvailable.value) return false
  if (isMyTurn.value && gameStore.ghostPhaseActive) return false
  return true
})

// 타이머 (Progress Bar Width%)
const timePercentage = computed(() => {
  const limit = quiz.value?.timeLimit ?? 1
  const ratio = limit > 0 ? (gameStore.timeRemaining / limit) * 100 : 0
  return Math.min(100, Math.max(0, ratio))
})

// 누군가 맞췄을 때 정답 보기 인덱스 (결과가 있으면 정답 표시)
const correctAnswerIndex = computed(() => {
  const result = gameStore.currentRoundResult
  const opts = quiz.value?.options
  if (!result?.correctAnswer || !opts?.length) return null
  const idx = opts.findIndex((o: string) => o === result.correctAnswer)
  return idx >= 0 ? idx : null
})
const showCorrectAnswer = computed(() => correctAnswerIndex.value !== null)

const emit = defineEmits(['submit'])

// 답안 제출 핸들러
const handleAnswer = (index: number) => {
  if (!myUserId.value || hasSubmitted.value || !canAnswer.value) return
  
  // Update local visual state
  localSelectedAnswer.value = index
  
  // Emit event for parent to handle (WebSocket connection)
  emit('submit', index)
}

// [FIX] Reset local state when quiz changes
watch(quiz, () => {
    localSelectedAnswer.value = null
})
</script>

<template>
  <div v-if="quiz && !gameStore.rouletteTargetId" class="quiz-overlay-container">
    
    <!-- 턴 플레이어/고스트 공통: 맞췄습니다 / 틀렸습니다 -->
    <div
      v-if="gameStore.turnPlayerFeedback"
      class="turn-feedback-overlay"
      :class="gameStore.turnPlayerFeedback === 'correct' ? 'correct' : 'wrong'"
    >
      <span class="feedback-icon">{{ gameStore.turnPlayerFeedback === 'correct' ? '⭕' : '❌' }}</span>
      <span class="feedback-text">
        {{ gameStore.turnPlayerFeedback === 'correct' ? '맞췄습니다!' : '틀렸습니다.' }}
      </span>
    </div>

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
          :class="{ 'urgent': gameStore.timeRemaining <= 2 }"
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

    <!-- 보기 버튼 영역 -->
    <div v-if="optionsAvailable" class="options-grid">
      <button
        v-for="(option, idx) in quiz.options"
        :key="idx"
        class="option-btn"
        :class="{
          'selected': mySubmission?.answer === idx,
          'disabled': hasSubmitted,
          'correct-answer': showCorrectAnswer && correctAnswerIndex === idx
        }"
        @click="handleAnswer(idx)"
        :disabled="hasSubmitted || !canAnswer"
      >
        <span class="option-text">{{ option }}</span>
        <span v-if="showCorrectAnswer && correctAnswerIndex === idx" class="correct-badge">✓ 정답</span>
      </button>
    </div>
    <div v-else class="options-waiting">
      <span v-if="isMyTurn">보기를 불러오는 중입니다. 잠시만 기다려 주세요.</span>
      <span v-else>턴 플레이어가 푸는 중입니다. 보기가 공개되면 빠르게 선택하세요!</span>
    </div>
    <div v-if="optionsAvailable && isMyTurn && gameStore.ghostPhaseActive" class="options-waiting">
      턴 시간이 종료되었습니다. 고스트가 푸는 동안 잠시 기다려 주세요.
    </div>
  </div>
</template>

<style scoped>
.quiz-overlay-container {
  position: absolute; /* Added overlay positioning */
  top: 0;
  left: 0;
  z-index: 100; /* Higher than UI overlay (10), lower than Roulette (2000) */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  pointer-events: auto;
  padding: 20px;
  background: rgba(0, 0, 0, 0.3); /* 배경 어둡게 */
}

.status-header {
  width: 100%;
  max-width: 800px;
  margin-bottom: 20px;
  text-align: center;
}

.turn-feedback-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 150;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 48px;
  border-radius: 24px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  animation: feedbackPop 0.35s ease-out;
}
.turn-feedback-overlay.correct {
  background: rgba(46, 204, 113, 0.95);
  color: white;
  border: 4px solid rgba(255,255,255,0.8);
}
.turn-feedback-overlay.wrong {
  background: rgba(231, 76, 60, 0.95);
  color: white;
  border: 4px solid rgba(255,255,255,0.8);
}
.feedback-icon {
  font-size: 4rem;
  margin-bottom: 8px;
  filter: drop-shadow(0 2px 8px rgba(0,0,0,0.3));
}
.feedback-text {
  font-size: 2rem;
  font-weight: 900;
  text-shadow: 0 2px 6px rgba(0,0,0,0.3);
}
@keyframes feedbackPop {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.7);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
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
  transition: width 0.25s linear, background 0.3s;
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

.options-waiting {
  width: 100%;
  max-width: 800px;
  text-align: center;
  color: #ffffff;
  font-size: 1.1rem;
  font-weight: 700;
  text-shadow: 0 2px 4px rgba(0,0,0,0.4);
  padding: 16px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.4);
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

.option-btn:hover:not(.disabled) {
  background: #74b9ff;
  border-color: #74b9ff;
  color: white;
  transform: translateY(-3px);
  box-shadow: 0 7px 14px rgba(0,0,0,0.2);
}

.option-btn:active:not(.disabled) {
  transform: translateY(1px);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.option-btn.selected {
  background: #ffeaa7;
  border-color: #fdcb6e;
  color: #d35400;
}

.option-btn.correct-answer {
  background: #d5f5e3;
  border-color: #2ecc71;
  color: #1e8449;
  box-shadow: 0 0 0 3px rgba(46, 204, 113, 0.4);
}

.option-btn .option-text {
  display: block;
  text-align: left;
}

.option-btn .correct-badge {
  display: inline-block;
  margin-top: 8px;
  padding: 4px 12px;
  background: #2ecc71;
  color: white;
  font-size: 0.95rem;
  font-weight: 800;
  border-radius: 8px;
}

.option-btn.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}
</style>
