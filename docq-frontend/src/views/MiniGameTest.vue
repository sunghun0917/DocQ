<script setup lang="ts">
import { ref } from 'vue';
import MiniGameModal from '@/components/minigame/MiniGameModal.vue';

// 테스트용 더미 플레이어
const players = ref([
    { playerId: 'p1', nickname: '나', avatar: '🐥', isMe: true },
    { playerId: 'p2', nickname: '철수', avatar: '🦁', isMe: false },
    { playerId: 'p3', nickname: '영희', avatar: '🐱', isMe: false },
]);

const currentGame = ref<'mash' | 'sequence' | 'stopwatch' | null>(null);

// 게임 종료 시 (가상의 결과 데이터)
const testResult = ref({
    rankings: [
        { playerId: 'p1', nickname: '나', value: 99, rank: 1 },
        { playerId: 'p2', nickname: '철수', value: 50, rank: 2 },
        { playerId: 'p3', nickname: '영희', value: 10, rank: 3 }
    ],
    winnerPlayerId: 'p1'
});

const openGame = (type: 'mash' | 'sequence' | 'stopwatch') => {
    currentGame.value = type;
};

const handleCreate = () => {
    console.log("Submit!");
    // 결과 화면 테스트를 위해 2초 후 닫거나 결과 보여주기 로직을 시뮬레이션 할 수 있음
    setTimeout(() => {
        // 결과 모드 테스트 (필요하면)
    }, 2000);
};

const closeGame = () => {
    currentGame.value = null;
};
</script>

<template>
    <div class="test-container">
        <h1 class="text-4xl font-bold mb-8 text-white drop-shadow-md">🎮 미니게임 통합 테스트 랩</h1>

        <div class="grid grid-cols-3 gap-6 w-full max-w-4xl">
            <button @click="openGame('mash')" class="game-btn mash">
                <span class="text-5xl mb-2">🔥</span>
                <span>연타 게임</span>
            </button>

            <button @click="openGame('sequence')" class="game-btn seq">
                <span class="text-5xl mb-2">🕹️</span>
                <span>순서 기억</span>
            </button>

            <button @click="openGame('stopwatch')" class="game-btn stop">
                <span class="text-5xl mb-2">⏱️</span>
                <span>스톱워치</span>
            </button>
        </div>

        <!-- 미니게임 모달 (실제 게임 화면) -->
        <MiniGameModal v-if="currentGame" :gameType="currentGame" :players="players" :timeLimitMs="10000"
            description="테스트 모드입니다. 마음껏 연습하세요!" @submit="handleCreate" @close="closeGame" />

        <!-- 뒤로가기 -->
        <RouterLink to="/" class="mt-12 text-white underline opacity-70 hover:opacity-100">
            메인으로 돌아가기
        </RouterLink>
    </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.test-container {
    width: 100vw;
    height: 100vh;
    background: linear-gradient(135deg, #74b9ff, #a29bfe);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    font-family: 'Jua', sans-serif;
}

.game-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px;
    border-radius: 24px;
    background: white;
    border: 4px solid #bcaaa4;
    box-shadow: 0 10px 0 rgba(0, 0, 0, 0.1);
    transition: all 0.2s;
    font-size: 1.8rem;
    color: #5d4037;
    cursor: pointer;
}

.game-btn:hover {
    transform: translateY(-5px);
    box-shadow: 0 15px 0 rgba(0, 0, 0, 0.1);
}

.game-btn:active {
    transform: translateY(5px);
    box-shadow: 0 0 0 rgba(0, 0, 0, 0.1);
}

.mash:hover {
    border-color: #ff7675;
    color: #d63031;
}

.seq:hover {
    border-color: #74b9ff;
    color: #0984e3;
}

.stop:hover {
    border-color: #55efc4;
    color: #00b894;
}
</style>
