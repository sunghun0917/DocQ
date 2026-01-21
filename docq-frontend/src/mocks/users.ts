import type { User } from '@/stores/roomStore'

export const mockUsers: User[] = [
    { id: '1', nickname: '티라노', avatar: '🐱', isReady: false, isHost: true, isMe: true },
    { id: '2', nickname: '창민짱짱돌이', avatar: '🐶', isReady: false, isHost: false, isMe: false },
    { id: '3', nickname: '머라카노', avatar: '🦊', isReady: false, isHost: false, isMe: false },
    { id: '4', nickname: '자바왕', avatar: '🐰', isReady: false, isHost: false, isMe: false }
]
