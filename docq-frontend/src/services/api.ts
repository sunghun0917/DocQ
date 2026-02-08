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
    LoginResponse,
    MemberMeResponse,
    GuestMeResponse
} from '@/types/docq-api'

// ==================== Community / Boards Types ====================

export type BoardAuthor = {
    userId: number
    nickname: string
}

export type BoardListItem = {
    boardId: number
    title: string
    content: string
    author: BoardAuthor
    quizId: number | null
    questionCount: number
    likeCount: number
    commentCount: number
    storedCount: number
    likedByMe: boolean
    storedByMe: boolean
    createdAt: string
}

export type BoardListResponse = {
    page: number
    size: number
    total: number
    items: BoardListItem[]
}

export type BoardDetailQuiz = {
    quizId: number
    quizCount: number
    quizList: string[] // question만
    quizTitle: string
}

export type BoardDetailResponse = {
    boardId: number
    title: string
    content: string
    author: BoardAuthor
    quiz: BoardDetailQuiz | null
    likeCount: number
    commentCount: number
    likedByMe: boolean
    storedByMe: boolean
    createdAt: string
}

export type CreateBoardRequest = {
    quizId: number
    title: string
    content: string
}

export type CreateBoardResponse = {
    boardId: number
}

export type LikeResponse = {
    boardId: number
    liked: boolean
    likeCount: number
}

export type StoreQuizResponse = {
    quizId: number
    stored: boolean
}

export type MyQuizItem = {
    quizId: number
    title: string
    questionCount: number
}

export type MyQuizListResponse = {
    quizzes: MyQuizItem[]
}

// ==================== Comments Types ====================

export type CommentAuthor = {
    userId: number
    nickname: string
}

export type CommentItem = {
    commentId: number
    author: CommentAuthor
    content: string
    createdAt: string
}

export type CommentListResponse = {
    page: number
    size: number
    total: number
    items: CommentItem[]
}

export type CreateCommentRequest = {
    content: string
}

export type CreateCommentResponse = {
    commentId: number
}

// Axios 인스턴스 생성
const apiClient: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 120000,
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json'
    }
})

// 응답 인터셉터 (에러 로깅용). /users/me 403은 미인증 시 정상이므로 로그 생략
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        const isMe403 = error.response?.status === 403 && String(error.config?.url || '').includes('/users/me')
        if (!isMe403) {
            if (error.response) {
                console.error('API Error Data:', error.response.data)
                console.error('API Error Status:', error.response.status)
            } else {
                console.error('API Error Message:', error.message)
            }
        }
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
 * 로그아웃 (세션 무효화)
 * POST /auth/logout
 */
