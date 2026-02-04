<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useGameStore } from '@/stores/gameStore';
import { useRoomStore } from '@/stores/roomStore';
import { useWebSocketStore } from '@/stores/websocketStore';
import { TresCanvas } from "@tresjs/core";
import GameRoulette3D from "@/components/game/board3D/GameRoulette3D.vue";
import GameGhostRoulette3D from "@/components/game/board3D/GameGhostRoulette3D.vue";
import gsap from 'gsap';

const gameStore = useGameStore();
const roomStore = useRoomStore();
const wsStore = useWebSocketStore();

const isSpinning = ref(false);
const rotation = ref(0);
const isVisible = ref(false);
const labelByIndex = ref<number[]>([-1, 1, 2, 3, 4, 5, 6, 7]);
const displayResult = ref<number | null>(null);
const isGhostMode = ref(false);

// 3D 룰렛 최종 골든 세팅
const initialRotation = ref({ x: 140, y: 0, z: 0 });
const spinAxis = ref<'y'>('y');
const arrowOffset = ref({ x: 0, y: 0, z: 3.2 }); // 사용자가 찾은 3.2 적용
const landingOffset = ref(60);
const modelScale = ref(1.5);        // 판 크기 1.5배로 확대
const modelPosition = ref({ x: 0, y: -0.5, z: 0 });

const shouldMount = computed(() => !!gameStore.rouletteTargetId);
const targetUserId = computed(() => gameStore.rouletteTargetId);
const rouletteValue = computed(() => gameStore.currentRoundResult?.rouletteValue ?? null);
const targetUser = computed(() => roomStore.users.find(u => u.playerId === targetUserId.value));

watch(() => gameStore.rouletteTargetId, (targetId) => {
  if (targetId) {
    const currentTurnPlayerId = gameStore.gameState.currentTurnPlayerId;
    const isTurnOwner = targetId === currentTurnPlayerId;
    isGhostMode.value = !isTurnOwner;
    labelByIndex.value = isTurnOwner ? [-1, 1, 2, 3, 4, 5, 6, 7] : [-1, 1, 2, 3];
    // 일반 룰렛은 60도, 고스트 룰렛은 120도 보정 적용
    landingOffset.value = isTurnOwner ? 60 : 120;
  }
}, { immediate: true });

const RESULT_HOLD_MS = 1000;
const FADE_OUT_MS = 300;
const ACK_DELAY_MS = 500;

const spinRoulette = (targetVal: number) => {
  if (isSpinning.value) return;
  isSpinning.value = true;
  displayResult.value = null;

  const labels = labelByIndex.value;
  const result = targetVal;

  let targetIndex = labels.indexOf(result);
  if (targetIndex === -1) {
    console.warn(`Roulette target ${result} not found in labels`, labels);
    targetIndex = 0;
  }

  const sectionAngle = 360 / labels.length;
  const totalSpins = 360 * 5;
  const currentDegrees = ((rotation.value % 360) + 360) % 360;
  const targetFinalAngle = -(targetIndex * sectionAngle) + landingOffset.value;
  const extra = (360 - currentDegrees) % 360;

  const targetRotationValue = rotation.value + totalSpins + extra + targetFinalAngle;

  gsap.to(rotation, {
    value: targetRotationValue,
    duration: 3.2,
    ease: "power4.out",
    onComplete: () => {
      isSpinning.value = false;
      displayResult.value = rouletteValue.value ?? result;

      setTimeout(() => {
        isVisible.value = false;
        setTimeout(() => {
          gameStore.setRouletteResult(result);
          wsStore.sendAck(
            'ROULETTE_COMPLETE',
            gameStore.currentRoundResult?.quizId,
            gameStore.gameState.round ?? undefined
          );
          gameStore.handleRouletteComplete();
        }, ACK_DELAY_MS);
      }, RESULT_HOLD_MS);
    }
  });
};

