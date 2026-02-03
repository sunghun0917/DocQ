// API 테스트 스크립트
// 브라우저 콘솔에서 실행하거나, 별도 페이지에서 테스트할 수 있습니다.

import {
    guestLogin,
    createRoom,
    getRoomByInviteCode,
    verifyJoinRoom,
    getErrorMessage
} from './services/api'

import { useWebSocketStore } from './stores/websocketStore'

// 랜덤 닉네임 생성 (중복 방지)
const generateRandomNickname = () => {
    const timestamp = Date.now()
    const random = Math.floor(Math.random() * 1000)
    return `테스트유저_${timestamp}_${random}`
}

// 전역에서 사용할 수 있도록 export
export const testAPI = {
    // 1. 사용자(게스트) 로그인 테스트
    async testUserLogin() {
        console.log('🧪 [TEST] 게스트 로그인 테스트')
        try {
            const nickname = generateRandomNickname()
            const result = await guestLogin(nickname)
            console.log('✅ 성공:', result)
            return result
        } catch (error) {
            console.error('❌ 실패:', getErrorMessage(error))
            throw error
        }
    },

    // 2. 방 생성 테스트 (세션의 playerId로 호스트 지정)
    async testCreateRoom() {
        console.log('🧪 [TEST] 방 생성 테스트')
        try {
            const result = await createRoom({
                capacity: 4,
                title: '테스트 방',
                isPrivate: false
            })
            console.log('✅ 성공:', result)
            return result
        } catch (error) {
            console.error('❌ 실패:', getErrorMessage(error))
            throw error
        }
    },


    // 4. 입장 코드로 방 조회 테스트
    async testGetRoomByInviteCode(inviteCode: string) {
        console.log('🧪 [TEST] 방 조회 테스트')
        try {
            const result = await getRoomByInviteCode(inviteCode)
            console.log('✅ 성공:', result)
            return result
        } catch (error) {
            console.error('❌ 실패:', getErrorMessage(error))
            throw error
        }
    },

    // 5. 방 참가 검증 테스트 (공개방)
    async testJoinPublicRoom(roomId: string) {
        console.log('🧪 [TEST] 공개방 참가 테스트')
        try {
            const result = await verifyJoinRoom({ roomId })
            console.log('✅ 성공:', result)
            return result
        } catch (error) {
            console.error('❌ 실패:', getErrorMessage(error))
            throw error
        }
    },


    // 7. WebSocket 연결 테스트
    async testWebSocketConnect() {
        console.log('🧪 [TEST] WebSocket 연결 테스트')
        try {
            const wsStore = useWebSocketStore()
            await wsStore.connect()
            console.log('✅ WebSocket 연결 성공')
            return wsStore
        } catch (error) {
            console.error('❌ WebSocket 연결 실패:', error)
            throw error
        }
    },

    // 8. 게임 참가 테스트 (WebSocket)
    async testGameJoin(roomId: string, userId: string, nickname: string) {
        console.log('🧪 [TEST] 게임 참가 테스트 (WebSocket)')
        try {
            const wsStore = useWebSocketStore()

            // 게임 상태 구독
            wsStore.subscribeGameState(roomId)
            wsStore.subscribeErrors(userId)

            // 게임 참가 메시지 발행
            wsStore.joinGame(roomId, userId, nickname)

            console.log('✅ 게임 참가 메시지 전송 완료')
            console.log('💡 게임 상태 업데이트를 기다리는 중...')
        } catch (error) {
            console.error('❌ 게임 참가 실패:', error)
            throw error
        }
    },

    // 9. 준비 상태 변경 테스트
    async testSetReady(roomId: string, userId: string, isReady: boolean) {
        console.log(`🧪 [TEST] 준비 상태 변경 테스트 (${isReady ? '준비' : '준비 취소'})`)
        try {
            const wsStore = useWebSocketStore()
            wsStore.setReady(roomId, userId, isReady)
            console.log('✅ 준비 상태 변경 메시지 전송 완료')
        } catch (error) {
            console.error('❌ 준비 상태 변경 실패:', error)
            throw error
        }
    },

    // 10. 게임 시작 테스트
    async testStartGame(roomId: string, userId: string) {
        console.log('🧪 [TEST] 게임 시작 테스트')
        try {
            const wsStore = useWebSocketStore()
            wsStore.startGame(roomId, userId)
            console.log('✅ 게임 시작 메시지 전송 완료')
        } catch (error) {
            console.error('❌ 게임 시작 실패:', error)
            throw error
        }
    },

    // 전체 플로우 테스트 (REST API + WebSocket)
    async testFullFlowWithWebSocket() {
        console.log('🚀 [TEST] 전체 플로우 테스트 (WebSocket 포함) 시작\n')

        try {
            // 1. 사용자 로그인
            console.log('1️⃣ 사용자 로그인...')
            const user = await this.testUserLogin()
            console.log(`   userId: ${user.playerId}\n`)

            // 2. 방 생성
            console.log('2️⃣ 방 생성...')
            const room = await this.testCreateRoom()
            console.log(`   roomId: ${room.roomId}`)
            console.log(`   inviteCode: ${room.inviteCode}\n`)

            // 3. WebSocket 연결
            console.log('3️⃣ WebSocket 연결...')
            await this.testWebSocketConnect()
            console.log('   연결 완료\n')

            // 4. 게임 참가
            console.log('4️⃣ 게임 참가...')
            await this.testGameJoin(room.roomId, user.playerId, user.nickname)
            console.log('   참가 메시지 전송 완료\n')

            // 5. 준비 상태 변경
            console.log('5️⃣ 준비 상태 변경...')
            await new Promise(resolve => setTimeout(resolve, 1000)) // 1초 대기
            await this.testSetReady(room.roomId, user.playerId, true)
            console.log('   준비 완료\n')

            // 6. 게임 시작 (준비 완료 상태에서만 가능하므로 잠시 대기 후 실행)
            console.log('6️⃣ 게임 시작...')
            await new Promise(resolve => setTimeout(resolve, 2000)) // 2초 대기
            await this.testStartGame(room.roomId, user.playerId)
            console.log('   게임 시작 요청 완료\n')

            console.log('✅ 전체 플로우 테스트 성공!')
            console.log('💡 콘솔에서 게임 상태 업데이트 메시지(status: "PLAYING")를 확인하세요!')

            return { user, room }

        } catch (error) {
            console.error('❌ 전체 플로우 테스트 실패')
            throw error
        }
    },

    // 기존 REST API만 테스트
    async testFullFlow() {
        console.log('🚀 [TEST] 전체 플로우 테스트 시작\n')

        try {
            // 1. 사용자 로그인
            console.log('1️⃣ 사용자 로그인...')
            const user = await this.testUserLogin()
            console.log(`   userId: ${user.playerId}\n`)

            // 2. 방 생성
            console.log('2️⃣ 방 생성...')
            const room = await this.testCreateRoom()
            console.log(`   roomId: ${room.roomId}`)
            console.log(`   inviteCode: ${room.inviteCode}\n`)

            // 3. 방 조회
            console.log('3️⃣ 입장 코드로 방 조회...')
            const roomInfo = await this.testGetRoomByInviteCode(room.inviteCode)
            console.log(`   방 제목: ${roomInfo.title}`)
            console.log(`   현재 인원: ${roomInfo.currentCount}/${roomInfo.capacity}\n`)

            // 4. 방 참가 검증
            console.log('4️⃣ 방 참가 검증...')
            const joinResult = await this.testJoinPublicRoom(room.roomId)
            console.log(`   상태: ${joinResult.status}\n`)

            console.log('✅ 전체 플로우 테스트 성공!')
            return { user, room, roomInfo, joinResult }

        } catch (error) {
            console.error('❌ 전체 플로우 테스트 실패')
            throw error
        }
    }
}

// 브라우저 콘솔에서 사용할 수 있도록 window 객체에 추가
if (typeof window !== 'undefined') {
    (window as any).testAPI = testAPI
}
