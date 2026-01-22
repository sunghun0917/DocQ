import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRoomStore } from './roomStore'
import { mockQuizzes } from '@/mocks/quizzes'
import type { Quiz } from '@/types/docq-api'

// 게임 진행 상태 및 퀴즈 관리
export const useGameStore = defineStore('game', () => {
    // State
    const quizzes = ref(mockQuizzes)
    const currentQuizIndex = ref(0)
    const currentQuiz = ref<Quiz | null>(null) // Live Quiz Object
    const gameStatus = ref<'waiting' | 'playing' | 'finished'>('waiting')

    // 게임 상태
    const isGameEnded = ref(false)
    const timeRemaining = ref(30) // Default 30 seconds
    const timerInterval = ref<number | null>(null)

    // 점수 및 결과
    const scores = ref<Record<string, number>>({})
    const lastAnswerResult = ref<boolean | null>(null)

    // [ADDED] Roulette State (for Overlay support)
    const rouletteTargetId = ref<string | null>(null)
    const rouletteResult = ref<number | null>(null)

    // === GameBoard.vue support ===
    const gameState = ref<{
        players: any[],
        currentTurnUserId: string | null,
        round: number | null,
    }>({
        players: [],
        currentTurnUserId: null,
        round: 1
    })

    const currentMove = ref<any>(null)
    const currentRoundResult = ref<any>(null)

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
    const setGameState = (newState: any) => {
        gameState.value = {
            ...gameState.value,
            ...newState,
            players: newState.players || []
        }

        if (newState.players) {
            newState.players.forEach((p: any) => {
                scores.value[p.userId] = p.score || 0
            })
        }

        if (newState.state) {
            if (newState.state === 'PLAYING') gameStatus.value = 'playing'
            else if (newState.state === 'WAITING') gameStatus.value = 'waiting'
        }
    }

    const handleMove = (moveInfo: any) => {
        currentMove.value = moveInfo
    }

    const onQuizReceived = (quizData: any) => {
        currentQuiz.value = {
            quizId: quizData.quizId,
            question: quizData.question,
            options: quizData.options,
            timeLimit: quizData.timeLimit ? Math.floor(quizData.timeLimit / 1000) : 30,
            answer: -1
        }

        currentQuizIndex.value = 0
        timeRemaining.value = currentQuiz.value?.timeLimit || 30
        gameStatus.value = 'playing'
        lastAnswerResult.value = null
        currentRoundResult.value = null

        startTimer()
    }

    const onRoundResultReceived = (resultData: any) => {
        stopTimer()
        currentRoundResult.value = resultData

        // [ADDED] Set roulette target if there's a winner
        if (resultData.winnerUserId) {
            setTimeout(() => {
                rouletteTargetId.value = resultData.winnerUserId
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
            if (timeRemaining.value > 0) {
                timeRemaining.value--
            } else {
                stopTimer()
            }
        }, 1000)
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
        lastAnswerResult.value = null
        gameState.value = { players: [], currentTurnUserId: null, round: 1 }
        currentMove.value = null
        rouletteTargetId.value = null
        rouletteResult.value = null
    }

    return {
        quizzes,
        currentQuiz,
        currentQuizIndex,
        isGameEnded,
        scores,
        lastAnswerResult,
        gameState,
        currentMove,
        currentRoundResult,
        timeRemaining,
        gameStatus,

        // [ADDED] Roulette
        rouletteTargetId,
        rouletteResult,

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

        // [ADDED] Roulette actions
        setRouletteResult,
        handleRouletteComplete
    }
})
