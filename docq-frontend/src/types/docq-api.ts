/**
 * ============================================================================
 * 🛠️ DocQ 공통 타입 정의 (Single Source of Truth)
 * ============================================================================
 */

/**
 * 게임 및 방의 상태 정의
 */
export type RoomStatus =
    | 'WAITING'
    | 'STARTING'
    | 'QUIZ_TIME'
    | 'JUDGMENT'
    | 'ROULETTE'
    | 'MOVE'
    | 'FINISHED'
    | 'PLAYING'
    | 'MINIGAME'

/**
 * ----------------------------------------------------------------------------
 * 📦 1. REST API 관련 타입 (HTTP)
 * ----------------------------------------------------------------------------
 */

// 회원가입
export interface SignUpRequest {
    loginId: string
    password: string
    nickname: string
}
export interface SignUpResponse {
    id: number
    loginId: string
    nickname: string
    createdAt: string
}

// 회원 로그인
export interface LoginRequest {
    loginId: string
    password: string
}
export interface LoginResponse {
    id: number
    loginId: string
    nickname: string
}

// 게스트 로그인 (Phase 1)
export interface GuestLoginRequest {
    nickname: string
}
export interface GuestLoginResponse {
    playerId: string
    nickname: string
}
export interface GuestNicknameUpdateRequest {
    nickname: string
}
export interface GuestNicknameUpdateResponse {
    playerId: string
    nickname: string
}

/**
 * 닉네임 중복 확인
 * GET /users/nickname/exists
 */
export interface CheckNicknameResponse {
    exists: boolean
    message: string
}

export interface CheckIdResponse {
    available: boolean
    message: string
}

// 방 생성
export interface CreateRoomRequest {
    playerId: string
    capacity: number
    title: string
    isPrivate?: boolean
}
export interface CreateRoomResponse {
    roomId: string
    inviteCode: string
    hostPlayerId: string
    isPrivate: boolean
}

// 방 정보 및 조회
export interface RoomResponse {
    roomId: string
    title: string
    isPrivate: boolean
    status: string
    currentCount: number
    capacity: number
    quizCount: number
}
export interface JoinRoomRequest {
    roomId: string
}
export interface JoinRoomResponse {
    roomId: string
    roomCode: string
    status: string
}

// 퀴즈 관리
export interface QuizResponse {
    quizId: number
    question: string
    options: string[]
    answerIndex: number
    answerText: string
}
export interface QuizGenerateResponse {
    message: string
    roomId: string
}

/**
 * ----------------------------------------------------------------------------
 * 🎮 2. WebSocket & 게임 엔진 관련 타입
 * ----------------------------------------------------------------------------
 */

/**
 * 플레이어 상세 정보
 */
export interface PlayerInfo {
    playerId: string
    nickname: string
    isHost: boolean
    connected: boolean
    isReady: boolean
    score: number
    position: number
    avatar?: string        // 프론트엔드 UI용 이모지 등
    hasBuff?: string | null
    avatarId?: number     // 백엔드 아바타 ID
    host?: boolean        // 백엔드 호환성용
    ready?: boolean       // 백엔드 호환성용
}

// 하위 호환을 위한 Alias
export type Player = PlayerInfo

/**
 * 퀴즈 기본 구조
 */
export interface Quiz {
    quizId: number
    question: string
    options: string[]
    timeLimit?: number
    answer?: number
    answerText?: string
    answerIndex?: number
}

/**
 * 실시간 게임 상태 응답
 */
export interface GameStateResponse {
    state: RoomStatus
    round?: number | null
    currentTurnPlayerId?: string | null
    players: PlayerInfo[]
    turnOrder?: string[]
    cardDrawResult?: Record<string, number>
    mapEvents?: Record<string, string> | null
    status?: RoomStatus   // 백엔드 호환성용
}

/**
 * 실시간 퀴즈 메시지
 */
export interface QuizMessage {
    quizId: number
    question: string
    options?: string[]
    timeLimit: number
    quizStartTime?: string
    quizStartTimeMs?: number
    type?: 'QUIZ_GENERATING' | 'QUIZ_GENERATED' | 'QUIZ_RESET' // 알림용
    count?: number          // 알림용
}

export interface QuizOptionsMessage {
    quizId: number
    options: string[]
    extraTimeMs?: number
}

/**
 * 라운드 채점 결과
 */
export interface RoundResult {
    quizId: number
    correctAnswer: string
    isTurnUserCorrect: boolean
    winnerPlayerId?: string | null
    rouletteType?: 'BUFF' | 'MOVE' | 'NONE' | string
    rouletteValue?: number
}

/**
 * 캐릭터 이동 상세 액션
 */
export interface MoveAction {
    type: string
    value?: number
    buffType?: string
    effectType?: string
    before?: number
    after?: number
    targetPlayerId?: string
}

/**
 * 전체 이동 정보
 */
export interface MoveInfo {
    playerId: string
    startPosition: number
    endPosition: number
    moveProcess: MoveAction[]
}

/**
 * 최종 순위 정보
 */
export interface FinishRank {
    playerId: string
    nickname: string
    position: number
    remaining: number
    score: number
    rank: number
}

/**
 * 게임 종료 응답
 */
export interface FinishResponse {
    type: 'FINISH'
    winnerPlayerId: string
    rankings: FinishRank[]
}

/**
 * 내 정보 조회 응답 (Member)
 */
export interface MemberMeResponse {
    id: number
    playerId: string
    loginId: string
    nickname: string
    isGuest: boolean
}

/**
 * 내 정보 조회 응답 (Guest)
 */
export interface GuestMeResponse {
    playerId: string
    nickname: string
    isGuest: boolean
}
