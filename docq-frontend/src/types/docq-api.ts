// 데이터 설계도를 모아둔 파일
// 실수로 오타를 내면 컴퓨터가 즉시 에러를 띄워줌.
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
