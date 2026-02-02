// 현재 사용자 정보 관리
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
    guestLogin,
    updateGuestNickname,
    checkNicknameAvailability,
    checkUserIdAvailability as checkIdApi,
    signUp as signUpApi,
    login as loginApi,
    logout as logoutApi,
    getMyInfo
} from '@/services/api'

// 통합 사용자 프로필 타입
export interface UserProfile {
    /** 로그인(회원) 시 RDS 사용자 ID. 로그인 유저만 값 있음 */
    userId?: string
    /** 게임 참여자 ID. 방/게임/웹소켓 API용 (게스트·로그인 공통) */
    playerId: string
    username: string
    avatar: string
    isGuest: boolean
    loginId?: string
    createdAt?: Date
}

export const useUserStore = defineStore('user', () => {
    // State
    const currentUser = ref<UserProfile | null>(null)
    const isAuthenticated = ref(false)
    const isLoading = ref(false)

    // Getters
    /** 로그인(회원) 시 RDS 사용자 ID. 로그인 유저만 값 있음, 게스트는 빈 문자열 */
    const userId = computed(() => currentUser.value?.userId ?? '')
    /** 게임 참여자 ID. 방/게임/웹소켓 API에는 이 값 사용 */
    const playerId = computed(() => currentUser.value?.playerId ?? '')
    const username = computed(() => currentUser.value?.username || 'Guest')
    const avatar = computed(() => currentUser.value?.avatar || '👤')
    const isGuest = computed(() => currentUser.value?.isGuest || false)

    // Actions

    /**
     * API 기반 게스트 로그인 (닉네임으로 playerId 발급)
     */
    const loginWithApi = async (nickname: string) => {
        isLoading.value = true
        try {
            const response = await guestLogin(nickname)
            currentUser.value = {
                playerId: response.playerId,
                username: response.nickname,
                avatar: getRandomAvatar(),
                isGuest: true,
                createdAt: new Date()
            }

            isAuthenticated.value = true
            sessionStorage.setItem('user', JSON.stringify(currentUser.value))

            return { success: true }
        } catch (error) {
            console.error('Login failed:', error)
            return { success: false, error }
        } finally {
            isLoading.value = false
        }
    }

    /**
     * 게스트로 입장 (닉네임으로 백엔드에서 playerId 발급받음)
     * playerId는 반드시 백엔드에서 발급받아야 방/게임 API가 동작함
     */
    const loginAsGuest = async (nickname: string) => {
        return loginWithApi(nickname)
    }

    /**
     * 2. 회원 로그인 (Mock)
     */
    const login = async (loginId: string, password?: string) => {
        if (!password) return { success: false, error: 'Password is required' }
        isLoading.value = true
        try {
            await loginApi({ loginId, password })
            // 로그인 성공 시 쿠키가 발급됨. restoreUser를 통해 playerId 포함 전체 정보를 가져옴
            const success = await restoreUser()
            return { success }
        } catch (error) {
            console.error('Login failed:', error)
            return { success: false, error }
        } finally {
            isLoading.value = false
        }
    }

    /**
     * 3. 회원가입 (Mock)
     */
    const signUp = async (loginId: string, password: string, nickname: string) => {
        isLoading.value = true
        try {
            await signUpApi({ loginId, password, nickname })
            // 회원가입 전용 세션이 생성되므로 정보 복구
            const success = await restoreUser()
            return { success }
        } catch (error) {
            console.error('Signup failed:', error)
            return { success: false, error }
        } finally {
            isLoading.value = false
        }
    }

    /**
     * 4. 로그아웃 (백엔드 세션 무효화 + 프론트 상태 초기화)
     */
    const logout = async () => {
        try {
            await logoutApi()  // 백엔드 세션 무효화
        } catch (error) {
            console.error('Logout API failed:', error)
        } finally {
            // 프론트엔드 상태 초기화 (API 실패해도 무조건 초기화)
            currentUser.value = null
            isAuthenticated.value = false
            sessionStorage.removeItem('user')
            sessionStorage.removeItem('guestUser')
        }
    }

    /**
     * 5. 닉네임 중복 확인 (Mock)
     */
    const checkNickname = async (nickname: string) => {
        try {
            const { exists } = await checkNicknameAvailability(nickname)
            return {
                isAvailable: !exists,
                message: !exists ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다."
            }
        } catch (error) {
            console.error('Nickname check failed:', error)
            return {
                isAvailable: false,
                message: "닉네임 확인 중 오류가 발생했습니다."
            }
        }
    }

    /**
     * 아이디 중복 확인
     */
    /**
     * 세션 복구 및 내 정보 조회 (새로고침 시)
     */
    const restoreUser = async () => {
        try {
            isLoading.value = true
            const response = await getMyInfo()

            currentUser.value = {
                userId: 'id' in response ? String(response.id) : undefined,
                playerId: response.playerId,
                username: response.nickname,
                avatar: getRandomAvatar(),
                isGuest: response.isGuest,
                loginId: 'loginId' in response ? response.loginId : undefined
            }
            isAuthenticated.value = true
            sessionStorage.setItem('user', JSON.stringify(currentUser.value))
            return true
        } catch (error) {
            console.error('Session restoration failed:', error)
            currentUser.value = null
            isAuthenticated.value = false
            sessionStorage.removeItem('user')
            return false
        } finally {
            isLoading.value = false
        }
    }

    const checkId = async (userId: string) => {
        try {
            const { available, message } = await checkIdApi(userId)
            return {
                isAvailable: available,
                message: message
            }
        } catch (error) {
            console.error('ID check failed:', error)
            return {
                isAvailable: false,
                message: "아이디 확인 중 오류가 발생했습니다."
            }
        }
    }

    /**
     * 6. 닉네임 변경 (Mock)
     */
    const updateUsername = async (newNickname: string) => {
        if (!currentUser.value) return false

        currentUser.value.username = newNickname
        sessionStorage.setItem('user', JSON.stringify(currentUser.value))
        return true
    }



    const updateAvatar = (newAvatar: string) => {
        if (currentUser.value) {
            currentUser.value.avatar = newAvatar
            sessionStorage.setItem('user', JSON.stringify(currentUser.value))
        }
    }

    /**
     * 프로필 전체 업데이트
     * @param updates - 업데이트할 프로필 정보
     */
    const updateProfile = (updates: Partial<UserProfile>) => {
        if (currentUser.value) {
            currentUser.value = {
                ...currentUser.value,
                ...updates
            }

            // 스토리지 업데이트
            if (sessionStorage.getItem('user')) {
                sessionStorage.setItem('user', JSON.stringify(currentUser.value))
            }
            if (sessionStorage.getItem('guestUser')) {
                sessionStorage.setItem('guestUser', JSON.stringify(currentUser.value))
            }
        }
    }



    /**
     * 랜덤 아바타 생성 (이모지)
     */
    const getRandomAvatar = (): string => {
        const avatars = ['😊', '😎', '🐱', '🐶', '🦊', '🐸', '🐼', '🐵', '🐣', '🦄']
        return avatars[Math.floor(Math.random() * avatars.length)] || '👤'
    }

    return {
        currentUser,
        isAuthenticated,
        isLoading,
        userId,
        playerId,
        username,
        avatar,
        isGuest,
        login,
        loginAsGuest,
        signUp,
        logout,
        checkNickname,
        checkId,
        updateUsername,
        updateAvatar,
        restoreUser,
        getRandomAvatar
    }
})
