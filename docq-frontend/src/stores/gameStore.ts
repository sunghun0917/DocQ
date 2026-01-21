// 게임 진행 상태 및 퀴즈 관리
/*
게임 시작:
1. startGame() 호출
2. 점수 초기화
3. 첫 번째 퀴즈 로드
4. 타이머 시작

퀴즈 진행:
1. 사용자가 답 선택
2. submitAnswer() 호출
3. 정답 확인 및 점수 업데이트
4. nextQuiz() 호출

게임 종료:
1. 마지막 퀴즈 완료
2. endGame() 호출
3. 순위 계산
4. ResultModal 표시
*/

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRoomStore } from './roomStore'
import { mockQuizzes } from '@/mocks/quizzes'

// 게임 진행 상태 및 퀴즈 관리
export const useGameStore = defineStore('game', () => {
    // state

    // 퀴즈 관련
    const quizzes = ref(mockQuizzes)
    const currentQuizIndex = ref(0)
    const currentQuiz = computed(() => quizzes.value[currentQuizIndex.value])

    // 게임 상태
    const gameStatus = ref<'waiting' | 'playing' | 'finished'>('waiting')
    const timeRemaining = ref(30)

    // 점수
    const scores = ref<Record<string, number>>({})

    // Getters

    // 전체 퀴즈 개수
    const totalQuizCount = computed(() => quizzes.value.length)

    // 순위 계산
    const rankings = computed(() => {
        const roomStore = useRoomStore()
        // Convert scores object to an array of { userId, score } and sort
        const userScores = Object.entries(scores.value).map(([userId, score]) => ({
            userId,
            score: score as number,
            nickname: roomStore.users.find((u: any) => u.id === userId)?.nickname || 'Unknown'
        }));
        return userScores.sort((a, b) => b.score - a.score);
    })

    // 게임 종료 여부
    const isGameFinished = computed(() => gameStatus.value === 'finished')

    // Actions

    let timerInterval: number | null = null

    // 게임 시작
    const startGame = () => {
        const roomStore = useRoomStore()

        // roomStore의 사용자 목록으로 점수 초기화
        roomStore.users.forEach(user => {
            scores.value[user.id] = 0
        })

        currentQuizIndex.value = 0
        gameStatus.value = 'playing'
        timeRemaining.value = currentQuiz.value?.timeLimit || 30

        // 타이머 시작
        startTimer()
    }

    // 다음 퀴즈
    const nextQuiz = () => {
        stopTimer()

        if (currentQuizIndex.value < quizzes.value.length - 1) {
            currentQuizIndex.value++
            timeRemaining.value = currentQuiz.value?.timeLimit || 30
            startTimer()
        } else {
            endGame()
        }
    }

    // 답안 제출
    const submitAnswer = (userId: string, answerIndex: number) => {
        if (!currentQuiz.value) return

        // 정답 확인
        const isCorrect = currentQuiz.value.answer === answerIndex

        if (isCorrect) {
            // 기본 점수
            let points = 10

            // 시간 보너스 (남은 시간에 비례)
            const timeBonus = Math.floor(timeRemaining.value / 3)
            points += timeBonus

            // 점수 추가
            scores.value[userId] = (scores.value[userId] || 0) + points
        }

        // 다음 퀴즈로 (약간의 딜레이 후)
        setTimeout(() => {
            nextQuiz()
        }, 1000)
    }

    // 점수 업데이트
    const updateScore = (userId: string, score: number) => {
        scores.value[userId] = score
    }

    // 게임 종료
    const endGame = () => {
        stopTimer()
        gameStatus.value = 'finished'
    }

    // 타이머 시작
    const startTimer = () => {
        stopTimer() // 기존 타이머 정리

        timerInterval = window.setInterval(() => {
            if (timeRemaining.value > 0) {
                timeRemaining.value--
            } else {
                // 시간 초과 시 자동으로 다음 퀴즈
                nextQuiz()
            }
        }, 1000)
    }

    // 타이머 정지
    const stopTimer = () => {
        if (timerInterval !== null) {
            clearInterval(timerInterval)
            timerInterval = null
        }
    }

    // 초기화
    const reset = () => {
        stopTimer()
        currentQuizIndex.value = 0
        scores.value = {}
        gameStatus.value = 'waiting'
        timeRemaining.value = 30
    }
    return {
        // State
        quizzes,
        currentQuizIndex,
        currentQuiz,
        gameStatus,
        timeRemaining,
        scores,

        // Getters
        totalQuizCount,
        rankings,
        isGameFinished,

        // Actions
        startGame,
        nextQuiz,
        submitAnswer,
        updateScore,
        endGame,
        startTimer,
        stopTimer,
        reset
    }
})
