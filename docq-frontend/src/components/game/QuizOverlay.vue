<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'

const gameStore = useGameStore()
const roomStore = useRoomStore()
const userStore = useUserStore()

const quiz = computed(() => gameStore.currentQuiz)
const myPlayerId = computed(() => userStore.playerId)
// MIRROR 효과 (문제 방해하기) - 턴 플레이어에게만 적용
const myPlayerState = computed(() => {
  if (!myPlayerId.value) return null
  return gameStore.gameState.players.find(p => p.playerId === myPlayerId.value) || null
})
const isMirrorMode = computed(() => quiz.value?.quizStyle === 'MIRROR')
const shouldMirror = computed(() => isMyTurn.value && isMirrorMode.value)
// currentTurnPlayerId = 게임 참여자 ID (playerId)
const isMyTurn = computed(() => gameStore.gameState.currentTurnPlayerId === myPlayerId.value)

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
  if (!myPlayerId.value || hasSubmitted.value || !canAnswer.value) return

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
    <div v-if="gameStore.turnPlayerFeedback" class="turn-feedback-overlay"
      :class="gameStore.turnPlayerFeedback === 'correct' ? 'correct' : 'wrong'">
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
        <div class="timer-bar" :style="{ width: `${timePercentage}%` }"
          :class="{ 'urgent': gameStore.timeRemaining <= 2 }"></div>
      </div>
    </div>

    <!-- MIRROR 디버프 표시 (퀴즈 방해하기 적용된 플레이어 전용) -->
    <div v-if="shouldMirror" class="mirror-badge">
      <span class="mirror-emoji">👻</span>
      <span class="mirror-text-label">문제 방해 효과가 적용된 상태입니다.</span>
    </div>

    <!-- 문제 카드 -->
    <div class="question-card">
      <div class="header-badge">
        <span class="round-badge">Round {{ gameStore.gameState.round || 1 }}</span>
      </div>
      <h2 class="question-text" :class="{ 'mirror-text': shouldMirror }">{{ quiz.question }}</h2>
      <div class="timer-text-display">남은 시간: {{ gameStore.timeRemaining.toFixed(1) }}s</div>
    </div>

    <!-- 보기 버튼 영역 -->
    <div v-if="optionsAvailable" class="options-grid">
      <button v-for="(option, idx) in quiz.options" :key="idx" class="option-btn" :class="{
        'selected': mySubmission?.answer === idx,
        'disabled': hasSubmitted,
        'correct-answer': showCorrectAnswer && correctAnswerIndex === idx
      }" @click="handleAnswer(idx)" :disabled="hasSubmitted || !canAnswer">
        <span class="option-text" :class="{ 'mirror-text': shouldMirror }">{{ option }}</span>
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
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.quiz-overlay-container {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  pointer-events: auto;
  padding: 20px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(5px);
  font-family: 'Jua', sans-serif;
  color: #5d4037;
}

.status-header {
  width: 100%;
  max-width: 800px;
  margin-bottom: 20px;
  text-align: center;
}

/* 턴 피드백 (맞춤/틀림) */
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
  padding: 40px 60px;
  border-radius: 30px;
  border: 6px solid white;
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.3);
  animation: feedbackPop 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.turn-feedback-overlay.correct {
  background: #7cb342;
  /* Leaf Green */
  color: white;
  text-shadow: 2px 2px 0 #33691e;
}

.turn-feedback-overlay.wrong {
  background: #ef5350;
  /* Red */
  color: white;
  text-shadow: 2px 2px 0 #b71c1c;
}

.feedback-icon {
  font-size: 5rem;
  margin-bottom: 10px;
  filter: drop-shadow(0 4px 0 rgba(0, 0, 0, 0.2));
}

.feedback-text {
  font-size: 2.5rem;
  font-weight: 400;
}

@keyframes feedbackPop {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.5);
  }

  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

/* 턴 메시지 */
.turn-message {
  font-size: 1.6rem;
  margin-bottom: 15px;
  padding: 12px 20px;
  border-radius: 16px;
  background: #fff8e1;
  border: 4px solid #8d6e63;
  box-shadow: 0 6px 0 rgba(0, 0, 0, 0.2);
  display: inline-block;
}

.my-turn {
  color: #e65100;
  border-color: #ffb74d;
}

.other-turn {
  color: #0277bd;
  border-color: #4fc3f7;
}

