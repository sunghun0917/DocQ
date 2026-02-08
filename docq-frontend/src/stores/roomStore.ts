// 방 정보 및 참가자 관리
/*
1. 방 정보 저장 (방 코드, 방 제목, 최대 인원)
2. 참가자 목록 관리
3. 방장 여부 확인
4. 방생성/참가/나가기
*/

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// 타입 정의
export interface User {
    playerId: string
    nickname: string
    avatar?: string
    isReady: boolean
    isHost: boolean
    isMe: boolean
}

export interface RoomInfo {
    roomId: string
    title: string
    capacity: number
    isPrivate: boolean
    status: string
    inviteCode: string
    hostId: string
    currentCount: number
    quizCount: number
}

export const useRoomStore = defineStore('room', () => {
    // State
    const roomInfo = ref<RoomInfo>({
        roomId: '',
        title: '',
        capacity: 0,
        isPrivate: false,
        status: 'WAITING',
        inviteCode: '',
        hostId: '',
        currentCount: 0,
        quizCount: 0
    })

    const users = ref<User[]>([])
    const isHost = ref(false)

    // Getters (계산된 값)
    const currentUserCount = computed(() => users.value.length)
    const isAllReady = computed(() => users.value.length > 1 && users.value.every(user => user.isReady || user.isHost))
    const myUser = computed(() => users.value.find(user => user.isMe))

    // Actions
    const setRoomInfo = (roomData: Partial<RoomInfo>) => {
        roomInfo.value = {
            ...roomInfo.value,
            ...roomData
        }
    }

    const setUsers = (nextUsers: User[]) => {
        users.value = nextUsers
    }

    const createRoom = (roomData: Partial<RoomInfo>) => {
        setRoomInfo(roomData)
        isHost.value = true
    }

    const joinRoom = (roomData: Partial<RoomInfo>) => {
        setRoomInfo(roomData)
        isHost.value = false
    }

    const leaveRoom = () => {
        roomInfo.value = {
            roomId: '',
            title: '',
            capacity: 0,
            isPrivate: false,
            status: 'WAITING',
            inviteCode: '',
            hostId: '',
            currentCount: 0,
            quizCount: 0
        }
        users.value = []
        isHost.value = false
    }

    const updateUser = (playerId: string, data: Partial<User>) => {
        const userIndex = users.value.findIndex(u => u.playerId === playerId)
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

    const removeUser = (playerId: string) => {
        users.value = users.value.filter(u => u.playerId !== playerId)
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
        setRoomInfo,
        setUsers,
        createRoom,
        joinRoom,
        leaveRoom,
        updateUser,
        addUser,
        removeUser
    }
})
