<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { TresCanvas } from '@tresjs/core';
import GameItemRoulette3D from '../game/board3D/GameItemRoulette3D.vue';
import { shallowRef } from 'vue';
import gsap from 'gsap';

const props = defineProps<{
  buffCode: string;
  winnerName?: string;
}>();

const emit = defineEmits<{
  (e: 'done', payload: { buffCode: string; requiresInteraction: boolean }): void;
}>();

const isSpinning = ref(false);
const rotation = ref(0);
const isVisible = ref(true);
const showResult = ref(false);

const labels = [
  { code: 'BOOM', label: '꽝', emoji: '💥' },                   // 0번: 폭탄 → 폭탄 ✓
  { code: 'BANANA', label: '바나나', emoji: '🍌' },            // 1번: 이전 +3 위치 → 바나나
  { code: 'SWAP_POSITION', label: '자리 바꾸기', emoji: '�' }, // 2번: 이전 방해하기 위치 → 자리바꾸기
  { code: 'QUIZ_INTERFERENCE', label: '방해하기', emoji: '�' }, // 3번: 이전 자리바꾸기 위치 → 방해하기
  { code: 'PLUS_THREE', label: '+3칸', emoji: '➕3️⃣' }        // 4번: 이전 바나나 위치 → +3
];

// Note: 3D 모델의 아이템 배치 순서를 확인해야 정확한 각도 계산 가능
// 일단 등분하여 각도 할당 (72도씩)
// Arrow가 상단(90도 or 0도)에 있다고 가정하고 계산

const targetIndex = computed(() => {
  const idx = labels.findIndex(item => item.code === props.buffCode);
  if (idx < 0) {
    console.warn(`[Item Roulette] Unknown buffCode: "${props.buffCode}". Defaulting to BOOM.`);
    return 0; // BOOM
  }
  return idx;
});

// 인터랙션이 필요한 아이템인지 확인
const requiresInteraction = computed(() => {
  const code = props.buffCode;
  return code === 'BANANA' || code === 'SWAP_POSITION' || code === 'QUIZ_INTERFERENCE';
});

const sectionAngle = 360 / 5; // 72도

const spin = () => {
  if (isSpinning.value) return;
  isSpinning.value = true;
  showResult.value = false;

  // 디버깅: buffCode와 targetIndex 확인
  console.log('[Item Roulette] buffCode:', props.buffCode);
  console.log('[Item Roulette] targetIndex:', targetIndex.value);
  console.log('[Item Roulette] labels:', labels);

  // 타겟 아이템이 12시 방향(Arrow)에 오도록 회전 각도 계산
  // 모델의 0도가 어디인지에 따라 오프셋 조정 필요. 
  // 가정: 인덱스 0이 0도에서 시작. 시계방향 배치.
  // 멈춰야 할 각도 = -(index * 72) + 오프셋

  // 랜덤 회전 수 (5바퀴 고정, 일반 룰렛과 동일)
  const totalSpins = 360 * 5;

  // 각 아이템별 개별 오프셋 (테스트 결과 기반)
  const offsetByCode: Record<string, number> = {
    'BANANA': 30,         // 이전 PLUS_THREE 값
    'BOOM': -40,
    'SWAP_POSITION': 120,
    'QUIZ_INTERFERENCE': 180,
    'PLUS_THREE': -120    // 이전 BANANA 값
  };
  const landingOffset = offsetByCode[props.buffCode] ?? 0;
  const targetFinalAngle = landingOffset; // 공식 계산 대신 직접 오프셋 사용

  // 현재 각도에서 0도로 돌아가기 위한 추가 회전 (일반 룰렛과 동일)
  const currentDegrees = ((rotation.value % 360) + 360) % 360;
  const extra = (360 - currentDegrees) % 360;

  // 최종 회전 각도
  const targetRotationValue = rotation.value + totalSpins + extra + targetFinalAngle;

  console.log('[Item Roulette] Calculation:');
  console.log('  - current rotation:', rotation.value);
  console.log('  - totalSpins:', totalSpins);
  console.log('  - extra:', extra);
  console.log('  - targetFinalAngle:', targetFinalAngle);
  console.log('  - targetRotationValue:', targetRotationValue);

  // 애니메이션 객체 사용 (수동 구현 대신 GSAP 활용 추천하거나 requestAnimationFrame)
  // 여기서는 간단히 값 증가 시뮬레이션 (CSS transition 효과 활용 불가하므로 JS 애니메이션 필요)

  gsap.to(rotation, {
    value: targetRotationValue, // 도 단위
    duration: 3.2,
    ease: "power4.out",
    onComplete: () => {
      isSpinning.value = false;
      showResult.value = true;
      setTimeout(() => {
        isVisible.value = false;
        emit('done', {
          buffCode: props.buffCode,
          requiresInteraction: requiresInteraction.value
        });
      }, 1500);
    }
  });
};

onMounted(() => {
  setTimeout(() => spin(), 500);
});



// rotation 값을 라디안으로 변환하여 전달
const rotationRadian = computed(() => rotation.value * (Math.PI / 180));

</script>

<template>
  <div v-if="isVisible" class="roulette-overlay">
    <div class="roulette-content">
      <div class="floating-header">
        <h3>{{ winnerName || '승자' }}님의 보상</h3>
      </div>

      <div class="roulette-container">
        <TresCanvas alpha :clear-alpha="0" shadows>
          <TresPerspectiveCamera :position="[0, 8, -8]" :look-at="[0, 0, 0]" />

          <GameItemRoulette3D :rotation="rotation" :is-spinning="isSpinning" :model-scale="1.8"
            :arrow-offset="{ x: 0, y: 0, z: 3.2 }" :initial-rotation="{ x: 140, y: 0, z: 0 }" />

          <TresAmbientLight :intensity="1.5" />
          <TresDirectionalLight :position="[2, 5, 2]" :intensity="1.5" cast-shadow />
        </TresCanvas>
      </div>

      <div class="info-text">
        <div v-if="showResult" class="result-badge cartoon-panel">
          <span class="result-emoji">{{ labels[targetIndex]?.emoji }}</span>
          <span class="result-label">{{ labels[targetIndex]?.label }} 획득!</span>
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
  z-index: 2500;
  backdrop-filter: blur(8px);
  font-family: 'Jua', sans-serif;
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
  font-size: 2.2rem;
  font-weight: 400;
  margin-bottom: 30px;
  text-shadow: 3px 3px 0 #5d4037, 0 0 15px rgba(0, 0, 0, 0.3);
  letter-spacing: 1px;
}

.roulette-container {
  position: relative;
  width: 100%;
  height: 550px;
  cursor: default;
}

.cartoon-panel {
  background: #fff8e1;
  border: 5px solid #8d6e63;
  border-radius: 24px;
  box-shadow: 0 8px 0 #5d4037, 0 15px 20px rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  padding: 15px 40px;
  transform: rotate(-2deg);
  animation: popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.result-badge {
  /* 결과 나왔을 때 스타일 */
}

.waiting-badge {
  padding: 12px 30px;
  transform: rotate(1deg);
  background: #d7ccc8;
}

.result-emoji {
  font-size: 3rem;
}

.result-label {
  color: #5d4037;
  font-size: 2rem;
  margin-top: 8px;
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
