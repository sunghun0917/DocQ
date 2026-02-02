// REST API 통신 서비스
// AxiosInstance 타입을 명시적으로 사용
import axios, { type AxiosInstance } from 'axios'
import type {
    GuestLoginRequest,
    GuestLoginResponse,
    GuestNicknameUpdateRequest,
    GuestNicknameUpdateResponse,
    CreateRoomRequest,
    CreateRoomResponse,
    RoomResponse,
    JoinRoomRequest,
    JoinRoomResponse,
    QuizResponse,
    QuizGenerateResponse,
    CheckNicknameResponse,
    CheckIdResponse,
    SignUpRequest,
    SignUpResponse,
    LoginRequest,
    LoginResponse
} from '@/types/docq-api'

// Axios 인스턴스 생성
const apiClient: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 120000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// 응답 인터셉터 (에러 로깅용)
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        console.error('API Error:', error.response?.data || error.message)
        return Promise.reject(error)
    }
)

/**
 * ============================================================================
 * 📡 API 함수 목록
 * ============================================================================
 */

/**
 * 회원 가입
 * POST /auth/signup
 */
export const signUp = async (data: SignUpRequest): Promise<SignUpResponse> => {
    const response = await apiClient.post<SignUpResponse>('/auth/signup', data)
    return response.data
}

/**
 * 회원 로그인
 * POST /auth/login
 */
export const login = async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post<LoginResponse>('/auth/login', data)
    return response.data
}

/**
 * 게스트 로그인 (닉네임으로 playerId 발급)
 * POST /guests/login
 */
export const guestLogin = async (nickname: string): Promise<GuestLoginResponse> => {
    const response = await apiClient.post<GuestLoginResponse>('/guests/login', { nickname } as GuestLoginRequest)
    return response.data
}

/**
 * 게스트 닉네임 변경
 * PUT /guests/{playerId}/nickname
 */
export const updateGuestNickname = async (playerId: string, nickname: string): Promise<GuestNicknameUpdateResponse> => {
    const response = await apiClient.put<GuestNicknameUpdateResponse>(`/guests/${playerId}/nickname`, {
        nickname
    } as GuestNicknameUpdateRequest)
    return response.data
}

/**
 * 방 생성
 * POST /rooms
 */
export const createRoom = async (data: CreateRoomRequest): Promise<CreateRoomResponse> => {
    const response = await apiClient.post<CreateRoomResponse>('/rooms', data)
    return response.data
}

/**
 * 방 ID로 조회
 * GET /rooms/{roomId}
 */
export const getRoomById = async (roomId: string): Promise<RoomResponse> => {
    const response = await apiClient.get<RoomResponse>(`/rooms/${roomId}`)
    return response.data
}

/**
 * 입장 코드로 방 조회
 * GET /rooms?inviteCode={inviteCode}
 */
export const getRoomByInviteCode = async (inviteCode: string): Promise<RoomResponse> => {
    const response = await apiClient.get<RoomResponse>('/rooms', {
        params: { inviteCode }
    })
    return response.data
}

/**
 * 방 참가 검증 (비밀번호 확인)
 * POST /rooms/join
 */
export const verifyJoinRoom = async (data: JoinRoomRequest): Promise<JoinRoomResponse> => {
    const response = await apiClient.post<JoinRoomResponse>('/rooms/join', data)
    return response.data
}

/**
 * PDF 업로드 및 퀴즈 생성
 * POST /api/game/{roomId}/quiz/generate
 */
export const generateQuiz = async (roomId: string, file: File): Promise<QuizGenerateResponse> => {
    const formData = new FormData()
    formData.append('file', file)

    const response = await apiClient.post(`/game/${roomId}/quiz/generate`, formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
    return response.data
}

/**
 * 생성된 퀴즈 조회
 * GET /api/game/{roomId}/quiz
 */
export const getQuizzes = async (roomId: string): Promise<QuizResponse[]> => {
    const response = await apiClient.get<QuizResponse[]>(`/game/${roomId}/quiz`)
    return response.data
}

/**
 * 기본 퀴즈 선택
 * POST /api/game/{roomId}/quiz/default?type=nonsense|common|mixed
 */
export const setDefaultQuizzes = async (roomId: string, type: string): Promise<{ count: number }> => {
    const response = await apiClient.post<{ count: number }>(`/game/${roomId}/quiz/default`, null, {
        params: { type }
    })
    return response.data
}

/**
 * 닉네임 중복 확인
 * GET /users/nickname/exists?nickname={nickname}
 */
export const checkNicknameAvailability = async (nickname: string): Promise<CheckNicknameResponse> => {
    const response = await apiClient.get<CheckNicknameResponse>('/users/nickname/exists', {
        params: { nickname }
    })
    return response.data
}

/**
 * 아이디 중복 확인
 * GET /users/userId/exists?userId={userId}
 */
export const checkUserIdAvailability = async (userId: string): Promise<CheckIdResponse> => {
    const response = await apiClient.get<CheckIdResponse>('/users/userId/exists', {
        params: { userId }
    })
    return response.data
}

// ==================== 에러 처리 헬퍼 ====================

/**
 * API 에러 메시지 추출
 */
export const getErrorMessage = (error: any): string => {
    if (typeof error === 'string') {
        return error
    }
    if (error.response?.data?.message) {
        return error.response.data.message
    }
    if (error.message) {
        return error.message
    }
    return '알 수 없는 오류가 발생했습니다.'
}

/**
 * API 에러 코드 추출
 */
export const getErrorCode = (error: any): string | null => {
    return error.response?.data?.code || null
}

// 기본 export
export default {
    guestLogin,
    updateGuestNickname,
    createRoom,
    getRoomById,
    getRoomByInviteCode,
    verifyJoinRoom,
    generateQuiz,
    getQuizzes,
    setDefaultQuizzes,
    checkNicknameAvailability,
    checkUserIdAvailability,
    signUp,
    login,
    getErrorMessage,
    getErrorCode
}
