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
  { code: 'BOOM', label: '꽝', emoji: '💥' },                  // 1번 위치
  { code: 'BANANA', label: '바나나', emoji: '🍌' },            // 2번 위치
  { code: 'SWAP_POSITION', label: '자리 바꾸기', emoji: '🔄' }, // 3번 위치
  { code: 'QUIZ_INTERFERENCE', label: '방해하기', emoji: '🔀' }, // 4번 위치
  { code: 'PLUS_THREE', label: '+3칸', emoji: '➕3️⃣' }       // 5번 위치
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

  // 타겟 각도 (이전 코드 참고하여 조정)
  // 인덱스별 중심 각도: 0->0, 1->72, 2->144...
  // 멈춰야 할 각도 = -(index * 72) + 오프셋
  const landingOffset = 40; // 착지 오프셋 (도 단위)
  const targetFinalAngle = -(targetIndex.value * sectionAngle) + landingOffset;

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
    <div class="roulette-modal">
      <div class="modal-header">
        <h3>아이템 룰렛</h3>
        <p v-if="winnerName" class="winner-text">{{ winnerName }}님 보상</p>
      </div>
      <div class="modal-body">
        <div class="canvas-container">
          <TresCanvas clear-color="#ffffff" alpha>
            <TresPerspectiveCamera :position="[0, 6, -10]" :look-at="[0, 0, 0]" />
            <TresAmbientLight :intensity="2.0" />
            <TresDirectionalLight :position="[5, 5, 5]" :intensity="1.5" cast-shadow />

            <GameItemRoulette3D :rotation="rotation" :is-spinning="isSpinning" :model-scale="1.8"
              :arrow-offset="{ x: 0, y: 0, z: 3.2 }" :initial-rotation="{ x: 140, y: 0, z: 0 }" />
          </TresCanvas>
        </div>

        <div class="info-text">
          <p v-if="showResult" class="result-text">
            {{ labels[targetIndex]?.label }} 획득!
          </p>
          <small v-if="showResult">잠시 후 게임이 이어집니다</small>
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
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2500;
  backdrop-filter: blur(4px);
}

.roulette-modal {
  background: white;
  width: 460px;
  height: 520px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
}

.modal-header {
  background: #f8fafc;
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  text-align: center;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.5rem;
  color: #0f172a;
  font-weight: 800;
}

.winner-text {
  margin: 4px 0 0;
  color: #64748b;
  font-weight: 600;
}

.modal-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  background: linear-gradient(180deg, #ffffff 0%, #f1f5f9 100%);
}

.canvas-container {
  width: 100%;
  height: 320px;
  /* 3D 캔버스 영역 */
}

.info-text {
  margin-top: 10px;
  text-align: center;
  height: 60px;
}

.result-text {
  font-size: 1.8rem;
  font-weight: 900;
  color: #ea580c;
  margin: 0;
  animation: popIn 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}

@keyframes popIn {
  from {
    transform: scale(0.5);
    opacity: 0;
  }

  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
