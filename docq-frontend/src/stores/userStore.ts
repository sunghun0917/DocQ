// 현재 사용자 정보 관리
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { guestLogin, updateGuestNickname } from '@/services/api'

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
        isLoading.value = true
        await new Promise(resolve => setTimeout(resolve, 500))

        currentUser.value = {
            userId: '12345',
            playerId: 'player_12345',
            loginId: loginId,
            username: loginId + "_User",
            avatar: getRandomAvatar(),
            isGuest: false,
            createdAt: new Date()
        }
        isAuthenticated.value = true
        sessionStorage.setItem('user', JSON.stringify(currentUser.value))
        isLoading.value = false
        return { success: true }
    }

    /**
     * 3. 회원가입 (Mock)
     */
    const signUp = async (loginId: string, password: string, nickname: string) => {
        isLoading.value = true
        await new Promise(resolve => setTimeout(resolve, 500))
        isLoading.value = false
        return { success: true }
    }

    /**
     * 4. 로그아웃 (Mock)
     */
    const logout = async () => {
        currentUser.value = null
        isAuthenticated.value = false
        sessionStorage.removeItem('user')
    }

    /**
     * 5. 닉네임 중복 확인 (Mock)
     */
    const checkNickname = async (nickname: string) => {
        await new Promise(resolve => setTimeout(resolve, 300))
        // 항상 성공
        return {
            isAvailable: true,
            message: "사용 가능한 닉네임입니다."
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

    /** 저장된 프로필을 userId/playerId 기준으로 정규화 (예전 id 필드 호환) */
    const normalizeProfile = (parsed: any): UserProfile | null => {
        if (!parsed || typeof parsed !== 'object') return null
        const playerIdVal = parsed.playerId ?? parsed.id
        if (!playerIdVal || typeof playerIdVal !== 'string') return null
        return {
            userId: parsed.userId,
            playerId: playerIdVal,
            username: parsed.username ?? 'Guest',
            avatar: parsed.avatar ?? '👤',
            isGuest: parsed.isGuest ?? true,
            createdAt: parsed.createdAt
        }
    }

    /**
     * 저장된 사용자 정보 복원
     */
    const restoreUser = () => {
        const savedUser = sessionStorage.getItem('user')
        if (savedUser) {
            try {
                const normalized = normalizeProfile(JSON.parse(savedUser))
                if (normalized) {
                    currentUser.value = normalized
                    isAuthenticated.value = true
                    return true
                }
            } catch (error) {
                console.error('Failed to restore user from sessionStorage:', error)
            }
        }

        const guestUser = sessionStorage.getItem('guestUser')
        if (guestUser) {
            try {
                const normalized = normalizeProfile(JSON.parse(guestUser))
                if (normalized) {
                    currentUser.value = normalized
                    isAuthenticated.value = true
                    return true
                }
            } catch (error) {
                console.error('Failed to restore guest user from sessionStorage:', error)
            }
        }

        return false
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
        updateUsername,
        updateAvatar,
        restoreUser,
        getRandomAvatar
    }
})
