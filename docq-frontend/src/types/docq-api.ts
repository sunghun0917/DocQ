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

export interface Player {
    userId: string
    nickname: string
    isHost?: boolean
    isReady?: boolean
    position: number
    avatar?: string
    score?: number
    hasBuff?: string | null
    avatarId?: number
    connected?: boolean
}

export interface Quiz {
    quizId: number
    question: string
    options: string[]
    timeLimit?: number
    answer?: number
    answerText?: string
    answerIndex?: number
}

export interface PlayerInfo {
    userId: string
    nickname: string
    isHost: boolean
    host?: boolean
    connected: boolean
    isReady: boolean
    ready?: boolean
    score?: number
    position?: number
    hasBuff?: string | null
    avatarId?: number
}

export interface GameStateResponse {
    state: RoomStatus
    status?: RoomStatus
    round?: number | null
    currentTurnUserId?: string | null
    players: PlayerInfo[]
    turnOrder?: string[]
    cardDrawResult?: Record<string, number>
    mapEvents?: Record<string, string> | null
}

export interface QuizMessage {
    quizId: number
    question: string
    options: string[]
    timeLimit: number
    quizStartTime?: string
}

export interface RoundResult {
    quizId: number
    correctAnswer: string
    isTurnUserCorrect: boolean
    winnerUserId?: string | null
    rouletteType?: 'BUFF' | 'MOVE' | 'NONE' | string
}

export interface MoveAction {
    type: string
    value?: number
    buffType?: string
    effectType?: string
    before?: number
    after?: number
    targetUserId?: string
}

export interface MoveInfo {
    userId: string
    startPosition: number
    endPosition: number
    moveProcess: MoveAction[]
}

export interface FinishRank {
    userId: string
    nickname: string
    position: number
    remaining: number
    score: number
    rank: number
}

export interface FinishResponse {
    type: 'FINISH'
    winnerUserId: string
    rankings: FinishRank[]
}
