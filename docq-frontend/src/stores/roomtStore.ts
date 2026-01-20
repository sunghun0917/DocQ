// 방 정보 및 참가자 관리
/*
1. 방 정보 저장 (방 코드, 방 제목, 최대 인원)
2. 참가자 목록 관리
3. 방장 여부 확인
4. 방생성/참가/나가기
*/

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { mockUsers } from '@/mocks/users'

// 타입 정의
export interface User {
    id: string
    username: string
    avatar?: string
    isReady: boolean
    isHost: boolean
    isMe: boolean
}

export interface RoomInfo {
    roomId: string
    roomName: string
    maxUsers: number
    isSecret: boolean
}

export const useRoomStore = defineStore('room', () => {
    // State
    const roomInfo = ref<RoomInfo>({
        roomId: '',
        roomName: '',
        maxUsers: 8,
        isSecret: false
    })

    const users = ref<User[]>(mockUsers)
    const isHost = ref(false)

    // Getters (계산된 값)
    const currentUserCount = computed(() => users.value.length)
    const isAllReady = computed(() => users.value.every(user => user.isReady || user.isHost))
    const myUser = computed(() => users.value.find(user => user.isMe))

    // Actions
    const createRoom = (roomData: Partial<RoomInfo>) => {
        roomInfo.value = {
            ...roomInfo.value,
            ...roomData
        }
        isHost.value = true
    }

    const joinRoom = (roomCode: string) => {
        // TODO: Implement room joining logic
        roomInfo.value.roomId = roomCode
        isHost.value = false
    }

    const leaveRoom = () => {
        roomInfo.value = {
            roomId: '',
            roomName: '',
            maxUsers: 8,
            isSecret: false
        }
        users.value = []
        isHost.value = false
    }

    const updateUser = (userId: string, data: Partial<User>) => {
        const userIndex = users.value.findIndex(u => u.id === userId)
        if (userIndex !== -1) {
            users.value[userIndex] = {
                ...users.value[userIndex],
                ...data
            } as User
        }
    }

    const addUser = (user: User) => {
        users.value.push(user)
    }

    const removeUser = (userId: string) => {
        users.value = users.value.filter(u => u.id !== userId)
    }

    return {
        // State
        roomInfo,
        users,
        isHost,

        // Getters
        currentUserCount,
        isAllReady,
        myUser,

        // Actions
        createRoom,
        joinRoom,
        leaveRoom,
        updateUser,
        addUser,
        removeUser
    }
})