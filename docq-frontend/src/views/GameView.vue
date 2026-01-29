<script setup lang="ts">
import { ref, computed, watch, onMounted, watchEffect } from 'vue';
import { useRouter } from 'vue-router'
import ResultModal from '@/components/game/ResultModal.vue';
import GameBoard from '@/components/game/board3D/GameBoard.vue';
import QuizOverlay from '@/components/game/QuizOverlay.vue';
import RouletteOverlay from '@/components/game/RouletteOverlay.vue';
import MiniGameModal from '@/components/minigame/MiniGameModal.vue';
import BuffRouletteOverlay from '@/components/minigame/BuffRouletteOverlay.vue';
import CloudBackground from '@/components/ui/CloudBackground.vue';
import ConfirmModal from '@/components/common/ConfirmModal.vue';
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
const isGameFinished = computed(() => gameStore.isGameFinished)
const gamePhase = computed(() => gameStore.gamePhase)
const isResultVisible = computed(() => isGameFinished.value || gamePhase.value === 'FINISHED')
const canShowResultModal = computed(() =>
    isResultVisible.value && !isBoardMoving.value && !gameStore.movementInProgress
)
const roomId = computed(() => roomStore.roomInfo.roomId)
const isHost = computed(() => roomStore.isHost || roomStore.myUser?.isHost || false)
const players = computed(() => gameStore.gameState.players || [])
const orderedPlayers = computed(() => {
    const list = players.value.slice()
    const order = gameStore.gameState.turnOrder || []
    if (!order.length) return list
    const byId = new Map(list.map(p => [p.userId, p]))
    const ordered = order.map(id => byId.get(id)).filter((p): p is typeof list[number] => !!p)
    const remaining = list.filter(p => !order.includes(p.userId))
    return [...ordered, ...remaining]
})
const cornerPlayers = computed(() => {
    const slots = Array.from({ length: 4 }, () => null as (typeof players.value[number] | null))
    orderedPlayers.value.slice(0, 4).forEach((p, idx) => {
        slots[idx] = p
    })
    return slots
})
const miniGame = computed(() => gameStore.miniGame)
const isMiniGameActive = computed(() => miniGame.value.phase === 'playing' || miniGame.value.phase === 'result')
const showBuffRoulette = computed(() =>
    miniGame.value.phase === 'result' &&
    !!miniGame.value.result?.rewardBuff &&
    miniGame.value.result.rewardBuff !== 'NONE'
)
const buffRouletteCode = computed(() => {
    if (!showBuffRoulette.value) return null
    return miniGame.value.result?.rewardBuff ?? null
})

const isBoardMoving = ref(false)
const showMiniGameStartNotice = ref(false)
const isMiniGameStarting = ref(false)
const systemNotice = ref<string | null>(null)
const isMenuOpen = ref(false)
const confirmAction = ref<'leave' | 'end' | 'restart' | null>(null)
const isMiniGameWinnerNoticeActive = ref(false)
const isGameEndNoticeActive = ref(false)
const hasShownGameStartNotice = ref(false)
const hasShownGameEndNotice = ref(false)
let systemNoticeTimer: number | null = null
let systemNoticeResolver: (() => void) | null = null

const showSystemNotice = (text: string, duration = 2000) => {
    if (systemNoticeTimer !== null) {
        clearTimeout(systemNoticeTimer)
        systemNoticeTimer = null
    }
    if (systemNoticeResolver) {
        systemNoticeResolver()
        systemNoticeResolver = null
    }
    systemNotice.value = text
    return new Promise<void>((resolve) => {
        systemNoticeResolver = resolve
        systemNoticeTimer = window.setTimeout(() => {
            systemNotice.value = null
            systemNoticeTimer = null
            systemNoticeResolver = null
            resolve()
        }, duration)
    })
}

const winnerName = computed(() => {
    const winnerId = miniGame.value.result?.winnerUserId
    if (!winnerId) return ''
    return roomStore.users.find(u => u.id === winnerId)?.nickname || ''
})

const miniGamePlayers = computed(() =>
    roomStore.users.map(user => ({
        id: user.id,
        nickname: user.nickname,
        avatar: user.avatar || '🎮',
        isMe: user.isMe
    }))
)

const miniGameUiType = computed(() => {
    switch (miniGame.value.gameType) {
        case 'LEFT_RIGHT_MASH':
            return 'mash'
        case 'DIRECTION_MATCH':
            return 'sequence'
        case 'EXACT_TIME':
            return 'stopwatch'
        default:
            return null
    }
})

