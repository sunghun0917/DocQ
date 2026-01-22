<script setup lang="ts">
import { ref, computed, watch, onMounted, watchEffect } from 'vue';
import { useRouter } from 'vue-router'
import ResultModal from '@/components/game/ResultModal.vue';
import GameBoard from '@/components/game/board3D/GameBoard.vue';
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
const gamePhase = computed(() => gameStore.gamePhase)
const roomId = computed(() => roomStore.roomInfo.roomId)
const isHost = computed(() => roomStore.isHost)
const players = computed(() => gameStore.gameState.players || [])

// 현재 턴인지 확인
const currentTurnPlayer = computed(() => gameStore.currentTurnPlayer)
const isMyTurn = computed(() => {
    if (!currentTurnPlayer.value || !myUserId.value) return false
    return currentTurnPlayer.value.userId === myUserId.value
})

const selectedOption = ref<number | null>(null)
const isSubmitted = ref(false)
const resultEffect = ref<{ text: 'WIN' | 'LOSE' | null } | null>(null)
let resultEffectTimer: number | null = null

// 퀴즈가 바뀌면 상태 초기화
watch(currentQuiz, () => {
    selectedOption.value = null
    isSubmitted.value = false
})

watch(
    () => gameStore.currentRoundResult,
    (result) => {
        if (!result || !userStore.userId) return
        if (resultEffectTimer !== null) {
            clearTimeout(resultEffectTimer)
            resultEffectTimer = null
        }

        const isWinner = result.winnerUserId === userStore.userId
        resultEffect.value = { text: isWinner ? 'WIN' : 'LOSE' }

        resultEffectTimer = window.setTimeout(() => {
            resultEffect.value = null
            resultEffectTimer = null
        }, 2000)
    }
)

const submitAnswer = (index: number) => {
    if (isSubmitted.value) return
    if (!currentQuiz.value) return
    
    selectedOption.value = index
    isSubmitted.value = true
    
    // WebSocket으로 정답 전송
    // answerText가 필요하다면 options[index]를 보냄, 여기선 index + 1 or string?
    // 백엔드 GameController.submitQuiz(String answer) 
    // 보통 1, 2, 3, 4 번호 혹은 텍스트.
    // 일단 텍스트로 보낸다고 가정 (Backend logic check needed, but let's try option text)
    const answerText = currentQuiz.value.options[index]
    
    // roomId is required. userStore doesn't have it? roomStore.roomInfo.roomId
    const roomId = roomStore.roomInfo.roomId
    if (roomId && myUserId.value) {
        wsStore.submitAnswer(roomId, myUserId.value, currentQuiz.value.quizId, answerText || "")
    }
}

const leaveGame = () => {
    if (!userStore.userId || !roomId.value) return
    if (confirm("정말 게임에서 나가시겠습니까?")) {
        wsStore.leaveGame(roomId.value, userStore.userId)
        setTimeout(() => {
            wsStore.cleanup()
            roomStore.leaveRoom()
            gameStore.reset()
            router.push('/')
        }, 150)
    }
}

const endGame = () => {
    if (!userStore.userId || !roomId.value) return
    if (confirm("게임을 종료하시겠습니까?")) {
        wsStore.endGame(roomId.value, userStore.userId)
    }
}

const restartGame = () => {
    if (!userStore.userId || !roomId.value) return
    if (confirm("게임을 재시작하시겠습니까?")) {
        wsStore.restartGame(roomId.value, userStore.userId)
    }
}

watchEffect(() => {
    if (roomStore.roomInfo.status === 'WAITING' && roomId.value) {
        router.push(`/waiting/${roomId.value}`)
    }
})
</script>

