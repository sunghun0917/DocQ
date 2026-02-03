<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useGameStore } from '@/stores/gameStore';
import { useRoomStore } from '@/stores/roomStore';
import { useWebSocketStore } from '@/stores/websocketStore';

const gameStore = useGameStore();
const roomStore = useRoomStore();
const wsStore = useWebSocketStore();

const isSpinning = ref(false);
const rotation = ref(0);
const isVisible = ref(false); // [NEW] 내부 표시 관리
const labelByIndex = ref<number[]>([-1, 1, 2, 3, 4, 5, 6, 7]);
const displayResult = ref<number | null>(null);

// 컴포넌트 마운트 여부 (Store 상태 의존)
const shouldMount = computed(() => !!gameStore.rouletteTargetId);
const targetUserId = computed(() => gameStore.rouletteTargetId);
const rouletteValue = computed(() => gameStore.currentRoundResult?.rouletteValue ?? null);
const targetUser = computed(() => roomStore.users.find(u => u.playerId === targetUserId.value));

// Init labels when target is set (Snapshot to prevent changing during spin)
// 플레이어 턴 승리: 8칸(-1~7, 0제외) / 고스트 턴 승리: 4칸(-1~3, 0제외)
watch(() => gameStore.rouletteTargetId, (targetId) => {
    if (targetId) {
        const currentTurnPlayerId = gameStore.gameState.currentTurnPlayerId;
        const isTurnOwner = targetId === currentTurnPlayerId;
        labelByIndex.value = isTurnOwner ? [-1, 1, 2, 3, 4, 5, 6, 7] : [-1, 1, 2, 3];
    }
}, { immediate: true });

/*
 * 룰렛 원판 스타일 (회전 애니메이션)
 */
const wheelStyle = computed(() => ({
  transform: `rotate(${rotation.value}deg)`,
  transition: isSpinning.value ? 'transform 3.2s cubic-bezier(0.1, 0, 0.1, 1)' : 'none',
}));

/*
 * 각 조각(Section)의 스타일
 */
const sectionStyle = (n: number, total: number) => {
  const sectionAngle = 360 / total;
  const startAngle = -(sectionAngle / 2); // Center alignment
  const angle = startAngle + (n) * sectionAngle;
  return {
    transform: `rotate(${angle}deg) skewY(-${90 - sectionAngle}deg)`
  };
};

/*
 * 룰렛 자동 실행 로직
 * 컴포넌트가 마운트되거나 visible해지면 자동 실행
 */
const RESULT_HOLD_MS = 1000
const FADE_OUT_MS = 300
const ACK_DELAY_MS = 500

const spinRoulette = (targetVal: number) => {
  if (isSpinning.value) return;
  isSpinning.value = true;
  displayResult.value = null;

  const labels = labelByIndex.value;
  // Use the provided target value
  const result = targetVal;
  
  // Find index. If not found, default to 0 (should not happen if logic matches)
  let targetIndex = labels.indexOf(result);
  if (targetIndex === -1) {
      console.warn(`Roulette target ${result} not found in labels`, labels);
      targetIndex = 0;
  }
  
  // 회전 애니메이션 계산
  const sectionAngle = 360 / labels.length;
  const totalSpins = 360 * 5; // 최소 5바퀴
  const currentDegrees = ((rotation.value % 360) + 360) % 360;
  
  // Target rotation: We want the target index to land at the TOP (0 degrees).
  const targetRotation = -(targetIndex * sectionAngle);
  
  // Calculate delta to reach multiple of 360 + target
  const extra = (360 - currentDegrees) % 360;
  
  rotation.value += totalSpins + extra + targetRotation;

  // 회전 종료 후 로직
  // 1. 회전 시간(3.2s) 대기
  // 2. 결과 확인 시간(1s) 대기
  // 3. 팝업 닫기
  // 4. GameBoard에 이동 신호 전송
  setTimeout(() => {
    isSpinning.value = false;
    displayResult.value = rouletteValue.value ?? result;
    
    // 결과 보여주기 위한 짧은 대기
    setTimeout(() => {
        isVisible.value = false; // 팝업 닫기 (이러면 보드판이 보임)
        
        // 팝업이 완전히 사라진 후 이동 신호 전송
        setTimeout(() => {
            setTimeout(() => {
                gameStore.setRouletteResult(result);
                wsStore.sendAck(
                    'ROULETTE_COMPLETE',
                    gameStore.currentRoundResult?.quizId,
                    gameStore.gameState.round ?? undefined
                );
                // [FIX] Clear roulette target so QuizOverlay can show next time
                gameStore.handleRouletteComplete();
            }, ACK_DELAY_MS);
        }, FADE_OUT_MS);
    }, RESULT_HOLD_MS); // 결과 보여줌

  }, 3200);
};