// 현재 턴인지 확인
const currentTurnPlayer = computed(() => gameStore.currentTurnPlayer)
const catchEffect = ref<string | null>(null)
const pendingCatchEffect = ref<string | null>(null)
let pendingCatchKey: string | null = null
let catchEffectTimer: number | null = null
let resultNoticeToken = 0
let miniGameNoticeToken = 0
let gameEndNoticeToken = 0

watch(
    () => gameStore.currentRoundResult,
    async (result) => {
        if (!result) return
        const token = ++resultNoticeToken
        const winnerId = result.winnerUserId ?? null
        const currentTurnId = gameStore.gameState.currentTurnUserId ?? null
        let noticeText = '맞춘 사람이 없습니다.'

        if (winnerId) {
            const winnerName = roomStore.users.find(u => u.id === winnerId)?.nickname || '알 수 없음'
            const roleLabel = winnerId === currentTurnId ? '플레이어' : '고스트'
            noticeText = `${roleLabel} ${winnerName}가 문제를 맞추었습니다.`
        }

        await showSystemNotice(noticeText, 2000)
        if (token !== resultNoticeToken) return
        gameStore.unlockMove()
        wsStore.sendAck('RESULT_DISPLAYED', result.quizId, gameStore.gameState.round ?? undefined)
    }
)

watch(
    () => gameStore.currentMove,
    (move) => {
        if (!move || !myUserId.value) return

        const catchAction = move.moveProcess?.find((item: any) => item.type === 'CATCH')
        if (!catchAction) {
            pendingCatchEffect.value = null
            pendingCatchKey = null
            return
        }

        const moveKey = JSON.stringify({
            userId: move.userId,
            start: move.startPosition,
            end: move.endPosition,
            process: move.moveProcess
        })
        pendingCatchKey = moveKey

        if (catchAction.targetUserId === myUserId.value) {
            pendingCatchEffect.value = '잡혔습니다. 출발지로 이동합니다.'
        } else if (move.userId === myUserId.value) {
            pendingCatchEffect.value = '잡았습니다. 출발지로 이동시킵니다.'
        } else {
            pendingCatchEffect.value = null
        }
    }
)

watch(
    () => gameStore.movementInProgress,
    (moving, prev) => {
        if (moving || !prev) return
        if (!pendingCatchEffect.value || !gameStore.currentMove) return

        const move = gameStore.currentMove
        const moveKey = JSON.stringify({
            userId: move.userId,
            start: move.startPosition,
            end: move.endPosition,
            process: move.moveProcess
        })
        if (pendingCatchKey !== moveKey) return

        if (catchEffectTimer !== null) {
            clearTimeout(catchEffectTimer)
            catchEffectTimer = null
        }

        catchEffect.value = pendingCatchEffect.value
        pendingCatchEffect.value = null
        pendingCatchKey = null

        catchEffectTimer = window.setTimeout(() => {
            catchEffect.value = null
            catchEffectTimer = null
        }, 2000)
    }
)

const submitAnswer = (index: number) => {
    if (!currentQuiz.value) return

    const answerText = currentQuiz.value.options[index]
    
    const roomId = roomStore.roomInfo.roomId
    if (roomId && myUserId.value) {
        wsStore.submitAnswer(roomId, myUserId.value, currentQuiz.value.quizId, answerText || "")
    }
}

const confirmConfig = computed(() => {
    switch (confirmAction.value) {
        case 'leave':
            return {
                title: '게임 나가기',
                message: '정말 게임에서 나가시겠습니까?',
                confirmLabel: '나가기',
                icon: 'logout',
                variant: 'danger' as const
            }
        case 'end':
            return {
                title: '게임 종료',
                message: '정말 게임을 종료하시겠습니까?',
                confirmLabel: '게임 종료',
                icon: 'stop_circle',
                variant: 'danger' as const
            }
        case 'restart':
            return {
                title: '게임 재시작',
                message: '게임을 재시작하시겠습니까?',
                confirmLabel: '재시작',
                icon: 'restart_alt',
                variant: 'primary' as const
            }
        default:
            return {
                title: '',
                message: '',
                confirmLabel: '',
                icon: 'help',
                variant: 'danger' as const
            }
    }
})

const openConfirm = (action: 'leave' | 'end' | 'restart') => {
    confirmAction.value = action
}

const closeConfirm = () => {
    confirmAction.value = null
}

const handleConfirm = () => {
    const action = confirmAction.value
    confirmAction.value = null
    if (!action) return
    if (action === 'leave') leaveGame()
    if (action === 'end') endGame()
    if (action === 'restart') restartGame()
}

