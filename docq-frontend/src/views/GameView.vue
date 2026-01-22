<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router'
import ResultModal from '@/components/game/ResultModal.vue';
import GameBoard from '@/components/game/board3D/GameBoard.vue';
import QuizOverlay from '@/components/game/QuizOverlay.vue';
import AnswerFeedback from '@/components/game/AnswerFeedback.vue';
import RouletteOverlay from '@/components/game/RouletteOverlay.vue';
import { useRoomStore } from '@/stores/roomStore'
import { useGameStore } from '@/stores/gameStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const roomStore = useRoomStore()
const gameStore = useGameStore()
const wsStore = useWebSocketStore()
const userStore = useUserStore()

const myUserId = computed(() => userStore.userId)
const currentQuiz = computed(() => gameStore.currentQuiz)
const timeRemaining = computed(() => gameStore.timeRemaining)
const gameStatus = computed(() => gameStore.gameStatus)
const isGameFinished = computed(() => gameStore.isGameFinished)

// 현재 턴인지 확인
const currentTurnPlayer = computed(() => gameStore.currentTurnPlayer)
const isMyTurn = computed(() => {
    if (!currentTurnPlayer.value || !myUserId.value) return false
    return currentTurnPlayer.value.userId === myUserId.value
})

const selectedOption = ref<number | null>(null)
const isSubmitted = ref(false)

// 퀴즈가 바뀌면 상태 초기화
watch(currentQuiz, () => {
    selectedOption.value = null
    isSubmitted.value = false
})

const submitAnswer = (index: number) => {
    if (isSubmitted.value) return
    if (!currentQuiz.value) return
    
    selectedOption.value = index
    isSubmitted.value = true
    
    const answerText = currentQuiz.value.options[index]
    
    const roomId = roomStore.roomInfo.roomId
    if (roomId && myUserId.value) {
        wsStore.submitAnswer(roomId, myUserId.value, currentQuiz.value.quizId, answerText || "")
    }
}
</script>

<template>
  <div class="game-page">
    <!-- 3D 보드게임 컴포넌트 (배경) -->
    <GameBoard />

    <!-- UI 오버레이: 3D 캔버스 위에 텍스트나 모달 등을 띄움 -->
    <div class="ui-overlay">
      <!-- 퀴즈 오버레이 (타이머 + 문제 + 보기) -->
      <QuizOverlay class="quiz-contents" />

      <!-- 정답/오답 피드백 오버레이 -->
      <AnswerFeedback />

      <!-- 룰렛 오버레이 -->
      <RouletteOverlay />

      <!-- 결과 모달 -->
      <ResultModal v-if="isGameFinished" class="pointer-events-auto" />
    </div>
  </div>
</template>

<style scoped>
/* 전체 화면을 채우는 컨테이너 */
.game-page {
  width: 100vw;
  height: 100vh;
  background: #222; /* 3D 캔버스 로딩 전 배경색 */
  position: relative;
  overflow: hidden;
}

/* UI 오버레이 스타일 */
.ui-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 10;     /* 3D 캔버스보다 위에 표시 */
  pointer-events: none; /* 클릭 이벤트를 3D 쪽으로 통과시킴 (버튼 등은 pointer-events-auto 필요) */
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

/* 퀴즈 콘텐츠 영역 */
.quiz-contents {
  width: 100%;
  height: 100%;
  pointer-events: auto; /* 내부 버튼 클릭 가능하도록 */
}

/* 유틸리티 클래스 (Tailwind가 적용 안 될 경우를 대비해 pointer-events 명시) */
.pointer-events-auto {
  pointer-events: auto;
}
</style>