/* 타이머 바 */
.timer-bar-container {
  width: 100%;
  height: 24px;
  background: #d7ccc8;
  border: 3px solid #8d6e63;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: inset 0 2px 5px rgba(0, 0, 0, 0.1);
  margin-top: 10px;
}

.timer-bar {
  height: 100%;
  background: #7cb342;
  transition: width 0.2s linear, background 0.3s;
  box-shadow: inset 0 2px 0 rgba(255, 255, 255, 0.3);
}

.timer-bar.urgent {
  background: #ff7043;
}

/* MIRROR 디버프 뱃지 */
.mirror-badge {
  margin: 0 auto 12px auto;
  padding: 10px 18px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.95);
  border: 3px solid #bfdbfe;
  color: #e0f2fe;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 6px 0 rgba(15, 23, 42, 0.6);
  font-size: 1.2rem;
}

.mirror-emoji {
  font-size: 1.6rem;
}

.mirror-text-label {
  font-weight: 700;
}

/* 질문 카드 */
.question-card {
  background: #fff8e1;
  /* Cream */
  padding: 40px;
  border-radius: 24px;
  border: 6px solid #8d6e63;
  /* Wood Dark */
  box-shadow: 0 12px 0 #5d4037, 0 20px 25px rgba(0, 0, 0, 0.2);
  margin-bottom: 40px;
  width: 100%;
  max-width: 800px;
  text-align: center;
  position: relative;
}

.round-badge {
  background: #ffecb3;
  color: #ff6f00;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 1.2rem;
  font-weight: bold;
  border: 2px solid #ffca28;
  display: inline-block;
  margin-bottom: 15px;
}

.question-text {
  color: #5d4037;
  font-size: 2.2rem;
  font-weight: 400;
  /* Jua Font Default */
  word-break: keep-all;
  line-height: 1.3;
  text-shadow: 1px 1px 0 rgba(255, 255, 255, 0.5);
}

.timer-text-display {
  margin-top: 15px;
  font-size: 1.2rem;
  color: #8d6e63;
}

/* 보기 그리드 */
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
  color: #fff8e1;
  font-size: 1.4rem;
  padding: 24px;
  background: rgba(93, 64, 55, 0.95);
  /* 가독성 위해 배경 불투명도 증가 */
  border-radius: 20px;
  border: 4px dashed #bcaaa4;
  margin-top: 30px;
  /* 겹침 방지 여백 추가 */
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.3);
  /* 그림자 추가로 레이어 구분 */
}

/* 보기 버튼 (카툰 스타일) */
.option-btn {
  background: white;
  border: 4px solid #bcaaa4;
  /* Wood Medium */
  border-radius: 20px;
  padding: 24px;
  font-size: 1.5rem;
  font-family: 'Jua', sans-serif;
  color: #5d4037;
  cursor: pointer;
  transition: all 0.1s ease;
  box-shadow: 0 6px 0 #8d6e63;
  /* Hard Shadow */
  position: relative;
  top: 0;
}

.option-btn:hover:not(.disabled) {
  background: #f1f8e9;
  border-color: #81c784;
  transform: translateY(-4px);
  box-shadow: 0 10px 0 #558b2f;
}

.option-btn:active:not(.disabled) {
  top: 6px;
  box-shadow: 0 0 0 #8d6e63;
}

.option-btn.selected {
  background: #ffcc80;
  /* Orange Light */
  border-color: #ef6c00;
  color: #e65100;
  box-shadow: 0 6px 0 #e65100 !important;
  top: 0;
}

.option-btn.correct-answer {
  background: #c5e1a5;
  /* Light Green */
  border-color: #33691e;
  color: #1b5e20;
  box-shadow: 0 6px 0 #1b5e20 !important;
}

.option-btn .correct-badge {
  display: inline-block;
  margin-left: 10px;
  padding: 4px 10px;
  background: #2ecc71;
  color: white;
  font-size: 1rem;
  border-radius: 8px;
  border: 2px solid white;
}

.option-btn.disabled {
  opacity: 0.7;
  cursor: not-allowed;
  filter: grayscale(0.5);
  box-shadow: 0 6px 0 #9e9e9e;
}

/* MIRROR 효과 (문제 방해하기) */
.mirror-text {
  display: inline-block;
  transform: scaleX(-1);
}
</style>