// TargetId가 생기고, 결과 값이 설정되면 스핀 시작
watch(() => [gameStore.rouletteTargetId, rouletteValue.value] as const, ([newId, newVal]) => {
  if (newId) {
    isVisible.value = true; // Show overlay immediately when ID is set
  }
  
  if (newId && newVal != null && !isSpinning.value) {
      // Delay slightly for effect implies waiting for Value
      setTimeout(() => {
        spinRoulette(newVal as number);
      }, 100);
  }
}, { immediate: true });

</script>

<template>
  <div v-if="shouldMount && isVisible" class="roulette-overlay">
    <div class="roulette-modal">
        <div class="modal-header">
            <h3>{{ targetUser?.nickname || 'Unknown' }}님의 결과 확인</h3>
        </div>
        <div class="modal-body">
             <div class="roulette-container">
                <!-- 룰렛 화살표 -->
                <div class="roulette-pointer">▼</div>

                <!-- 룰렛 원판 -->
                <div class="roulette-wheel" :style="wheelStyle">
                <div
                    v-for="(label, i) in labelByIndex"
                    :key="i"
                    class="wheel-section"
                    :style="sectionStyle(i, labelByIndex.length)"
                >
                    <span class="section-label" :style="{ transform: `translateX(-50%) skewY(${90 - (360 / labelByIndex.length)}deg) rotate(${360 / labelByIndex.length / 2}deg)` }">{{ label }}</span>
                </div>
                </div>
            </div>
            
            <div class="info-text">
                <p v-if="displayResult !== null">
                    앞으로 {{ displayResult }} 칸 이동합니다.
                </p>
                <p v-else>결과 확인 중...</p>
            </div>
        </div>
    </div>
  </div>
</template>

<style scoped>
.roulette-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000; /* QuizOverlay(100)보다 위 */
  backdrop-filter: blur(4px);
}

.roulette-modal {
  background: white;
  width: 500px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 10px 25px rgba(0,0,0,0.5);
  text-align: center;
  animation: popIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.modal-header {
  background: #f3f4f6;
  padding: 15px;
  border-bottom: 2px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.5rem;
  color: #1f2937;
}

.modal-body {
  padding: 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.roulette-container {
  position: relative;
  width: 200px;
  height: 220px;
  display: flex;
  justify-content: center;
  padding-top: 20px;
}

.roulette-pointer {
  position: absolute;
  top: 0;
  color: #ef4444;
  font-size: 30px;
  z-index: 10;
  filter: drop-shadow(0 2px 2px rgba(0,0,0,0.3));
}

.roulette-wheel {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  border: 4px solid white;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 10px rgba(0,0,0,0.3);
}

.wheel-section {
  position: absolute;
  width: 100%;
  height: 100%;
  transform-origin: 50% 50%;
  clip-path: polygon(50% 50%, 50% 0%, 100% 0%, 100% 50%);
}

.wheel-section:nth-child(odd) {
  background-color: #cbd5e1; /* 연한 회색 */
}
.wheel-section:nth-child(even) {
  background-color: #94a3b8; /* 진한 회색 */
}

.section-label {
  position: absolute;
  top: 15%;
  left: 50%;
  color: #1e293b;
  font-weight: bold;
  font-size: 24px;
  transform-origin: 50% 70px;
}

.info-text p {
    font-size: 1.2rem;
    font-weight: bold;
    margin: 5px 0;
}

@keyframes popIn {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}
</style>
