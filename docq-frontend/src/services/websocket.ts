// WebSocket 서비스 (STOMP over SockJS)
import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export interface WebSocketConfig {
    url: string
    connectHeaders?: Record<string, string> // [ADD] 연결 헤더 추가
    onConnect?: () => void
    onDisconnect?: () => void
    onError?: (error: any) => void
    debug?: (str: string) => void           // [ADD] 디버그 콜백 추가
    reconnectDelay?: number
    heartbeatIncoming?: number
    heartbeatOutgoing?: number
}

export class WebSocketService {
    private client: Client | null = null
    private subscriptions: Map<string, any> = new Map()

    constructor(private config: WebSocketConfig) { }

    /**
     * WebSocket 연결
     */
    connect(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.client = new Client({
                // SockJS를 사용한 WebSocket 연결
                webSocketFactory: () => new SockJS(this.config.url) as any,

                // 연결 헤더 설정 (userId 등)
                connectHeaders: this.config.connectHeaders,

                // 디버그 로그 (비활성화)
                debug: (str) => {
                    // 중요한 상태 변화 로그(연결됨, 에러남, 끊김)만 보이고, 지저분한 데이터 통신은 무시합니다.
                    if (str.includes('Connected') || str.includes('Error') || str.includes('DISCONNECT')) {
                        console.log('[STOMP Status]', str);
                    }
                },

                // 재연결 설정
                reconnectDelay: this.config.reconnectDelay || 5000,
                heartbeatIncoming: this.config.heartbeatIncoming || 4000,
                heartbeatOutgoing: this.config.heartbeatOutgoing || 4000,

                // 연결 성공
                onConnect: () => {
                    console.log('✅ WebSocket Connected')
                    this.config.onConnect?.()
                    resolve()
                },

                // 연결 실패
                onStompError: (frame) => {
                    console.error('❌ STOMP Error:', frame)
                    this.config.onError?.(frame)
                    reject(frame)
                },

                // 연결 종료
                onDisconnect: () => {
                    console.log('🔌 WebSocket Disconnected')
                    this.config.onDisconnect?.()
                }
            })

            this.client.activate()
        })
    }

    /**
     * WebSocket 연결 해제
     */
    disconnect(): void {
        if (this.client) {
            this.subscriptions.clear()
            this.client.deactivate()
            this.client = null
            console.log('🔌 WebSocket Manually Disconnected')
        }
    }

    /**
     * 메시지 구독
     * @param destination - 구독할 경로 (예: /topic/game/room123/state)
     * @param callback - 메시지 수신 시 호출될 콜백
     */
    subscribe(destination: string, callback: (message: any) => void): void {
        if (!this.client || !this.client.connected) {
            console.error('❌ WebSocket is not connected')
            return
        }

        // 이미 구독 중이면 무시
        if (this.subscriptions.has(destination)) {
            console.warn(`⚠️ Already subscribed to ${destination}`)
            return
        }

        const subscription = this.client.subscribe(destination, (message: IMessage) => {
            try {
                const data = JSON.parse(message.body)
                console.log(`📨 Received from ${destination}:`, data)
                callback(data)
            } catch (error) {
                console.error('Failed to parse message:', error)
            }
        })

        this.subscriptions.set(destination, subscription)
        console.log(`📡 Subscribed to ${destination}`)
    }

    /**
     * 구독 해제
     * @param destination - 구독 해제할 경로
     */
    unsubscribe(destination: string): void {
        const subscription = this.subscriptions.get(destination)
        if (subscription) {
            subscription.unsubscribe()
            this.subscriptions.delete(destination)
            console.log(`🔕 Unsubscribed from ${destination}`)
        }
    }

    /**
     * 메시지 발행
     * @param destination - 발행할 경로 (예: /app/game/join)
     * @param body - 전송할 데이터
     */
    publish(destination: string, body: any): void {
        if (!this.client || !this.client.connected) {
            console.error('❌ WebSocket is not connected')
            return
        }

        this.client.publish({
            destination,
            body: JSON.stringify(body)
        })

        console.log(`📤 Published to ${destination}:`, body)
    }

    /**
     * 연결 상태 확인
     */
    isConnected(): boolean {
        return this.client?.connected || false
    }
}

// 싱글톤 인스턴스 (선택적)
let websocketInstance: WebSocketService | null = null

export const getWebSocketService = (config?: WebSocketConfig): WebSocketService => {
    if (!websocketInstance && config) {
        websocketInstance = new WebSocketService(config)
    }
    if (!websocketInstance) {
        throw new Error('WebSocket service not initialized')
    }
    return websocketInstance
}

export const destroyWebSocketService = (): void => {
    if (websocketInstance) {
        websocketInstance.disconnect()
        websocketInstance = null
    }
}