watch(() => [gameStore.rouletteTargetId, rouletteValue.value] as const, ([newId, newVal]) => {
  if (newId) {
    isVisible.value = true;
  }
  if (newId && newVal != null && !isSpinning.value) {
    setTimeout(() => {
      spinRoulette(newVal as number);
    }, 100);
  }
}, { immediate: true });

</script>

<template>
  <div v-if="shouldMount && isVisible" class="roulette-overlay">
    <!-- 흰 박스를 제거하고 콘텐츠만 배치 -->
    <div class="roulette-content">
      <div class="floating-header">
        <h3>{{ targetUser?.nickname || 'Unknown' }}님의 결과 확인</h3>
      </div>

      <div class="roulette-container">
        <TresCanvas alpha :clear-alpha="0" shadows>
          <TresPerspectiveCamera :position="[0, 6, -10]" :look-at="[0, 0, 0]" />

          <component :is="isGhostMode ? GameGhostRoulette3D : GameRoulette3D" :rotation="rotation"
            :isSpinning="isSpinning" :spin-axis="spinAxis" :initial-rotation="initialRotation"
            :arrow-offset="arrowOffset" :model-scale="modelScale" :model-position="modelPosition" use-raw-model />

          <TresAmbientLight :intensity="1.5" />
          <TresDirectionalLight :position="[2, 5, 2]" :intensity="1.5" cast-shadow />
        </TresCanvas>
      </div>

      <div class="info-text">
        <div v-if="displayResult !== null" class="result-badge cartoon-panel">
          <span class="result-number">{{ displayResult }}</span>
          <span class="result-label">칸 이동!</span>
        </div>
        <div v-else class="waiting-badge cartoon-panel">
          <p class="waiting-text">두구두구...</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.roulette-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
  backdrop-filter: blur(8px);
  font-family: 'Jua', sans-serif;
  /* 전체 폰트 적용 */
}

.roulette-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 900px;
  animation: fadeIn 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}

.floating-header h3 {
  color: #fff8e1;
  /* 크림색 텍스트 */
  font-size: 2.2rem;
  font-weight: 400;
  /* Jua는 굵기 하나 */
  margin-bottom: 30px;
  text-shadow: 3px 3px 0 #5d4037, 0 0 15px rgba(0, 0, 0, 0.3);
  /* 카툰풍 굵은 그림자 */
  letter-spacing: 1px;
}

.roulette-container {
  position: relative;
  width: 100%;
  height: 550px;
  cursor: default;
}

/* 카툰 스타일 패널 공통 */
.cartoon-panel {
  background: #fff8e1;
  /* 메인 테마의 크림색 배경 */
  border: 5px solid #8d6e63;
  /* 나무색 테두리 */
  border-radius: 24px;
  box-shadow: 0 8px 0 #5d4037, 0 15px 20px rgba(0, 0, 0, 0.2);
  /* 3D 입체 그림자 */
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  padding: 15px 40px;
  transform: rotate(-2deg);
  /* 살짝 기울여서 장난스러운 느낌 */
  animation: popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.result-badge {
  /* 결과 나왔을 때 스타일 */
}

.waiting-badge {
  padding: 12px 30px;
  transform: rotate(1deg);
  /* 대기 중엔 반대로 기울임 */
  background: #d7ccc8;
  /* 대기 중엔 조금 더 어두운 종이색 */
}

.result-number {
  color: #e64a19;
  /* 진한 주황/빨강으로 강조 */
  font-size: 4rem;
  line-height: 1;
  text-shadow: 2px 2px 0 #fff;
  /* 흰색 테두리 효과 */
}

.result-label {
  color: #5d4037;
  /* 진한 갈색 텍스트 (메인 테마 텍스트 색상) */
  font-size: 2rem;
  margin-top: 8px;
  /* 폰트 베이스라인 보정 */
}

.waiting-text {
  color: #5d4037;
  font-size: 1.8rem;
  margin: 0;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes popIn {
  from {
    opacity: 0;
    transform: scale(0.5) translateY(50px);
  }

  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>
