import type { User } from '@/stores/roomtStore'

export const mockUsers: User[] = [
    { id: '1', username: '티라노', avatar: '🐱', isReady: false, isHost: true, isMe: true },
    { id: '2', username: '창민짱짱돌이', avatar: '🐶', isReady: false, isHost: false, isMe: false },
    { id: '3', username: '머라카노', avatar: '🦊', isReady: false, isHost: false, isMe: false },
    { id: '4', username: '자바왕', avatar: '🐰', isReady: false, isHost: false, isMe: false }
]