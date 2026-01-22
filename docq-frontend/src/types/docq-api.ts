export interface Player {
    userId: string;
    nickname: string;
    isHost?: boolean;
    isReady?: boolean;
    position: number;
    avatar?: string;
    score?: number;
}

export interface Quiz {
    quizId: number;
    question: string;
    options: string[];
    timeLimit?: number;
    answer?: number;
}
