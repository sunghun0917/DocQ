import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRoomStore } from './roomStore'
// import { useWebSocketStore } from './websocketStore' // Cyclic Dependency Risk

import type { Quiz, GameStateResponse, PlayerInfo, MoveInfo, RoundResult, QuizMessage, RoomStatus } from '@/types/docq-api'

// 게임 진행 상태 및 퀴즈 관리
export const useGameStore = defineStore('game', () => {
    // state

    // 퀴즈 관련
    const quizzes = ref<Quiz[]>([])
    const currentQuizIndex = ref(0)
    const currentQuiz = ref<Quiz | null>(null) // Live Quiz Object
    const gameStatus = ref<'waiting' | 'playing' | 'finished'>('waiting')

    // 게임 상태
    const isGameEnded = ref(false)
    const timeRemaining = ref(7) // Default 7 seconds
    const quizEndAt = ref<number | null>(null)
    const timerInterval = ref<number | null>(null)

    // 점수 및 결과
    const scores = ref<Record<string, number>>({})
    const lastAnswerResult = ref<boolean | null>(null)

    // [ADDED] Roulette State (for Overlay support)
    const rouletteTargetId = ref<string | null>(null)
    const rouletteResult = ref<number | null>(null)
    const roulettePending = ref(false)
    const moveLocked = ref(false)
    const ghostPhaseActive = ref(false)
    /** 턴 플레이어 전용: 'correct' = 맞췄습니다, 'wrong' = 틀렸습니다 */
    const turnPlayerFeedback = ref<'correct' | 'wrong' | null>(null)

    // === GameBoard.vue support ===
    const gameState = ref<GameStateResponse>({
        state: 'WAITING',
        players: [],
        currentTurnPlayerId: null,
        round: 1,
        turnOrder: [],
        cardDrawResult: {},
        mapEvents: null
    })

    const currentMove = ref<MoveInfo | null>(null)
    const currentRoundResult = ref<RoundResult | null>(null)
    /** MOVE_COMPLETE ACK에 사용할 퀴즈 ID (라운드 결과 수신 시 설정, 이동 완료 후 사용) */
    const lastMoveQuizId = ref<number | null>(null)
    const movementInProgress = ref(false)
    const pendingQuiz = ref<QuizMessage | null>(null)
    const miniGame = ref<{
        phase: 'idle' | 'pending' | 'playing' | 'result'
        gameType: string | null
        timeLimitMs: number | null
        description: string | null
        result: null | {
            winnerPlayerId?: string | null
            rewardBuff?: string | null
            rankings?: Array<{
                playerId: string
                nickname: string
                value: number
                rank: number
            }>
        }
    }>({
        phase: 'idle',
        gameType: null,
        timeLimitMs: null,
        description: null,
        result: null
    })

    // Getters
    const totalQuizCount = computed(() => quizzes.value.length)

    const rankings = computed(() => {
        return [...gameState.value.players].sort((a, b) => {
            // 1순위: 위치(position)가 높은 사람 순서
            const posA = a.position ?? 0;
            const posB = b.position ?? 0;
            if (posA !== posB) {
                return posB - posA;
            }
            // 2순위: 위치가 같다면 점수(score)가 높은 사람 순서
            const scoreA = a.score ?? 0;
            const scoreB = b.score ?? 0;
            return scoreB - scoreA;
        });
    })

    const isGameFinished = computed(() => gameStatus.value === 'finished')

    const currentTurnPlayer = computed(() => {
        if (!gameState.value.currentTurnPlayerId) return null
        return gameState.value.players.find(p => p.playerId === gameState.value.currentTurnPlayerId)
    })

    // Actions
    const setGameState = (newState: GameStateResponse) => {
        gameState.value = {
            ...gameState.value,
            ...newState,
            players: newState.players || []
        }

        if (newState.players) {
            newState.players.forEach((p: PlayerInfo) => {
                scores.value[p.playerId] = p.score ?? 0
            })
        }

        if (newState.state) {
            if (newState.state === 'WAITING') {
                gameStatus.value = 'waiting'
            } else {
                gameStatus.value = 'playing'
            }

            // 퀴즈 진행 상태가 아니면 타이머를 정지
            if (newState.state !== 'QUIZ_TIME') {
                stopTimer()
            }

            // 미니게임 진입 시 퀴즈 상태를 정리
            if (newState.state === 'MINIGAME') {
                currentQuiz.value = null
                currentRoundResult.value = null
            }
            // 다음 턴 시작(STARTING) 시 이전 퀴즈/결과 비우기 → 새 퀴즈 수신 전 깜빡임 방지
            if (newState.state === 'STARTING') {
                currentQuiz.value = null
                currentRoundResult.value = null
            }
            // 이동/룰렛 중에는 퀴즈 오버레이 데이터 비우기 → 이동 중 퀴즈 노출 방지
            if (newState.state === 'MOVE' || newState.state === 'ROULETTE') {
                currentQuiz.value = null
                currentRoundResult.value = null
            }
        }
    }

    const handleMove = (moveInfo: MoveInfo) => {
        currentQuiz.value = null
        currentRoundResult.value = null
        currentMove.value = moveInfo
        setMovementInProgress(true)
    }

    const applyQuiz = (quizData: QuizMessage) => {
        const timeLimitMs = quizData.timeLimit ? Number(quizData.timeLimit) : 7000
        const startAtMs = quizData.quizStartTimeMs
        const startAtIso = quizData.quizStartTime ? Date.parse(quizData.quizStartTime) : null
        const effectiveStart = (startAtMs ?? startAtIso) ?? Date.now()
        const endAt = effectiveStart + timeLimitMs
        quizEndAt.value = endAt
        const remainingMs = Math.max(0, endAt - Date.now())
        const options = Array.isArray(quizData.options) ? quizData.options : []

        currentQuiz.value = {
            quizId: quizData.quizId,
            question: quizData.question,
            options,
            timeLimit: Math.max(1, Math.ceil(remainingMs / 1000)),
            answer: -1,
            quizStyle: quizData.quizStyle ?? null
        }

        currentQuizIndex.value = 0
        timeRemaining.value = currentQuiz.value?.timeLimit || 7
        gameStatus.value = 'playing'
        lastAnswerResult.value = null
        currentRoundResult.value = null
        moveLocked.value = true
        ghostPhaseActive.value = false
        turnPlayerFeedback.value = null

        startTimer()
    }

    const onQuizReceived = (quizData: QuizMessage) => {
        const incomingOptions = Array.isArray(quizData.options) ? quizData.options : []
        if (movementInProgress.value) {
            if (pendingQuiz.value && pendingQuiz.value.quizId === quizData.quizId) {
                if (incomingOptions.length > 0) {
                    pendingQuiz.value = {
                        ...pendingQuiz.value,
                        options: incomingOptions
                    }
                }
                return
            }
            pendingQuiz.value = quizData
            return
        }
        if (currentQuiz.value && currentQuiz.value.quizId === quizData.quizId) {
            if (incomingOptions.length > 0 && currentQuiz.value.options.length === 0) {
                currentQuiz.value.options = incomingOptions
            }
            return
        }
        applyQuiz(quizData)
    }

    const revealQuizOptions = (quizId: number, options: string[], extraTimeMs?: number) => {
        if (!Array.isArray(options) || options.length === 0) return
        ghostPhaseActive.value = true
        if (extraTimeMs && extraTimeMs > 0) {
            quizEndAt.value = Date.now() + extraTimeMs
            const remainingMs = Math.max(0, extraTimeMs)
            timeRemaining.value = Math.max(0, remainingMs / 1000)
            startTimer()
        }
        if (currentQuiz.value && currentQuiz.value.quizId === quizId) {
            currentQuiz.value.options = options
        }
        if (pendingQuiz.value && pendingQuiz.value.quizId === quizId) {
            pendingQuiz.value = {
                ...pendingQuiz.value,
                options
            }
        }
    }

    const setTurnPlayerFeedback = (correct: boolean) => {
        turnPlayerFeedback.value = correct ? 'correct' : 'wrong'
    }

    const clearTurnPlayerFeedback = () => {
        turnPlayerFeedback.value = null
    }

    const onRoundResultReceived = (resultData: RoundResult) => {
        stopTimer()
        currentRoundResult.value = resultData
        ghostPhaseActive.value = false
        if (resultData.quizId != null) {
            lastMoveQuizId.value = resultData.quizId
        }

        const winnerPlayerId = resultData.winnerPlayerId ?? null
        if (winnerPlayerId && resultData.rouletteValue != null) {
            roulettePending.value = true
        }
        // rouletteTargetId는 GameView에서 결과 2초 표시 후 설정
    }

    // [ADDED] Roulette helpers
    const setRouletteResult = (result: number) => {
        rouletteResult.value = result
    }

    const handleRouletteComplete = () => {
        currentQuiz.value = null
        currentRoundResult.value = null
        rouletteTargetId.value = null
        rouletteResult.value = null
        roulettePending.value = false
    }

    const submitAnswer = (playerId: string, answerText: string) => {
        // WebSocket logic handled in View
    }

    const updateScore = (playerId: string, score: number) => {
        scores.value[playerId] = score
    }

    const endGame = () => {
        isGameEnded.value = true
        gameStatus.value = 'finished'
        stopTimer()
    }

    const startTimer = () => {
        stopTimer()
        timerInterval.value = window.setInterval(() => {
            if (!quizEndAt.value) return
            const remainingMs = Math.max(0, quizEndAt.value - Date.now())
            timeRemaining.value = Math.max(0, remainingMs / 1000)
            if (remainingMs <= 0) {
                stopTimer()
            }
        }, 250)
    }

    const stopTimer = () => {
        if (timerInterval.value !== null) {
            clearInterval(timerInterval.value)
            timerInterval.value = null
        }
    }

    const resetTimer = () => {
        stopTimer()
        currentQuizIndex.value = 0
        scores.value = {}
        gameStatus.value = 'waiting'
        timeRemaining.value = 7
        quizEndAt.value = null
        lastAnswerResult.value = null
        gameState.value = {
            state: 'WAITING',
            players: [],
            currentTurnPlayerId: null,
            round: 1,
            turnOrder: [],
            cardDrawResult: {},
            mapEvents: null
        }
        currentMove.value = null
        lastMoveQuizId.value = null
        rouletteTargetId.value = null
        rouletteResult.value = null
        moveLocked.value = false
        movementInProgress.value = false
        pendingQuiz.value = null
        ghostPhaseActive.value = false
        turnPlayerFeedback.value = null
    }

    const reset = () => {
        resetTimer()
        isGameEnded.value = false
        currentQuiz.value = null
        lastAnswerResult.value = null
        currentRoundResult.value = null
        quizEndAt.value = null
        ghostPhaseActive.value = false
        turnPlayerFeedback.value = null
        miniGame.value = {
            phase: 'idle',
            gameType: null,
            timeLimitMs: null,
            description: null,
            result: null
        }
    }

    const unlockMove = () => {
        moveLocked.value = false
    }

    const setMovementInProgress = (isMoving: boolean) => {
        movementInProgress.value = isMoving
        if (!isMoving && pendingQuiz.value) {
            const nextQuiz = pendingQuiz.value
            pendingQuiz.value = null
            applyQuiz(nextQuiz)
        }
    }

    const queueMiniGame = (payload: { gameType: string; timeLimit?: number; description?: string }) => {
        miniGame.value = {
            phase: 'pending',
            gameType: payload.gameType,
            timeLimitMs: payload.timeLimit ?? null,
            description: payload.description ?? null,
            result: null
        }
    }

    const startMiniGame = () => {
        if (miniGame.value.phase !== 'pending') return
        miniGame.value = {
            ...miniGame.value,
            phase: 'playing'
        }
    }

    const setMiniGameResult = (result: {
        winnerPlayerId?: string | null
        rewardBuff?: string | null
        rankings?: Array<{ playerId: string; nickname: string; value: number; rank: number }>
    }) => {
        miniGame.value = {
            ...miniGame.value,
            phase: 'result',
            result: result
        }
    }

    const clearMiniGame = () => {
        miniGame.value = {
            phase: 'idle',
            gameType: null,
            timeLimitMs: null,
            description: null,
            result: null
        }
    }

    const clearQuiz = () => {
        stopTimer()
        currentQuiz.value = null
        currentRoundResult.value = null
        lastAnswerResult.value = null
        quizEndAt.value = null
        ghostPhaseActive.value = false
        turnPlayerFeedback.value = null
    }

    const gamePhase = computed<RoomStatus | null>(() => gameState.value.state || null)

    return {
        quizzes,
        currentQuiz,
        currentQuizIndex,
        gameStatus,
        isGameEnded,
        timeRemaining,
        scores,
        lastAnswerResult,
        gameState,
        currentMove,
        currentRoundResult,
        lastMoveQuizId,
        gamePhase,
        miniGame,
        moveLocked,
        movementInProgress,

        // Getters
        totalQuizCount,
        rankings,
        isGameFinished,
        currentTurnPlayer,

        // Actions
        setGameState,
        handleMove,
        onQuizReceived,
        revealQuizOptions,
        onRoundResultReceived,
        submitAnswer,
        updateScore,
        endGame,
        startTimer,
        stopTimer,
        resetTimer,
        reset,
        unlockMove,
        setMovementInProgress,

        // [ADDED] Roulette actions
        rouletteTargetId,
        setRouletteResult,
        handleRouletteComplete,
        roulettePending,
        ghostPhaseActive,
        turnPlayerFeedback,
        setTurnPlayerFeedback,
        clearTurnPlayerFeedback,
        queueMiniGame,
        startMiniGame,
        setMiniGameResult,
        clearMiniGame,
        clearQuiz
    }
})
