// 현재 사용자 정보 관리
/*
1. 사용자 인증 상태
2. 사용자 프로필 정보
3. 로그인/로그아웃
4. 닉네임 및 아바타 설정
*/

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { guestLogin, updateGuestNickname } from '@/services/api'

// 타입 정의
export interface UserProfile {
    /** 로그인(회원) 시 RDS 사용자 ID. 로그인 유저만 값 있음 */
    userId?: string
    /** 게임 참여자 ID. 방/게임/웹소켓 API용 (게스트·로그인 공통) */
    playerId: string
    username: string
    avatar: string
    email?: string
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

    // Actions

    /**
     * 사용자 로그인
     * @param username - 사용자 이름
     * @param password - 비밀번호 (실제 구현 시 사용)
     */
    const login = async (username: string, password?: string) => {
        isLoading.value = true
        try {
            // TODO: 실제 API 호출로 대체
            // const response = await api.login({ username, password })

            // TODO: 실제 회원 로그인 API 연동 시 userId·playerId 구분
            // 임시로도 playerId는 백엔드에서 발급받아야 함 → 게스트 로그인 API 호출
            const response = await guestLogin(username)
            currentUser.value = {
                playerId: response.playerId,
                username: response.nickname,
                avatar: getRandomAvatar(),
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
    const loginAsGuest = async (username: string) => {
        return loginWithApi(username)
    }

    /**
     * 사용자 로그아웃
     */
    const logout = () => {
        currentUser.value = null
        isAuthenticated.value = false

        // 스토리지 정리
        sessionStorage.removeItem('user')
        sessionStorage.removeItem('guestUser')
    }

    /**
     * 닉네임 변경
     * @param newUsername - 새 닉네임
     */
    const updateUsername = (newUsername: string) => {
        if (currentUser.value) {
            currentUser.value.username = newUsername

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
     * 아바타 변경
     * @param newAvatar - 새 아바타 (이모지 또는 URL)
     */
    const updateAvatar = (newAvatar: string) => {
        if (currentUser.value) {
            currentUser.value.avatar = newAvatar

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
            email: parsed.email,
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
        const avatars = ['❓']
        return avatars[Math.floor(Math.random() * avatars.length)] || '👤'
    }

    /**
     * 사용자 초기화 (개발/테스트용)
     */
    const reset = () => {
        currentUser.value = null
        isAuthenticated.value = false
        isLoading.value = false
        sessionStorage.removeItem('user')
        sessionStorage.removeItem('guestUser')
    }

    return {
        // State
        currentUser,
        isAuthenticated,
        isLoading,

        // Getters
        userId,
        playerId,
        username,
        avatar,

        // Actions
        login,
        loginWithApi,
        loginAsGuest,
        logout,
        updateUsername,
        updateAvatar,
        updateProfile,
        restoreUser,
        getRandomAvatar,
        reset
    }
})
