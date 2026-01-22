// WebSocket 연결 관리 Pinia Store
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { WebSocketService } from '@/services/websocket'
import { useUserStore } from './userStore'
import { useRoomStore } from './roomStore'
import { useGameStore } from './gameStore'
import router from '@/router'

// 백엔드 DTO 타입 정의
export interface GameStateResponse {
    state: 'WAITING' | 'PLAYING' | 'FINISHED'
    players: PlayerInfo[]
}

export interface PlayerInfo {
    userId: string
    nickname: string
    isHost: boolean
    host?: boolean // 서버에서 오는 필드명 대응
    connected: boolean
    isReady: boolean
    ready?: boolean // 서버에서 오는 필드명 대응
    avatarId?: number // 아바타 ID 추가
}

export interface ErrorResponse {
    code: string
    message: string
}

const AVATAR_MAP: Record<number, string> = {
    0: '🐱',
    1: '🐶',
    2: '🦊',
    3: '🐰',
    4: '🐯',
    5: '🐸',
    6: '🐨',
    7: '🐷'
}

export const useWebSocketStore = defineStore('websocket', () => {
    // State
    const wsService = ref<WebSocketService | null>(null)
    const isConnected = ref(false)
    const isConnecting = ref(false)
    const lastError = ref<string | null>(null)

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

        wsService.value.subscribe(`/topic/game/${roomId}/state`, (data: any) => {
            console.log('📨 Game State Update:', data)

            // 1. GameStore 업데이트 (게임 진행 상태)
            gameStore.setGameState(data)

            // 2. RoomStore 업데이트 (로비/유저 목록 상태)
            const players = Array.isArray(data.players) ? data.players : []
            const nextUsers = players.map((player: any) => {
                const isMe = player.userId === userStore.userId

                // 백엔드 naming 이슈(isReady vs ready, isHost vs host)를 방어적으로 처리
                const isReady = player.isReady !== undefined ? player.isReady : (player.ready !== undefined ? player.ready : false)
                const isHost = player.isHost !== undefined ? player.isHost : (player.host !== undefined ? player.host : false)

                // 아바타 결정 로직:
                let avatar = '🎮'
                const cachedAvatar = avatarCache.value[player.userId]

                if (player.avatarId !== null && player.avatarId !== undefined) {
                    avatar = AVATAR_MAP[player.avatarId] || '🎮'
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

            roomStore.setUsers(nextUsers)
            roomStore.setRoomInfo({
                status: data.state || data.status || 'WAITING',
                currentCount: nextUsers.length
            })
        })

        // 아바타 전용 구독 (경량 업데이트용)
        wsService.value.subscribe(`/topic/game/${roomId}/avatar`, (data: any) => {
            console.log('📨 Avatar Update:', data)
            const emoji = AVATAR_MAP[data.avatarId] || '🎮'

            // 캐시 먼저 업데이트
            avatarCache.value[data.userId] = emoji

            // 스토어 업데이트
            roomStore.updateUser(data.userId, { avatar: emoji })

            if (data.userId === userStore.userId) {
                userStore.updateAvatar(emoji)
            }
        })

        // 이동/룰렛 이벤트 구독
        wsService.value.subscribe(`/topic/game/${roomId}/move`, (data: any) => {
            console.log('📨 Move Event:', data)
            gameStore.handleMove(data)
        })

        // 퀴즈 문제 수신
        wsService.value.subscribe(`/topic/game/${roomId}/quiz`, (data: any) => {
            console.log('📨 Quiz Received:', data)
            gameStore.onQuizReceived(data)
        })

        // 라운드 결과 수신
        wsService.value.subscribe(`/topic/game/${roomId}/result`, (data: any) => {
            console.log('📨 Round Result:', data)
            gameStore.onRoundResultReceived(data)
        })

        // 방 종료/삭제 알림
        wsService.value.subscribe(`/topic/game/${roomId}/closed`, (data: any) => {
            console.log('📨 Room Closed:', data)
            alert('방이 종료되었습니다.')
            cleanup()
            roomStore.leaveRoom()
            gameStore.reset()
            router.push('/')
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
            alert(`에러: ${data.message}`)
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
    }

    return {
        // State
        isConnected,
        isConnecting,
        lastError,

        // Getters
        connectionStatus,

        // Actions
        connect,
        disconnect,
        subscribeGameState,
        subscribeErrors,
        joinGame,
        leaveGame,
        submitAnswer,
        setReady,
        setAvatar,
        startGame,
        endGame,
        restartGame,
        unsubscribe,
        cleanup
    }
})
