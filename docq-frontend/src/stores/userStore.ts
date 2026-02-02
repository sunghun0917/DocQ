// 현재 사용자 정보 관리
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// 통합 사용자 프로필 타입
export interface UserProfile {
    id: string | number
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
    const userId = computed(() => currentUser.value?.id || '')
    const username = computed(() => currentUser.value?.username || 'Guest')
    const avatar = computed(() => currentUser.value?.avatar || '👤')
    const isGuest = computed(() => currentUser.value?.isGuest || false)

    // Actions

    /**
     * 1. 게스트 로그인 (Mock)
     */
    const loginAsGuest = async (nickname: string) => {
        isLoading.value = true
        // Mock delay
        await new Promise(resolve => setTimeout(resolve, 500))

        currentUser.value = {
            id: Math.random().toString(36).substring(7), // Random ID
            username: nickname,
            avatar: getRandomAvatar(),
            isGuest: true,
            createdAt: new Date()
        }
        isAuthenticated.value = true
        sessionStorage.setItem('user', JSON.stringify(currentUser.value))
        isLoading.value = false
        return { success: true }
    }

    /**
     * 2. 회원 로그인 (Mock)
     */
    const login = async (loginId: string, password?: string) => {
        isLoading.value = true
        await new Promise(resolve => setTimeout(resolve, 500))

        currentUser.value = {
            id: 12345, // Dummy Number ID
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

    /**
     * 7. 세션 복구 (Local Only)
     */
    const restoreUser = async () => {
        const stored = sessionStorage.getItem('user')
        if (stored) {
            currentUser.value = JSON.parse(stored)
            isAuthenticated.value = true
        }
    }

    const updateAvatar = (newAvatar: string) => {
        if (currentUser.value) {
            currentUser.value.avatar = newAvatar
            sessionStorage.setItem('user', JSON.stringify(currentUser.value))
        }
    }

    const getRandomAvatar = (): string => {
        const avatars = ['😊', '😎', '🐱', '🐶', '🦊', '🐸', '🐼', '🐵', '🐣', '🦄']
        return avatars[Math.floor(Math.random() * avatars.length)] || '👤'
    }

    return {
        currentUser,
        isAuthenticated,
        isLoading,
        userId,
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
