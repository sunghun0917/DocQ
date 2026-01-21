// WebSocket 연결 관리 Pinia Store
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { WebSocketService } from '@/services/websocket'
import { useUserStore } from './userStore'
import { useRoomStore } from './roomStore'

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
}

export interface ErrorResponse {
    code: string
    message: string
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
                url: import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws-docq',

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

        wsService.value.subscribe(`/topic/game/${roomId}/state`, (data: any) => {
            console.log('📨 Game State Update:', data)

            const players = Array.isArray(data.players) ? data.players : []
            const nextUsers = players.map((player: any) => {
                const existingUser = roomStore.users.find(u => u.id === player.userId)
                const isMe = player.userId === userStore.userId

                // 백엔드 naming 이슈(isReady vs ready, isHost vs host)를 방어적으로 처리
                const isReady = player.isReady !== undefined ? player.isReady : (player.ready !== undefined ? player.ready : false)
                const isHost = player.isHost !== undefined ? player.isHost : (player.host !== undefined ? player.host : false)

                return {
                    id: player.userId,
                    nickname: player.nickname,
                    avatar: existingUser?.avatar || (isMe ? userStore.avatar : '🎮'),
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
        setReady,
        startGame,
        unsubscribe,
        cleanup
    }
})
