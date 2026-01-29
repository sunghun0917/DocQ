// WebSocket 연결 관리 Pinia Store
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { WebSocketService } from '@/services/websocket'
import type { GameStateResponse, PlayerInfo, RoundResult, MoveInfo, QuizMessage, QuizOptionsMessage } from '@/types/docq-api'
import { useUserStore } from './userStore'
import { useRoomStore } from './roomStore'
import { useGameStore } from './gameStore'
import { useUiStore } from './uiStore'
import router from '@/router'

export interface ErrorResponse {
    code: string
    message: string
}

export interface QuizGeneratedNotice {
    count: number
    type?: string // [ADD]
    fileName?: string
    quizType?: string
}

const AVATAR_MAP: Record<number, string> = {
    0: '🐔',
    1: '🐶',
    2: '🐷',
    3: '🐑'
}

export const useWebSocketStore = defineStore('websocket', () => {
    // State
    const wsService = ref<WebSocketService | null>(null)
    const isConnected = ref(false)
    const isConnecting = ref(false)
    const lastError = ref<string | null>(null)
    const quizGeneratedNotice = ref<QuizGeneratedNotice | null>(null)

    // Getters
    const connectionStatus = computed(() => {
        if (isConnecting.value) return 'connecting'
        if (isConnected.value) return 'connected'
        return 'disconnected'
    })

    // Actions

    /**
     * WebSocket 연결
     */
    const connect = async () => {
        if (isConnected.value || isConnecting.value) {
            console.warn('Already connected or connecting')
            return
        }

        isConnecting.value = true
        lastError.value = null

        try {
            // WebSocket 서비스 생성
            wsService.value = new WebSocketService({
                url: import.meta.env.VITE_WS_URL || '/ws-docq',

                onConnect: () => {
                    isConnected.value = true
                    isConnecting.value = false
                    console.log('✅ WebSocket Store: Connected')
                },

                onDisconnect: () => {
                    isConnected.value = false
                    isConnecting.value = false
                    console.log('🔌 WebSocket Store: Disconnected')
                },

                onError: (error) => {
                    lastError.value = error?.message || 'WebSocket error'
                    isConnecting.value = false
                    console.error('❌ WebSocket Store: Error', error)
                }
            })

            await wsService.value.connect()
        } catch (error: any) {
            lastError.value = error?.message || 'Connection failed'
            isConnecting.value = false
            throw error
        }
    }

    /**
     * WebSocket 연결 해제
     */
    const disconnect = () => {
        if (wsService.value) {
            wsService.value.disconnect()
            wsService.value = null
            isConnected.value = false
        }
    }

    // 아바타 정보 유실 방지를 위한 로컬 캐시
    const avatarCache = ref<Record<string, string>>({})

    /**
     * 게임 상태 구독
     * @param roomId - 방 ID
     */
    const subscribeGameState = (roomId: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        const roomStore = useRoomStore()
        const userStore = useUserStore()
        const gameStore = useGameStore() // Inject GameStore

        wsService.value.subscribe(`/topic/game/${roomId}/state`, (data: GameStateResponse) => {
            console.log('📨 Game State Update:', data)

            // 1. GameStore 업데이트 (게임 진행 상태)
            gameStore.setGameState(data)

            // 2. RoomStore 업데이트 (로비/유저 목록 상태)
            const players = Array.isArray(data.players) ? data.players : []
            const nextUsers = players.map((player: PlayerInfo) => {
                const isMe = player.userId === userStore.userId

                // 백엔드 naming 이슈(isReady vs ready, isHost vs host)를 방어적으로 처리
                const isReady = player.isReady !== undefined ? player.isReady : (player.ready !== undefined ? player.ready : false)
                const isHost = player.isHost !== undefined ? player.isHost : (player.host !== undefined ? player.host : false)

                // 아바타 결정 로직:
                let avatar = '❓'
                const cachedAvatar = avatarCache.value[player.userId]

                if (player.avatarId !== null && player.avatarId !== undefined) {
                    avatar = AVATAR_MAP[player.avatarId] || '❓'
                    avatarCache.value[player.userId] = avatar // 캐시 업데이트
                } else if (cachedAvatar) {
                    avatar = cachedAvatar
                } else if (isMe) {
                    avatar = userStore.avatar
                    avatarCache.value[player.userId] = avatar
                }

                return {
                    id: player.userId,
                    nickname: player.nickname,
                    avatar: avatar,
                    isReady: !!isReady,
                    isHost: !!isHost,
                    isMe
                }
            })

            const userById = new Map(nextUsers.map((user: any) => [user.id, user]))
            const hostUserId = nextUsers.find((user: any) => user.isHost)?.id
            const orderedIds: string[] = []

            if (hostUserId) {
                orderedIds.push(hostUserId)
            }

            const turnOrder = Array.isArray(data.turnOrder) ? data.turnOrder : []
            turnOrder.forEach((id: string) => {
                if (id && id !== hostUserId) orderedIds.push(id)
            })

            nextUsers.forEach((user: any) => {
                if (!orderedIds.includes(user.id)) orderedIds.push(user.id)
            })

            const orderedUsers = orderedIds
                .map(id => userById.get(id))
                .filter((user): user is typeof nextUsers[number] => !!user)

            roomStore.setUsers(orderedUsers)
            roomStore.setRoomInfo({
                status: data.state || data.status || 'WAITING',
                currentCount: orderedUsers.length
            })
        })

        // 아바타 전용 구독 (경량 업데이트용)
        wsService.value.subscribe(`/topic/game/${roomId}/avatar`, (data: any) => {
            console.log('📨 Avatar Update:', data)
            const emoji = AVATAR_MAP[data.avatarId] || '❓'

            // 캐시 먼저 업데이트
            avatarCache.value[data.userId] = emoji

            // 스토어 업데이트
            roomStore.updateUser(data.userId, { avatar: emoji })

            if (data.userId === userStore.userId) {
                userStore.updateAvatar(emoji)
            }
        })

        // 이동/룰렛 이벤트 구독
        wsService.value.subscribe(`/topic/game/${roomId}/move`, (data: MoveInfo) => {
            console.log('📨 Move Event:', data)
            gameStore.handleMove(data)
        })

        // 퀴즈 문제 수신
        wsService.value.subscribe(`/topic/game/${roomId}/quiz`, (data: QuizMessage & { type?: string; count?: number; fileName?: string; quizType?: string }) => {
            console.log('📨 Quiz Received:', data)

            if (data?.type === 'QUIZ_GENERATING' || data?.type === 'QUIZ_GENERATED' || data?.type === 'QUIZ_RESET') {
                quizGeneratedNotice.value = {
                    count: Number(data.count ?? 0),
                    type: data.type,
                    fileName: data.fileName,
                    quizType: data.quizType
                }
                return
            }

            if (data?.question) {
                gameStore.onQuizReceived(data)
            }
        })

        if (userStore.userId) {
            wsService.value.subscribe(`/queue/game/${userStore.userId}/quiz`, (data: QuizMessage) => {
                console.log('📨 Private Quiz Received:', data)
                if (data?.question) {
                    gameStore.onQuizReceived(data)
                }
            })
        }

        wsService.value.subscribe(`/topic/game/${roomId}/quiz/options`, (data: QuizOptionsMessage) => {
            console.log('📨 Quiz Options Revealed:', data)
            if (data?.quizId && Array.isArray(data.options)) {
                gameStore.revealQuizOptions(data.quizId, data.options, data.extraTimeMs)
            }
        })

        if (userStore.userId) {
            wsService.value.subscribe(`/queue/game/answer-feedback/${userStore.userId}`, (data: { correct?: boolean }) => {
                console.log('📨 Answer Feedback:', data)
                if (data && typeof data.correct === 'boolean') {
                    gameStore.setTurnPlayerFeedback(data.correct)
                }
            })
        }

        // 라운드 결과 수신 시 턴 피드백(틀렸습니다) 제거 → 시스템 알림 + 정답 체크만 표시
        wsService.value.subscribe(`/topic/game/${roomId}/result`, (data: RoundResult) => {
            console.log('📨 Round Result:', data)
            gameStore.clearTurnPlayerFeedback()
            gameStore.onRoundResultReceived(data)
        })

        // 방 종료/삭제 알림
        wsService.value.subscribe(`/topic/game/${roomId}/closed`, (data: any) => {
            console.log('📨 Room Closed:', data)
            const uiStore = useUiStore()
            uiStore.openConfirm({
                title: '방 종료',
                message: '방이 종료되었습니다.',
                confirmLabel: '확인',
                icon: 'warning',
                variant: 'danger',
                showCancel: false
            })
            cleanup()
            roomStore.leaveRoom()
            gameStore.reset()
            router.push('/')
        })
    }

    /**
     * 미니게임 이벤트 구독
     */
    const subscribeMiniGame = (roomId: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        const gameStore = useGameStore()

        wsService.value.subscribe(`/topic/game/${roomId}/minigame`, (data: any) => {
            console.log('📨 MiniGame Event:', data)

            if (data?.type === 'START') {
                gameStore.clearQuiz()
                gameStore.queueMiniGame({
                    gameType: data.gameType,
                    timeLimit: data.timeLimit,
                    description: data.description
                })
                return
            }

            if (data?.type === 'RESULT') {
                gameStore.setMiniGameResult({
                    winnerUserId: data.winnerUserId,
                    rewardBuff: data.rewardBuff,
                    rankings: data.rankings || []
                })
            }
        })
    }

    /**
     * 정답 제출
     */
    const submitAnswer = (roomId: string, userId: string, quizId: number, answer: string) => {
        if (!wsService.value) return

        wsService.value.publish('/app/game/submit', {
            roomId,
            userId,
            quizId,
            answer
        })
    }

    /**
     * 에러 메시지 구독
     * @param userId - 사용자 ID
     */
    const subscribeErrors = (userId: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.subscribe(`/queue/errors/${userId}`, (data: ErrorResponse) => {
            console.error('❌ Server Error:', data)
            lastError.value = `[${data.code}] ${data.message}`

            // 에러 알림 표시 (Toast 등)
            const uiStore = useUiStore()
            uiStore.openConfirm({
                title: '에러',
                message: data.message,
                confirmLabel: '확인',
                icon: 'warning',
                variant: 'danger',
                showCancel: false
            })
        })
    }

    /**
     * 게임 참가
     */
    const joinGame = (roomId: string, userId: string, nickname: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.publish('/app/game/join', {
            roomId,
            userId,
            nickname
        })
    }

    /**
     * 게임 나가기
     */
    const leaveGame = (roomId: string, userId: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.publish('/app/game/leave', {
            roomId,
            userId
        })
    }

    /**
     * 준비 상태 변경
     */
    const setReady = (roomId: string, userId: string, isReady: boolean) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        // 서버의 Jackson 매핑 이슈를 고려하여 둘 다 전송
        wsService.value.publish('/app/game/ready', {
            roomId,
            userId,
            isReady: isReady, // Java 필드명
            ready: isReady   // Jackson 기본 매핑용 키
        })
    }

    /**
     * 아바타 설정
     */
    const setAvatar = (roomId: string, userId: string, avatarId: number) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.publish('/app/game/avatar', {
            roomId,
            userId,
            avatarId
        })
    }

    /**
     * 게임 시작 (Host Only)
     */
    const startGame = (roomId: string, userId: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.publish('/app/game/start', {
            roomId,
            userId
        })
    }

    /**
     * 게임 종료 (Host Only)
     */
    const endGame = (roomId: string, userId: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.publish('/app/game/end', {
            roomId,
            userId
        })
    }

    /**
     * 게임 재시작 (Host Only)
     */
    const restartGame = (roomId: string, userId: string) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.publish('/app/game/restart', {
            roomId,
            userId
        })
    }

    /**
     * 미니게임 결과 제출
     */
    const submitMiniGameResult = (roomId: string, userId: string, gameType: string, resultValue: number) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }

        wsService.value.publish('/app/game/minigame/submit', {
            roomId,
            userId,
            gameType,
            resultValue
        })
    }

    /**
     * 퀴즈 리셋 (다시 만들기 시작 알림)
     */
    const resetQuiz = (roomId: string) => {
        if (!wsService.value) return
        wsService.value.publish('/app/game/quiz/reset', { roomId })
    }

    /**
     * ACK 전송
     */
    const sendAck = (ackType: 'RESULT_DISPLAYED' | 'ROULETTE_COMPLETE' | 'MOVE_COMPLETE' | 'MINIGAME_COMPLETE' | 'BUFF_ROULETTE_COMPLETE' | 'CAMERA_END', quizId?: number, round?: number) => {
        if (!wsService.value) {
            console.error('WebSocket not connected')
            return
        }
        const roomStore = useRoomStore()
        const userStore = useUserStore()
        const roomId = roomStore.roomInfo.roomId
        const userId = userStore.userId

        if (!roomId || !userId) {
            return
        }

        wsService.value.publish('/app/game/ack', {
            roomId,
            userId,
            ackType,
            quizId,
            round
        })
    }

    /**
     * 특정 경로 구독 해제
     */
    const unsubscribe = (destination: string) => {
        if (wsService.value) {
            wsService.value.unsubscribe(destination)
        }
    }

    /**
     * 모든 구독 해제 및 연결 종료
     */
    const cleanup = () => {
        disconnect()
        lastError.value = null
        quizGeneratedNotice.value = null
    }

    const clearQuizGeneratedNotice = () => {
        quizGeneratedNotice.value = null
    }

    return {
        // State
        isConnected,
        isConnecting,
        lastError,
        quizGeneratedNotice,

        // Getters
        connectionStatus,

        // Actions
        connect,
        disconnect,
        subscribeGameState,
        subscribeMiniGame,
        subscribeErrors,
        joinGame,
        leaveGame,
        submitAnswer,
        setReady,
        setAvatar,
        startGame,
        endGame,
        restartGame,
        submitMiniGameResult,
        resetQuiz, // [ADD]
        sendAck,
        unsubscribe,
        cleanup,
        clearQuizGeneratedNotice
    }
})