export const logout = async (): Promise<void> => {
    await apiClient.post('/auth/logout')
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
 * 회원 닉네임 변경
 * PATCH /users/me/nickname
 */
export const updateMemberNickname = async (nickname: string): Promise<{ nickname: string }> => {
    const response = await apiClient.patch<{ nickname: string }>('/users/me/nickname', { nickname })
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
        headers: { 'Content-Type': 'multipart/form-data' }
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
 * DB에 저장된 퀴즈 선택
 * POST /game/{roomId}/quiz/{quizId}
 */
export const setQuizFromDb = async (roomId: string, quizId: number): Promise<{ count: number }> => {
    const response = await apiClient.post<{ count: number }>(`/game/${roomId}/quiz/${quizId}`)
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

/**
 * 내 정보 조회 (새로고침 시 세션 복구용)
 * GET /users/me
 */
export const getMyInfo = async (): Promise<MemberMeResponse | GuestMeResponse> => {
    const response = await apiClient.get<MemberMeResponse | GuestMeResponse>('/users/me')
    return response.data
}

// ==================== Community / Boards API ====================
// 인증: 쿠키/세션 기반. apiClient에 withCredentials 적용됨.

/**
 * 게시판 목록 조회
 * GET /boards?page={page}&size={size}
 */
export const getBoards = async (page = 1, size = 8): Promise<BoardListResponse> => {
    const response = await apiClient.get<BoardListResponse>('/boards', { params: { page, size } })
    return response.data
}

/**
 * 게시글 상세 조회
 * GET /boards/{boardId}
 */
export const getBoardDetail = async (boardId: number): Promise<BoardDetailResponse> => {
    const response = await apiClient.get<BoardDetailResponse>(`/boards/${boardId}`)
    return response.data
}

/**
 * 게시글 작성 (회원 전용, 세션 인증)
 * POST /boards
 */
export const createBoard = async (data: CreateBoardRequest): Promise<CreateBoardResponse> => {
    const response = await apiClient.post<CreateBoardResponse>('/boards', data)
    return response.data
}

/**
 * 좋아요
 * POST /boards/{boardId}/likes
 */
export const likeBoard = async (boardId: number): Promise<LikeResponse> => {
    const response = await apiClient.post<LikeResponse>(`/boards/${boardId}/likes`, null)
    return response.data
}

/**
 * 좋아요 취소
 * DELETE /boards/{boardId}/likes
 */
export const unlikeBoard = async (boardId: number): Promise<LikeResponse> => {
    const response = await apiClient.delete<LikeResponse>(`/boards/${boardId}/likes`)
    return response.data
}

/**
 * 퀴즈 저장
 * POST /quizzes/{quizId}/store
 */
export const storeQuiz = async (quizId: number): Promise<StoreQuizResponse> => {
    const response = await apiClient.post<StoreQuizResponse>(`/quizzes/${quizId}/store`, null)
    return response.data
}

/**
 * 퀴즈 저장 취소 (언저장)
 * DELETE /quizzes/{quizId}/store
 */
export const unstoreQuiz = async (quizId: number): Promise<StoreQuizResponse> => {
    const response = await apiClient.delete<StoreQuizResponse>(`/quizzes/${quizId}/store`)
    return response.data
}

/**
 * 내가 만든 퀴즈 목록 조회
 * GET /quizzes/my
 */
export const getMyQuizzes = async (): Promise<MyQuizListResponse> => {
    const response = await apiClient.get<MyQuizListResponse>('/quizzes/my')
    return response.data
}

/**
 * 내가 저장한 퀴즈 목록 조회
 * GET /quizzes/stored-quizz
 */
export const getStoredQuizzes = async (): Promise<MyQuizListResponse> => {
    const response = await apiClient.get<MyQuizListResponse>('/quizzes/stored-quizz')
    return response.data
}

// ==================== Comments API ====================

/**
 * 댓글 목록 조회
 * GET /boards/{boardId}/comments?page={page}&size={size}
 */
export const getComments = async (boardId: number, page = 1, size = 20): Promise<CommentListResponse> => {
    const response = await apiClient.get<CommentListResponse>(`/boards/${boardId}/comments`, {
        params: { page, size }
    })
    return response.data
}

/**
 * 댓글 작성
 * POST /boards/{boardId}/comments
 */
export const createComment = async (boardId: number, data: CreateCommentRequest): Promise<CreateCommentResponse> => {
    const response = await apiClient.post<CreateCommentResponse>(`/boards/${boardId}/comments`, data)
    return response.data
}

/**
 * 댓글 삭제
 * DELETE /boards/{boardId}/comments/{commentId}
 */
export const deleteComment = async (boardId: number, commentId: number): Promise<void> => {
    await apiClient.delete(`/boards/${boardId}/comments/${commentId}`)
}

// ==================== 에러 처리 헬퍼 ====================

/**
 * API 에러 메시지 추출
 */
export const getErrorMessage = (error: any): string => {
    if (error == null) return '알 수 없는 오류가 발생했습니다.'
    if (typeof error === 'string') return error
    if (error.response?.data?.message) return error.response.data.message
    if (error.message) return error.message
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
    updateMemberNickname,
    createRoom,
    getRoomById,
    getRoomByInviteCode,
    verifyJoinRoom,
    generateQuiz,
    getQuizzes,
    setDefaultQuizzes,
    setQuizFromDb,
    checkNicknameAvailability,
    checkUserIdAvailability,
    signUp,
    login,
    logout,
    getMyInfo,
    getBoards,
    getBoardDetail,
    createBoard,
    likeBoard,
    unlikeBoard,
    storeQuiz,
    unstoreQuiz,
    getMyQuizzes,
    getStoredQuizzes,
    getComments,
    createComment,
    deleteComment,
    getErrorMessage,
    getErrorCode
}