const leaveGame = () => {
    if (!userStore.userId || !roomId.value) return
    wsStore.leaveGame(roomId.value, userStore.userId)
    setTimeout(() => {
        wsStore.cleanup()
        roomStore.leaveRoom()
        gameStore.reset()
        router.push('/')
    }, 150)
}

const endGame = () => {
    if (!userStore.userId || !roomId.value) return
    wsStore.endGame(roomId.value, userStore.userId)
}

const restartGame = () => {
    if (!userStore.userId || !roomId.value) return
    wsStore.restartGame(roomId.value, userStore.userId)
}

watchEffect(() => {
    if (roomStore.roomInfo.status === 'WAITING' && roomId.value) {
        gameStore.reset()
        router.push(`/waiting/${roomId.value}`)
    }
})

watchEffect(() => {
    if (miniGame.value.phase !== 'pending') {
        showMiniGameStartNotice.value = false
        isMiniGameStarting.value = false
        return
    }

    if (isBoardMoving.value || isMiniGameStarting.value || catchEffect.value || pendingCatchEffect.value) return
    isMiniGameStarting.value = true
    showMiniGameStartNotice.value = true
    window.setTimeout(() => {
        showMiniGameStartNotice.value = false
        gameStore.startMiniGame()
        isMiniGameStarting.value = false
    }, 2000)
})

const handleMiniGameSubmit = (score: number) => {
    if (!roomId.value || !userStore.userId || !miniGame.value.gameType) return
    // 점수는 높을수록 유리하도록 변환 (시간 기반 -> 역수 점수)
    const safeScore = Math.max(score, 0.001)
    const resultValue = Math.max(1, Math.round((1 / safeScore) * 1000))
    wsStore.submitMiniGameResult(roomId.value, userStore.userId, miniGame.value.gameType, resultValue)
}

const handleMiniGameClose = () => {
    wsStore.sendAck('MINIGAME_COMPLETE', undefined, gameStore.gameState.round ?? undefined)
    gameStore.clearMiniGame()
}

const handleBuffRouletteDone = () => {
    wsStore.sendAck('BUFF_ROULETTE_COMPLETE', undefined, gameStore.gameState.round ?? undefined)
    gameStore.clearMiniGame()
}

watch(
    () => miniGame.value.phase,
    async (phase) => {
        if (phase !== 'result') return
        const token = ++miniGameNoticeToken
        isMiniGameWinnerNoticeActive.value = true
        const name = winnerName.value
        const noticeText = name ? `미니게임의 승자는 ${name}입니다.` : '미니게임 승자가 없습니다.'
        await showSystemNotice(noticeText, 2000)
        if (token !== miniGameNoticeToken) return
        isMiniGameWinnerNoticeActive.value = false

        const rewardBuff = miniGame.value.result?.rewardBuff
        if (!rewardBuff || rewardBuff === 'NONE') {
            wsStore.sendAck('MINIGAME_COMPLETE', undefined, gameStore.gameState.round ?? undefined)
            gameStore.clearMiniGame()
        }
    }
)

watch(
    () => gamePhase.value,
    async (phase) => {
        if (phase === 'WAITING') {
            hasShownGameStartNotice.value = false
            return
        }
        if (phase === 'STARTING' && !hasShownGameStartNotice.value) {
            hasShownGameStartNotice.value = true
            await showSystemNotice('게임이 시작됩니다.', 2000)
        }
    },
    { immediate: true }
)

watch(
    () => [isGameFinished.value, gamePhase.value] as const,
    async ([finished, phase]) => {
        if (!finished && phase !== 'FINISHED') return
        if (hasShownGameEndNotice.value) return
        if (isBoardMoving.value || gameStore.movementInProgress) return
        hasShownGameEndNotice.value = true
        const token = ++gameEndNoticeToken
        isGameEndNoticeActive.value = true
        await showSystemNotice('게임이 종료되었습니다.', 2000)
        if (token !== gameEndNoticeToken) return
        isGameEndNoticeActive.value = false
    }
)

onMounted(() => {
    if (roomId.value) {
        wsStore.subscribeMiniGame(roomId.value)
    }
})
</script>

