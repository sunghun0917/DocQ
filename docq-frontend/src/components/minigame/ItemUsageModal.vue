<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

interface Player {
    playerId: string
    nickname: string
    avatar?: string
}

const props = defineProps<{
    itemType: string // 'BANANA', 'SWAP_POSITION', 'QUIZ_INTERFERENCE'
    players?: Player[] // 타겟 선택 가능한 플레이어 목록
}>()

const emit = defineEmits<{
    (e: 'use', data: { targetPlayerId?: string; targetPosition?: number }): void
    (e: 'cancel'): void
}>()

const selectedPlayerId = ref<string | null>(null)
const selectedPosition = ref<number | null>(null)
const timeRemaining = ref(10) // 10초 타이머
let timerInterval: number | null = null

const itemInfo = computed(() => {
    switch (props.itemType) {
        case 'BANANA':
            return {
                title: '🍌 바나나 설치',
                description: '바나나를 설치할 타일을 선택하세요.',
                instruction: '상대가 밟으면 뒤로 3칸 이동합니다.'
            }
        case 'SWAP_POSITION':
            return {
                title: '🔄 자리 바꾸기',
                description: '자리를 바꿀 상대를 선택하세요.',
                instruction: '선택한 상대와 위치가 즉시 바뀝니다.'
            }
        case 'QUIZ_INTERFERENCE':
            return {
                title: '🔀 문제 방해하기',
                description: '방해할 상대를 선택하세요.',
                instruction: '다음 문제가 거울 모드로 표시됩니다.'
            }
        default:
            return {
                title: '아이템 사용',
                description: '',
                instruction: ''
            }
    }
})

const canUse = computed(() => {
    if (props.itemType === 'BANANA') {
        return selectedPosition.value !== null
    }
    return selectedPlayerId.value !== null
})

const selectPlayer = (playerId: string) => {
    selectedPlayerId.value = playerId
}

const selectTile = (position: number) => {
    selectedPosition.value = position
}

const handleUse = () => {
    if (!canUse.value) return
    stopTimer()

    emit('use', {
        targetPlayerId: selectedPlayerId.value || undefined,
        targetPosition: selectedPosition.value || undefined
    })
}

const handleCancel = () => {
    stopTimer()
    emit('cancel')
}

// 타이머 자동 사용 (시간 초과 시)
const autoUse = () => {
    // 선택되지 않은 경우 첫 번째 옵션 자동 선택
    if (props.itemType === 'BANANA') {
        if (selectedPosition.value === null) {
            selectedPosition.value = 1 // 첫 번째 타일
        }
    } else {
        if (selectedPlayerId.value === null && props.players && props.players.length > 0) {
            selectedPlayerId.value = props.players[0]?.playerId ?? null
        }
    }
    handleUse()
}

const startTimer = () => {
    timerInterval = window.setInterval(() => {
        timeRemaining.value -= 1
        if (timeRemaining.value <= 0) {
            stopTimer()
            autoUse()
        }
    }, 1000)
}

const stopTimer = () => {
    if (timerInterval) {
        clearInterval(timerInterval)
        timerInterval = null
    }
}

onMounted(() => {
    startTimer()
})

onUnmounted(() => {
    stopTimer()
})

// 바나나용 타일 번호 (1~30)
const tileNumbers = Array.from({ length: 30 }, (_, i) => i + 1)
</script>

<template>
    <div class="item-usage-overlay">
        <div class="item-usage-modal">
            <!-- Header -->
            <div class="modal-header">
                <div class="timer-badge" :class="{ urgent: timeRemaining <= 3 }">
                    {{ timeRemaining }}초
                </div>
                <h3>{{ itemInfo.title }}</h3>
                <p class="description">{{ itemInfo.description }}</p>
                <small class="instruction">{{ itemInfo.instruction }}</small>
            </div>

            <!-- Body -->
            <div class="modal-body">
                <!-- 플레이어 선택 (자리바꾸기, 방해하기) -->
                <div v-if="itemType !== 'BANANA' && players" class="player-selection">
                    <div v-for="player in players" :key="player.playerId" class="player-card"
                        :class="{ selected: selectedPlayerId === player.playerId }"
                        @click="selectPlayer(player.playerId)">
                        <div class="player-avatar">{{ player.avatar || '❓' }}</div>
                        <div class="player-name">{{ player.nickname }}</div>
                    </div>
                </div>

                <!-- 타일 선택 (바나나) -->
                <div v-if="itemType === 'BANANA'" class="tile-selection">
                    <div v-for="tile in tileNumbers" :key="tile" class="tile-button"
                        :class="{ selected: selectedPosition === tile }" @click="selectTile(tile)">
                        {{ tile }}
                    </div>
                </div>
            </div>

            <!-- Footer -->
            <div class="modal-footer">
                <button class="btn-cancel" @click="handleCancel">취소</button>
                <button class="btn-use" :disabled="!canUse" @click="handleUse">사용</button>
            </div>
        </div>
    </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.item-usage-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.75);
    display: flex;
    justify-content: center;
    align-items: center;
    z-index: 3000;
    backdrop-filter: blur(4px);
    font-family: 'Jua', sans-serif;
}

