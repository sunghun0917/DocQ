// REST API 통신 서비스
// AxiosInstance 타입을 명시적으로 사용
import axios, { type AxiosInstance } from 'axios'

// Axios 인스턴스 생성
// VITE_API_BASE_URL 사용
/* JSON 헤더 기본 세팅이란,
이 axios 인스턴스를 사용하는 모든 요청이
“나는 JSON으로 말할게”라고 서버에 선언하는 것 */
const apiClient: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// 요청 인터셉터, API 호출 전에 실행(필요시 토큰 추가 등)
// 왜 필요한가
/*
매번 토큰을 수동으로 추가할 필요가 없다.
*/

// config 객체란? 요청에 대한 모든 정보를 담고 있는 객체
apiClient.interceptors.request.use(
    (config) => {
        // 예: Authorization 헤더 추가
        // const token = localStorage.getItem('token')
        // if (token) {
        //     config.headers.Authorization = `Bearer ${token}`
        // }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 응답 인터셉터, API 응답 후에 실행 (에러 처리)
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        console.error('API Error:', error.response?.data || error.message)
        return Promise.reject(error)
    }
)

// ==================== 타입 정의 ====================

// 사용자 로그인 요청 (닉네임으로 userId 발급)
export interface UserLoginRequest {
    nickname: string
}

// 사용자 로그인 응답
export interface UserLoginResponse {
    userId: string
    nickname: string
}

// 방 생성 요청
export interface CreateRoomRequest {
    userId: string
    capacity: number
    title: string
    isPrivate?: boolean
    password?: string
}

// 방 생성 응답
export interface CreateRoomResponse {
    roomId: string
    inviteCode: string
    hostId: string
    isPrivate: boolean
}

// 방 정보 응답
export interface RoomResponse {
    roomId: string
    title: string
    isPrivate: boolean
    status: string
    currentCount: number
    capacity: number
}

// 방 참가 요청
export interface JoinRoomRequest {
    roomId: string
    password?: string
}

// 방 참가 응답
export interface JoinRoomResponse {
    roomId: string
    roomCode: string
    status: string
}

// ==================== API 함수 ====================

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
    getErrorMessage,
    getErrorCode
}