<template>
  <div class="game-page">
    <CloudBackground class="game-background" />
    <div class="map-focus-blur" aria-hidden="true" />

    <!-- 3D 보드게임 컴포넌트 (배경) -->
    <GameBoard 
      class="game-board-layer"
      @movement-change="(moving) => (isBoardMoving = moving)" 
    />

    <MiniGameModal
      v-if="isMiniGameActive && miniGameUiType && !showBuffRoulette"
      :game-type="miniGameUiType"
      :time-limit-ms="miniGame.timeLimitMs ?? undefined"
      :description="miniGame.description ?? undefined"
      :players="miniGamePlayers"
      :result="miniGame.result ?? undefined"
      @submit="handleMiniGameSubmit"
      @close="handleMiniGameClose"
    />

    <QuizOverlay
      v-if="currentQuiz && gamePhase === 'QUIZ_TIME' && !isBoardMoving && !isMiniGameActive && !showMiniGameStartNotice && !systemNotice"
      @submit="submitAnswer"
    />

    <RouletteOverlay />

    <div v-if="showMiniGameStartNotice" class="minigame-start-notice">
      <div class="minigame-start-card">
        미니게임이 시작됩니다.
      </div>
    </div>

    <BuffRouletteOverlay
      v-if="buffRouletteCode && !isMiniGameWinnerNoticeActive"
      :buff-code="buffRouletteCode"
      :winner-name="winnerName"
      @done="handleBuffRouletteDone"
    />

    <transition name="result-pop">
      <div v-if="systemNotice" class="system-notice">
        <div class="system-notice-card">
          {{ systemNotice }}
        </div>
      </div>
    </transition>

    <!-- UI 오버레이 -->
    <div v-if="!isMiniGameActive && !showMiniGameStartNotice" class="ui-overlay pointer-events-none flex flex-col justify-between p-6">
      <div class="corner-players pointer-events-auto">
        <div
          v-for="(p, idx) in cornerPlayers"
          :key="p?.userId ?? `corner-empty-${idx}`"
          class="corner-card"
          :class="[
            `corner-${idx + 1}`,
            { 'corner-card-active': p?.userId === currentTurnPlayer?.userId }
          ]"
        >
          <template v-if="p">
            <div class="corner-card-name">
              {{ p.nickname }}
              <span v-if="p.userId === myUserId" class="corner-card-me">MY</span>
              <span v-if="p.userId === currentTurnPlayer?.userId" class="corner-card-turn">TURN</span>
            </div>
            <div class="corner-card-meta">
              <span v-if="p.hasBuff" class="corner-card-buff" :title="p.hasBuff">⚡</span>
              <span>맞춘 문제: {{ p.score ?? 0 }}</span>
              <span>POS {{ p.position ?? 0 }}</span>
            </div>
          </template>
          <template v-else>
            <div class="corner-card-empty">Empty Slot</div>
          </template>
        </div>
      </div>

      <transition name="result-pop">
        <div v-if="catchEffect" class="catch-effect">
          <div class="catch-effect-text">
            {{ catchEffect }}
          </div>
        </div>
      </transition>
      
      <!-- 상단 정보: 라운드, 타이머 -->
      <header class="top-center-header flex justify-between items-start pointer-events-auto gap-4">
         <div class="bg-white/90 backdrop-blur rounded-xl p-4 shadow-lg border-2 border-slate-200">
            <h2 class="text-xl font-black text-slate-700">
               ROUND {{ gameStore.gameState.round || 1 }}
            </h2>
         </div>

         <div v-if="currentQuiz && gamePhase === 'QUIZ_TIME'" 
              class="flex flex-col items-center">
            <div class="text-4xl font-black text-white drop-shadow-[0_2px_2px_rgba(0,0,0,0.5)]"
                 :class="{'text-red-400 animate-pulse': timeRemaining <= 5}">
               {{ Math.ceil(timeRemaining) }}
            </div>
            <div class="text-white text-xs font-bold drop-shadow">SEC</div>
         </div>
      </header>

      <!-- 결과 모달 -->
      <ResultModal v-if="canShowResultModal && !isGameEndNoticeActive" class="pointer-events-auto" />
    </div>

    <div class="global-controls pointer-events-auto">
      <button
        class="menu-button"
        @click="isMenuOpen = !isMenuOpen"
        aria-label="menu"
      >
        <span class="material-symbols-outlined">menu</span>
      </button>
      <div v-if="isMenuOpen" class="menu-panel">
        <button
          class="bg-white text-slate-700 font-bold px-4 py-2 rounded-lg border border-slate-200 shadow hover:bg-white"
          @click="isMenuOpen = false; openConfirm('leave')"
        >
          나가기
        </button>
        <button
          v-if="isHost"
          class="bg-rose-500 text-white font-bold px-4 py-2 rounded-lg shadow hover:bg-rose-600"
          @click="isMenuOpen = false; openConfirm('end')"
        >
          게임 종료
        </button>
        <button
          v-if="isHost"
          class="bg-emerald-500 text-white font-bold px-4 py-2 rounded-lg shadow hover:bg-emerald-600"
          @click="isMenuOpen = false; openConfirm('restart')"
        >
          재시작
        </button>
      </div>
    </div>
    <ConfirmModal
      :isOpen="!!confirmAction"
      :title="confirmConfig.title"
      :message="confirmConfig.message"
      :confirmLabel="confirmConfig.confirmLabel"
      :icon="confirmConfig.icon"
      :variant="confirmConfig.variant"
      @close="closeConfirm"
      @confirm="handleConfirm"
    />
  </div>
