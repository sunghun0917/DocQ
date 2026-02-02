// 현재 사용자 정보 관리
/*
1. 사용자 인증 상태
2. 사용자 프로필 정보
3. 로그인/로그아웃
4. 닉네임 및 아바타 설정
*/

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userLogin } from '@/services/api'

// 타입 정의
export interface UserProfile {
    id: string
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
    const userId = computed(() => currentUser.value?.id || '')
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

            // 임시 로그인 처리
            currentUser.value = {
                id: Math.random().toString(36).substring(7),
                username: username,
                avatar: getRandomAvatar(),
                createdAt: new Date()
            }

            isAuthenticated.value = true

            // 세션 스토리지에 저장
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
     * API 기반 로그인 (닉네임으로 userId 발급)
     */
    const loginWithApi = async (nickname: string) => {
        isLoading.value = true
        try {
            const response = await userLogin(nickname)
            currentUser.value = {
                id: response.userId,
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
     * 게스트로 입장 (닉네임만 설정)
     * @param username - 사용자 이름
     */
    const loginAsGuest = (username: string) => {
        currentUser.value = {
            id: Math.random().toString(36).substring(7),
            username: username,
            avatar: getRandomAvatar(),
            createdAt: new Date()
        }

        isAuthenticated.value = true

        // 세션 스토리지에 저장 (게스트는 세션만 유지)
        sessionStorage.setItem('guestUser', JSON.stringify(currentUser.value))
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

    /**
     * 저장된 사용자 정보 복원
     */
    const restoreUser = () => {
        // 세션 스토리지에서 복원 시도
        const savedUser = sessionStorage.getItem('user')
        if (savedUser) {
            try {
                currentUser.value = JSON.parse(savedUser)
                isAuthenticated.value = true
                return true
            } catch (error) {
                console.error('Failed to restore user from sessionStorage:', error)
            }
        }

        // 세션 스토리지에서 게스트 복원 시도
        const guestUser = sessionStorage.getItem('guestUser')
        if (guestUser) {
            try {
                currentUser.value = JSON.parse(guestUser)
                isAuthenticated.value = true
                return true
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