<template>
  <div class="game-page">
    <!-- 3D 보드게임 컴포넌트 (배경) -->
    <GameBoard />

    <!-- UI 오버레이 -->
    <div class="ui-overlay pointer-events-none flex flex-col justify-between p-6">
      <div class="player-panel pointer-events-auto">
        <div class="player-panel-title">Players</div>
        <div class="player-panel-turn">
          현재 턴: <span>{{ currentTurnPlayer?.nickname || '결정 중...' }}</span>
        </div>
        <div class="player-panel-list">
          <div
            v-for="p in players"
            :key="p.userId"
            class="player-row"
            :class="{ 'player-row-active': p.userId === currentTurnPlayer?.userId }"
          >
            <div class="player-name">{{ p.nickname }}</div>
            <div class="player-meta">
              <span class="player-score">SCORE {{ p.score ?? 0 }}</span>
              <span class="player-pos">POS {{ p.position ?? 0 }}</span>
              <span v-if="p.hasBuff" class="player-buff" :title="p.hasBuff">
                ⚡
              </span>
            </div>
          </div>
        </div>
      </div>

      <transition name="result-pop">
        <div v-if="resultEffect?.text" class="result-effect">
          <div
            class="result-effect-text"
            :class="resultEffect.text === 'WIN' ? 'result-win' : 'result-lose'"
          >
            {{ resultEffect.text }}
          </div>
        </div>
      </transition>
      
      <!-- 상단 정보: 라운드, 타이머 -->
      <header class="top-right-header flex justify-between items-start pointer-events-auto gap-4">
         <div class="bg-white/90 backdrop-blur rounded-xl p-4 shadow-lg border-2 border-slate-200">
            <h2 class="text-xl font-black text-slate-700">
               ROUND {{ gameStore.gameState.round || 1 }}
            </h2>
         </div>

         <div v-if="currentQuiz && gamePhase === 'QUIZ_TIME'" 
              class="flex flex-col items-center">
            <div class="text-4xl font-black text-white drop-shadow-[0_2px_2px_rgba(0,0,0,0.5)]"
                 :class="{'text-red-400 animate-pulse': timeRemaining <= 5}">
               {{ timeRemaining }}
            </div>
            <div class="text-white text-xs font-bold drop-shadow">SEC</div>
         </div>

         <div class="flex flex-col gap-2 pointer-events-auto">
            <button
              class="bg-white/90 text-slate-700 font-bold px-4 py-2 rounded-lg border border-slate-200 shadow hover:bg-white"
              @click="leaveGame"
            >
              나가기
            </button>
            <button
              v-if="isHost"
              class="bg-rose-500 text-white font-bold px-4 py-2 rounded-lg shadow hover:bg-rose-600"
              @click="endGame"
            >
              게임 종료
            </button>
            <button
              v-if="isHost"
              class="bg-emerald-500 text-white font-bold px-4 py-2 rounded-lg shadow hover:bg-emerald-600"
              @click="restartGame"
            >
              재시작
            </button>
         </div>
      </header>

      <!-- 퀴즈 영역 (중앙 하단) -->
      <div v-if="currentQuiz && gamePhase === 'QUIZ_TIME'" class="quiz-container">
         
         <!-- 퀴즈 풀기 (모든 플레이어 참여 가능 - 스틸 룰) -->
         <div class="bg-white rounded-2xl shadow-2xl p-6 w-[600px] pointer-events-auto animate-bounce-up border-4"
              :class="isMyTurn ? 'border-indigo-500 ring-4 ring-indigo-200' : 'border-slate-200'"
         >
            <div class="mb-4 flex justify-between items-start">
               <div>
                   <span v-if="isMyTurn" class="bg-indigo-600 text-white text-xs font-black px-2 py-1 rounded animate-pulse">
                        👑 MY TURN
                   </span>
                   <span v-else class="bg-rose-500 text-white text-xs font-black px-2 py-1 rounded">
                        ⚡ STEAL CHANCE
                   </span>
                   <h3 class="text-xl font-bold mt-2 text-slate-800 leading-snug">
                      {{ currentQuiz.question }}
                   </h3>
               </div>
               <div v-if="!isMyTurn" class="text-xs font-bold text-slate-400 text-right">
                    {{ currentTurnPlayer?.nickname }}님의 턴<br>
                    틀리면 가장 빠른 정답자가 승리!
               </div>
            </div>
            
            <div class="grid grid-cols-1 gap-2">
               <button 
                  v-for="(option, idx) in currentQuiz.options" 
                  :key="idx"
                  @click="submitAnswer(idx)"
                  :disabled="isSubmitted"
                  class="w-full text-left p-4 rounded-xl border-2 transition-all font-bold text-slate-600"
                  :class="[
                     selectedOption === idx 
                        ? 'bg-indigo-50 border-indigo-500 text-indigo-700 ring-2 ring-indigo-200' 
                        : 'bg-slate-50 border-slate-200 hover:bg-white hover:border-indigo-300'
                  ]"
               >
                  <span class="inline-block w-6 h-6 rounded-full bg-white border-2 border-current text-center text-sm mr-2 leading-5">
                    {{ idx + 1 }}
                  </span>
                  {{ option }}
               </button>
            </div>
            
            <div v-if="isSubmitted" class="text-center mt-4 text-indigo-600 font-bold animate-pulse">
               답안 제출 완료! {{ isMyTurn ? '정답 체크 중...' : '턴 주인의 결과를 기다리는 중...' }}
            </div>
         </div>

      </div>

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
}

/* 유틸리티 클래스 (Tailwind가 적용 안 될 경우를 대비해 pointer-events 명시) */
.pointer-events-auto {
  pointer-events: auto;
}

.player-panel {
  position: absolute;
  top: 20px;
  left: 20px;
  width: 220px;
  background: rgba(255, 255, 255, 0.9);
  border: 2px solid #e2e8f0;
  border-radius: 16px;
  padding: 12px;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.12);
  z-index: 20;
}

.top-right-header {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 20;
}

.player-panel-title {
  font-weight: 800;
  font-size: 14px;
  color: #334155;
  margin-bottom: 8px;
}

.player-panel-turn {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 8px;
}

.player-panel-turn span {
  color: #4f46e5;
}

.player-panel-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.player-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
  font-size: 12px;
}

.player-row-active {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.12);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}

.player-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 130px;
}

.player-pos {
  font-weight: 900;
  color: #0f172a;
}

.quiz-container {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  width: min(680px, 100%);
  display: flex;
  justify-content: center;
  padding: 0 16px;
  z-index: 20;
}

.player-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.player-score {
  font-weight: 800;
  color: #1e293b;
}

.player-buff {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: rgba(250, 204, 21, 0.2);
  border: 1px solid rgba(250, 204, 21, 0.6);
  font-size: 12px;
}

.result-effect {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.result-effect-text {
  font-size: 6rem;
  font-weight: 900;
  letter-spacing: 0.1em;
  text-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  padding: 16px 32px;
  border-radius: 24px;
  border: 4px solid transparent;
}

.result-win {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.12);
  border-color: rgba(34, 197, 94, 0.5);
  box-shadow: 0 0 30px rgba(34, 197, 94, 0.5);
}

.result-lose {
  color: #f43f5e;
  background: rgba(244, 63, 94, 0.12);
  border-color: rgba(244, 63, 94, 0.5);
  box-shadow: 0 0 30px rgba(244, 63, 94, 0.5);
}

.result-pop-enter-active,
.result-pop-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}

.result-pop-enter-from,
.result-pop-leave-to {
  opacity: 0;
  transform: scale(0.9);
}
</style>