</template>

<style scoped>
/* 전체 화면을 채우는 컨테이너 */
.game-page {
  width: 100vw;
  height: 100vh;
  /* background: #222;  <- 삭제: InfiniteBackground가 배경임 */
  position: relative;
  overflow: hidden;
}

.game-background {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.game-board-layer {
  position: relative;
  z-index: 2;
}

.map-focus-blur {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  backdrop-filter: blur(14px) saturate(1.05);
  -webkit-backdrop-filter: blur(14px) saturate(1.05);
  background: rgba(0, 0, 0, 0.04);
  mask-image: radial-gradient(ellipse 60% 55% at 50% 55%, transparent 0%, transparent 45%, rgba(0,0,0,1) 65%, rgba(0,0,0,1) 100%);
  -webkit-mask-image: radial-gradient(ellipse 60% 55% at 50% 55%, transparent 0%, transparent 45%, rgba(0,0,0,1) 65%, rgba(0,0,0,1) 100%);
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

.corner-players {
  position: absolute;
  inset: 0;
  z-index: 20;
  pointer-events: none;
}

.corner-card {
  position: absolute;
  width: 220px;
  min-height: 90px;
  background: rgba(255, 255, 255, 0.92);
  border: 2px solid #e2e8f0;
  border-radius: 16px;
  padding: 12px;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.12);
  pointer-events: auto;
}

.corner-card-active {
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.25), 0 10px 24px rgba(0, 0, 0, 0.12);
}

.corner-1 {
  top: 20px;
  left: 20px;
}

.corner-2 {
  top: 20px;
  right: 20px;
}

.corner-3 {
  bottom: 20px;
  left: 20px;
}

.corner-4 {
  bottom: 20px;
  right: 20px;
}

.corner-card-name {
  font-weight: 900;
  font-size: 14px;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.corner-card-turn {
  font-size: 10px;
  font-weight: 900;
  color: white;
  background: #6366f1;
  padding: 2px 6px;
  border-radius: 999px;
}

.corner-card-me {
  font-size: 10px;
  font-weight: 900;
  color: #0f172a;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 999px;
}

.corner-card-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.corner-card-buff {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: rgba(250, 204, 21, 0.2);
  border: 1px solid rgba(250, 204, 21, 0.6);
  font-size: 12px;
  margin-bottom: 4px;
}

.corner-card-empty {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
}

.top-center-header {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
}

.global-controls {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 2600;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.menu-button {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  border: 2px solid #e2e8f0;
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.12);
  cursor: pointer;
}

.menu-button:hover {
  background: white;
}

.menu-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.95);
  border: 2px solid #e2e8f0;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.18);
  min-width: 140px;
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

.result-pop-enter-active,
.result-pop-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}

.result-pop-enter-from,
.result-pop-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

.system-notice {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2300;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(4px);
}

.system-notice-card {
  background: white;
  padding: 18px 30px;
  border-radius: 18px;
  font-size: 1.6rem;
  font-weight: 900;
  color: #111827;
  text-align: center;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.35);
}

.catch-effect {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 40;
}

.catch-effect-text {
  background: rgba(15, 23, 42, 0.85);
  color: #f8fafc;
  font-weight: 800;
  font-size: 1.4rem;
  padding: 14px 24px;
  border-radius: 16px;
  border: 2px solid rgba(148, 163, 184, 0.6);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.4);
  text-align: center;
}

.minigame-start-notice {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.7);
  z-index: 2400;
  backdrop-filter: blur(4px);
}

.minigame-start-card {
  background: white;
  padding: 20px 32px;
  border-radius: 16px;
  font-size: 1.8rem;
  font-weight: 900;
  color: #1f2937;
  box-shadow: 0 12px 30px rgba(0,0,0,0.35);
  animation: popIn 0.25s ease-out;
}

@keyframes popIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
</style>
