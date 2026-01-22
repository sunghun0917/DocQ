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
// import { useWebSocketStore } from './websocketStore' // Cyclic Dependency Risk

import type { Quiz } from '@/types/docq-api'

// 게임 진행 상태 및 퀴즈 관리
export const useGameStore = defineStore('game', () => {
    // state

    // 퀴즈 관련
    const quizzes = ref<Quiz[]>([])
    const currentQuizIndex = ref(0)
    // const currentQuiz = computed(() => quizzes.value[currentQuizIndex.value])
    const currentQuiz = ref<Quiz | null>(null) // Live Quiz Object

    // 게임 상태
    const gameStatus = ref<'waiting' | 'playing' | 'finished'>('waiting')
    const timeRemaining = ref(30)

    // 점수
    const scores = ref<Record<string, number>>({})

    // 현재 퀴즈 정답 여부 (UI 피드백용)
    const lastAnswerResult = ref<boolean | null>(null)

    // === Added for GameBoard.vue support ===
    const gameState = ref<{
        players: any[],
        currentTurnUserId: string | null,
        round: number | null,
        // ... other fields from backend
    }>({
        players: [],
        currentTurnUserId: null,
        round: 1
    })

    const currentMove = ref<any>(null)

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

    // 현재 턴 플레이어
    const currentTurnPlayer = computed(() => {
        if (!gameState.value.currentTurnUserId) return null
        return gameState.value.players.find(p => p.userId === gameState.value.currentTurnUserId)
    })

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
        lastAnswerResult.value = null

        // 타이머 시작
        startTimer()
    }

    const setGameState = (newState: any) => {
        // 병합 또는 교체
        gameState.value = {
            ...gameState.value,
            ...newState,
            players: newState.players || []
        }

        // Update local scores if needed
        if (newState.players) {
            newState.players.forEach((p: any) => {
                scores.value[p.userId] = p.score || 0
            })
        }

        // Sync Status
        if (newState.state) {
            if (newState.state === 'PLAYING') gameStatus.value = 'playing'
            else if (newState.state === 'WAITING') gameStatus.value = 'waiting'
            // ...
        }
    }

    const handleMove = (moveInfo: any) => {
        currentMove.value = moveInfo
        // 애니메이션 후 처리는 watch에서 담당하거나 여기서 delay 줄 수 있음
    }

    // === Live Game Logic ===
    const currentRoundResult = ref<any>(null)

    const onQuizReceived = (quizData: any) => {
        // 백엔드 QuizMessage: { quizId, question, options, timeLimit }
        currentQuiz.value = {
            quizId: quizData.quizId,
            question: quizData.question,
            options: quizData.options,
            timeLimit: quizData.timeLimit ? Math.floor(quizData.timeLimit / 1000) : 30, // ms to sec
            answer: -1 // 정답은 모름 (보안)
        }

        // 퀴즈 시작 (타이머)
        currentQuizIndex.value = 0 // Using single slot for now
        timeRemaining.value = currentQuiz.value.timeLimit || 30
        gameStatus.value = 'playing'
        lastAnswerResult.value = null
        currentRoundResult.value = null // Reset previous result

        startTimer()
    }

    const onRoundResultReceived = (resultData: any) => {
        // 백엔드 RoundResult: { quizId, correctAnswer, isTurnUserCorrect, winnerUserId, rouletteType }
        stopTimer()
        currentRoundResult.value = resultData

        // 정답 공개 처리
        // currentQuiz.value.answerText = resultData.correctAnswer

        // UI 효과를 위해 잠시 대기 후 모달 표시 등은 View에서 처리
    }

    // 다음 퀴즈 (Local Mock Logic - Deprecated for Live)
    // const nextQuiz = () => { ... }

    // 답안 제출 (WebSocket)
    const submitAnswer = (userId: string, answerText: string) => {
        // WebSocket logic is handled in View or WebSocketStore to avoid cyclic dependency
        // View에서 websocketStore.submitAnswer()를 직접 호출합니다.
    }

    // 점수 업데이트 (Server Sync)
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
                // nextQuiz() // Deprecated: Server drives the game flow
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
        lastAnswerResult.value = null
        gameState.value = { players: [], currentTurnUserId: null, round: 1 }
        currentMove.value = null
    }

    return {
        // State
        quizzes,
        currentQuizIndex,
        currentQuiz,
        gameStatus,
        timeRemaining,
        scores,
        lastAnswerResult,
        gameState,
        currentMove,
        currentRoundResult,

        // Getters
        totalQuizCount,
        rankings,
        isGameFinished,
        currentTurnPlayer,

        // Actions
        startGame,
        setGameState,
        handleMove,
        onQuizReceived,
        onRoundResultReceived,
        // @ts-ignore
        // nextQuiz,
        submitAnswer,
        updateScore,
        endGame,
        startTimer,
        stopTimer,
        reset
    }
})
