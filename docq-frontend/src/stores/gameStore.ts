import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRoomStore } from './roomtStore' // roomtStore? check spelling if needed
import { mockQuizzes } from '@/mocks/quizzes'

interface QuizSubmission {
    userId: string
    answer: number
    timestamp: number
}

// 게임 진행 상태 및 퀴즈 관리
export const useGameStore = defineStore('game', () => {
    // State
    const quizzes = ref(mockQuizzes)
    const currentQuizIndex = ref(0)

    // 게임 상태
    const isGameEnded = ref(false)
    const timeRemaining = ref(5) // Default 5 seconds for turn
    const timerInterval = ref<number | null>(null)

    // 점수 및 결과
    const scores = ref<Record<string, number>>({})
    const lastAnswerResult = ref<boolean | null>(null) // 결과 표시용

    // [New] Turn & Interaction Logic
    const currentTurnUserId = ref<string | null>(null)
    const quizSubmissions = ref<Record<string, QuizSubmission>>({})

    // Getters
    const currentQuiz = computed(() => quizzes.value[currentQuizIndex.value])
    const totalQuizCount = computed(() => quizzes.value.length)

    const rankings = computed(() => {
        const roomStore = useRoomStore()
        const userScores = Object.entries(scores.value).map(([userId, score]) => ({
            userId,
            score: score as number,
            username: roomStore.users.find((u: any) => u.id === userId)?.username || 'Unknown'
        }));
        return userScores.sort((a, b) => b.score - a.score);
    })

    // Actions

    // 게임 시작
    const startGame = () => {
        const roomStore = useRoomStore()

        // Initialize scores
        roomStore.users.forEach(user => {
            scores.value[user.id] = 0
        })

        currentQuizIndex.value = 0
        isGameEnded.value = false
        timeRemaining.value = 5
        currentTurnUserId.value = roomStore.users[0]?.id || null
        quizSubmissions.value = {}
        lastAnswerResult.value = null

        startTimer()
    }

    // 답안 제출 (동시 입력)
    const submitAnswer = (userId: string, answer: number) => {
        // 이미 제출했으면 무시
        if (quizSubmissions.value[userId]) return

        quizSubmissions.value[userId] = {
            userId,
            answer,
            timestamp: Date.now()
        }
    }

    // 결과 판정 (타이머 종료 시 호출)
    const concludeQuiz = () => {
        if (!currentQuiz.value) return

        const correctAnswer = currentQuiz.value.answer
        const submissions = Object.values(quizSubmissions.value)

        // 1. 턴 주인 확인
        const ownerSubmission = submissions.find(s => s.userId === currentTurnUserId.value)

        let winnerId: string | null = null

        // 턴 주인이 맞췄는지 확인
        if (ownerSubmission && ownerSubmission.answer === correctAnswer) {
            winnerId = ownerSubmission.userId
        } else {
            // 턴 주인 실패 -> 스틸 기회 (나머지 중 가장 빠른 정답자)
            const otherCorrectSubmissions = submissions
                .filter(s => s.userId !== currentTurnUserId.value && s.answer === correctAnswer)
                .sort((a, b) => a.timestamp - b.timestamp) // 시간순 정렬

            if (otherCorrectSubmissions.length > 0) {
                winnerId = otherCorrectSubmissions[0].userId
            }
        }

        if (winnerId) {
            scores.value[winnerId] = (scores.value[winnerId] || 0) + 10
            lastAnswerResult.value = true
        } else {
            lastAnswerResult.value = false
        }

        // 잠시 대기 후 다음으로
        setTimeout(() => {
            nextQuiz()
        }, 2000)
    }

    // 다음 퀴즈 준비
    const nextQuiz = () => {
        stopTimer()
        if (currentQuizIndex.value < quizzes.value.length - 1) {
            currentQuizIndex.value++
            timeRemaining.value = 5
            quizSubmissions.value = {}
            lastAnswerResult.value = null

            // TODO: Rotate Turn User
            // currentTurnUserId.value = getNextUser(...)

            startTimer()
        } else {
            endGame()
        }
    }

    const endGame = () => {
        isGameEnded.value = true
        stopTimer()
    }

    const startTimer = () => {
        stopTimer()
        timerInterval.value = window.setInterval(() => {
            if (timeRemaining.value > 0) {
                timeRemaining.value--
            } else {
                // 시간 초과 -> 자동 결산
                stopTimer()
                concludeQuiz()
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
        timeRemaining.value = 5
        startTimer()
    }

    return {
        quizzes,
        currentQuiz,
        currentQuizIndex,
        isGameEnded,
        scores,
        timeRemaining,
        rankings,

        // New State
        currentTurnUserId,
        quizSubmissions,
        lastAnswerResult,

        startGame,
        submitAnswer,
        nextQuiz,
        endGame,
        startTimer,
        stopTimer,
        resetTimer,
        concludeQuiz
    }
})