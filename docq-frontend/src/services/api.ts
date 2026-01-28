// REST API 통신 서비스
// AxiosInstance 타입을 명시적으로 사용
import axios, { type AxiosInstance } from 'axios'
import type {
    UserLoginRequest,
    UserLoginResponse,
    CreateRoomRequest,
    CreateRoomResponse,
    RoomResponse,
    JoinRoomRequest,
    JoinRoomResponse,
    QuizResponse,
    QuizGenerateResponse
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
 * 사용자 로그인 (닉네임으로 userId 발급)
 * POST /users/login
 */
export const userLogin = async (nickname: string): Promise<UserLoginResponse> => {
    const response = await apiClient.post<UserLoginResponse>('/users/login', { nickname })
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

// ==================== 에러 처리 헬퍼 ====================

/**
 * API 에러 메시지 추출
 */
export const getErrorMessage = (error: any): string => {
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
    userLogin,
    createRoom,
    getRoomById,
    getRoomByInviteCode,
    verifyJoinRoom,
    generateQuiz,
    getQuizzes,
    setDefaultQuizzes,
    getErrorMessage,
    getErrorCode
}
