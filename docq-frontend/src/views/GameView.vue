<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
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
</script>

<template>
  <div class="game-page">
    <!-- 3D 보드게임 컴포넌트 (배경) -->
    <GameBoard />

    <!-- UI 오버레이 -->
    <div class="ui-overlay pointer-events-none flex flex-col justify-between p-6">
      
      <!-- 상단 정보: 라운드, 타이머 -->
      <header class="flex justify-between items-start pointer-events-auto">
         <div class="bg-white/90 backdrop-blur rounded-xl p-4 shadow-lg border-2 border-slate-200">
            <h2 class="text-xl font-black text-slate-700">
               ROUND {{ gameStore.gameState.round || 1 }}
            </h2>
            <div class="text-sm text-slate-500 font-bold">
               현재 턴: <span class="text-indigo-600">{{ currentTurnPlayer?.nickname || '결정 중...' }}</span>
            </div>
         </div>

         <div v-if="currentQuiz && gameStatus === 'playing'" 
              class="flex flex-col items-center">
            <div class="text-4xl font-black text-white drop-shadow-[0_2px_2px_rgba(0,0,0,0.5)]"
                 :class="{'text-red-400 animate-pulse': timeRemaining <= 5}">
               {{ timeRemaining }}
            </div>
            <div class="text-white text-xs font-bold drop-shadow">SEC</div>
         </div>
      </header>

      <!-- 퀴즈 영역 (중앙 하단) -->
      <div v-if="currentQuiz && gameStatus === 'playing'" class="flex justify-center mb-10">
         
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
</style>
