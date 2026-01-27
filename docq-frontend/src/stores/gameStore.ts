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
    const timeRemaining = ref(30) // Default 30 seconds
    const quizEndAt = ref<number | null>(null)
    const timerInterval = ref<number | null>(null)

    // 점수 및 결과
    const scores = ref<Record<string, number>>({})
    const lastAnswerResult = ref<boolean | null>(null)

    // [ADDED] Roulette State (for Overlay support)
    const rouletteTargetId = ref<string | null>(null)
    const rouletteResult = ref<number | null>(null)
    const moveLocked = ref(false)

    // === GameBoard.vue support ===
    const gameState = ref<GameStateResponse>({
        state: 'WAITING',
        players: [],
        currentTurnUserId: null,
        round: 1,
        turnOrder: [],
        cardDrawResult: {},
        mapEvents: null
    })

    const currentMove = ref<MoveInfo | null>(null)
    const currentRoundResult = ref<RoundResult | null>(null)
    const movementInProgress = ref(false)
    const pendingQuiz = ref<QuizMessage | null>(null)
    const miniGame = ref<{
        phase: 'idle' | 'pending' | 'playing' | 'result'
        gameType: string | null
        timeLimitMs: number | null
        description: string | null
        result: null | {
            winnerUserId?: string | null
            rewardBuff?: string | null
            rankings?: Array<{
                userId: string
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
        const roomStore = useRoomStore()
        const userScores = Object.entries(scores.value).map(([userId, score]) => ({
            userId,
            score: score as number,
            nickname: roomStore.users.find((u: any) => u.id === userId)?.nickname || 'Unknown'
        }));
        return userScores.sort((a, b) => b.score - a.score);
    })

    const isGameFinished = computed(() => gameStatus.value === 'finished')

    const currentTurnPlayer = computed(() => {
        if (!gameState.value.currentTurnUserId) return null
        return gameState.value.players.find(p => p.userId === gameState.value.currentTurnUserId)
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
                scores.value[p.userId] = p.score ?? 0
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
        }
    }

    const handleMove = (moveInfo: MoveInfo) => {
        currentMove.value = moveInfo
        setMovementInProgress(true)
    }

    const applyQuiz = (quizData: QuizMessage) => {
        const timeLimitMs = quizData.timeLimit ? Number(quizData.timeLimit) : 30000
        const startAtMs = quizData.quizStartTimeMs
        const startAtIso = quizData.quizStartTime ? Date.parse(quizData.quizStartTime) : null
        const effectiveStart = (startAtMs ?? startAtIso) ?? Date.now()
        const endAt = effectiveStart + timeLimitMs
        quizEndAt.value = endAt
        const remainingMs = Math.max(0, endAt - Date.now())

        currentQuiz.value = {
            quizId: quizData.quizId,
            question: quizData.question,
            options: quizData.options,
            timeLimit: Math.max(1, Math.ceil(remainingMs / 1000)),
            answer: -1
        }

        currentQuizIndex.value = 0
        timeRemaining.value = currentQuiz.value?.timeLimit || 30
        gameStatus.value = 'playing'
        lastAnswerResult.value = null
        currentRoundResult.value = null
        moveLocked.value = true

        startTimer()
    }

    const onQuizReceived = (quizData: QuizMessage) => {
        if (movementInProgress.value) {
            pendingQuiz.value = quizData
            return
        }
        applyQuiz(quizData)
    }

    const onRoundResultReceived = (resultData: RoundResult) => {
        stopTimer()
        currentRoundResult.value = resultData

        // [ADDED] Set roulette target if there's a winner
        const winnerUserId = resultData.winnerUserId ?? null
        if (winnerUserId) {
            setTimeout(() => {
                rouletteTargetId.value = winnerUserId
            }, 2000) // Show feedback first
        }
    }

    // [ADDED] Roulette helpers
    const setRouletteResult = (result: number) => {
        rouletteResult.value = result
    }

    const handleRouletteComplete = () => {
        rouletteTargetId.value = null
        rouletteResult.value = null
    }

    const submitAnswer = (userId: string, answerText: string) => {
        // WebSocket logic handled in View
    }

    const updateScore = (userId: string, score: number) => {
        scores.value[userId] = score
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
            timeRemaining.value = Math.max(0, Math.ceil(remainingMs / 1000))
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
        timeRemaining.value = 30
        quizEndAt.value = null
        lastAnswerResult.value = null
        gameState.value = {
            state: 'WAITING',
            players: [],
            currentTurnUserId: null,
            round: 1,
            turnOrder: [],
            cardDrawResult: {},
            mapEvents: null
        }
        currentMove.value = null
        rouletteTargetId.value = null
        rouletteResult.value = null
        moveLocked.value = false
        movementInProgress.value = false
        pendingQuiz.value = null
    }

    const reset = () => {
        resetTimer()
        isGameEnded.value = false
        currentQuiz.value = null
        lastAnswerResult.value = null
        currentRoundResult.value = null
        quizEndAt.value = null
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
        winnerUserId?: string | null
        rewardBuff?: string | null
        rankings?: Array<{ userId: string; nickname: string; value: number; rank: number }>
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
        queueMiniGame,
        startMiniGame,
        setMiniGameResult,
        clearMiniGame
    }
})