.item-usage-modal {
    background: #fff8e1;
    border: 5px solid #8d6e63;
    border-radius: 24px;
    box-shadow: 0 12px 0 #5d4037, 0 20px 30px rgba(0, 0, 0, 0.3);
    width: 90%;
    max-width: 600px;
    max-height: 80vh;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    animation: modalPop 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes modalPop {
    from {
        opacity: 0;
        transform: scale(0.7) translateY(50px);
    }

    to {
        opacity: 1;
        transform: scale(1) translateY(0);
    }
}

.modal-header {
    background: #d7ccc8;
    padding: 20px;
    border-bottom: 4px solid #bcaaa4;
    text-align: center;
    position: relative;
}

.timer-badge {
    position: absolute;
    top: 10px;
    right: 10px;
    background: #ff6f00;
    color: white;
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 1.1rem;
    font-weight: bold;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
    transition: background 0.3s;
}

.timer-badge.urgent {
    background: #d32f2f;
    animation: timerPulse 0.5s infinite;
}

@keyframes timerPulse {

    0%,
    100% {
        transform: scale(1);
    }

    50% {
        transform: scale(1.1);
    }
}

.modal-header h3 {
    margin: 0 0 8px;
    font-size: 2rem;
    color: #5d4037;
}

.description {
    margin: 4px 0;
    font-size: 1.2rem;
    color: #6d4c41;
}

.instruction {
    color: #8d6e63;
    font-size: 0.9rem;
}

.modal-body {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    background: #fff8e1;
}

/* 플레이어 선택 */
.player-selection {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.player-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
    background: #ffffff;
    border: 3px solid #bcaaa4;
    border-radius: 16px;
    cursor: pointer;
    transition: all 0.2s;
}

.player-card:hover {
    border-color: #8d6e63;
    transform: translateX(4px);
}

.player-card.selected {
    background: #ffecb3;
    border-color: #ff6f00;
    box-shadow: 0 0 0 3px rgba(255, 111, 0, 0.3);
}

.player-avatar {
    font-size: 2.5rem;
    width: 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f5f5f5;
    border-radius: 50%;
}

.player-name {
    font-size: 1.4rem;
    color: #5d4037;
    font-weight: bold;
}

/* 타일 선택 */
.tile-selection {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 8px;
    max-width: 500px;
    margin: 0 auto;
}

.tile-button {
    aspect-ratio: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #ffffff;
    border: 3px solid #bcaaa4;
    border-radius: 12px;
    font-size: 1.2rem;
    font-weight: bold;
    color: #5d4037;
    cursor: pointer;
    transition: all 0.2s;
}

.tile-button:hover {
    border-color: #8d6e63;
    transform: scale(1.05);
}

.tile-button.selected {
    background: #ffecb3;
    border-color: #ff6f00;
    box-shadow: 0 0 0 3px rgba(255, 111, 0, 0.3);
    transform: scale(1.1);
}

/* Footer */
.modal-footer {
    display: flex;
    gap: 12px;
    padding: 16px 20px;
    background: #d7ccc8;
    border-top: 4px solid #bcaaa4;
}

.btn-cancel,
.btn-use {
    flex: 1;
    padding: 14px;
    font-size: 1.3rem;
    font-family: 'Jua', sans-serif;
    border: none;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.2s;
    box-shadow: 0 4px 0 rgba(0, 0, 0, 0.2);
}

.btn-cancel {
    background: #9e9e9e;
    color: white;
}

.btn-cancel:hover {
    background: #757575;
    transform: translateY(-2px);
    box-shadow: 0 6px 0 rgba(0, 0, 0, 0.2);
}

.btn-cancel:active {
    transform: translateY(2px);
    box-shadow: 0 2px 0 rgba(0, 0, 0, 0.2);
}

.btn-use {
    background: #ff6f00;
    color: white;
}

.btn-use:hover:not(:disabled) {
    background: #e65100;
    transform: translateY(-2px);
    box-shadow: 0 6px 0 rgba(0, 0, 0, 0.2);
}

.btn-use:active:not(:disabled) {
    transform: translateY(2px);
    box-shadow: 0 2px 0 rgba(0, 0, 0, 0.2);
}

.btn-use:disabled {
    background: #d7ccc8;
    color: #bcaaa4;
    cursor: not-allowed;
    box-shadow: none;
}
</style>
