<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { useGameStore } from '@/stores/gameStore';
import { useRoomStore } from '@/stores/roomStore';

const gameStore = useGameStore();
const roomStore = useRoomStore();

const isSpinning = ref(false);
const rotation = ref(0);
const isVisible = ref(false); // [NEW] 내부 표시 관리
const labelByIndex = [1, 2, 3, 4, 5, 6];

// 컴포넌트 마운트 여부 (Store 상태 의존)
const shouldMount = computed(() => !!gameStore.rouletteTargetId);
const targetUserId = computed(() => gameStore.rouletteTargetId);
const targetUser = computed(() => roomStore.users.find(u => u.id === targetUserId.value));

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
const sectionStyle = (n: number) => {
  const sectionAngle = 360 / 6;
  const startAngle = -30;
  const angle = startAngle + (n - 1) * sectionAngle;
  return {
    transform: `rotate(${angle}deg)`
  };
};

/*
 * 룰렛 자동 실행 로직
 * 컴포넌트가 마운트되거나 visible해지면 자동 실행
 */
const spinRoulette = () => {
  if (isSpinning.value) return;
  isSpinning.value = true;

  // 1~6 랜덤값 생성
  const result = Math.floor(Math.random() * 6) + 1;
  const targetIndex = labelByIndex.indexOf(result);

  // 회전 애니메이션 계산
  const sectionAngle = 360 / 6;
  const totalSpins = 360 * 5; // 최소 5바퀴
  const currentDegrees = ((rotation.value % 360) + 360) % 360;
  const extraToZero = (360 - currentDegrees) % 360;
  const targetRotation = -(targetIndex * sectionAngle);

  rotation.value += totalSpins + extraToZero + targetRotation;

  // 회전 종료 후 로직
  // 1. 회전 시간(3.2s) 대기
  // 2. 결과 확인 시간(1s) 대기
  // 3. 팝업 닫기
  // 4. GameBoard에 이동 신호 전송
  setTimeout(() => {
    isSpinning.value = false;
    
    // 결과 보여주기 위한 짧은 대기
    setTimeout(() => {
        isVisible.value = false; // 팝업 닫기 (이러면 보드판이 보임)
        
        // 팝업이 완전히 사라진 후 이동 신호 전송 (Fade out 고려 0.3s)
        setTimeout(() => {
             gameStore.setRouletteResult(result);
        }, 300);
    }, 1500); // 1.5초 동안 결과 보여줌

  }, 3200);
};

// TargetId가 생기면 팝업을 열고 스핀 시작
watch(() => gameStore.rouletteTargetId, (newVal) => {
  if (newVal) {
    isVisible.value = true;
    setTimeout(() => {
        spinRoulette();
    }, 500);
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
                    v-for="i in 6"
                    :key="i"
                    class="wheel-section"
                    :style="sectionStyle(i)"
                >
                    <span class="section-label">{{ labelByIndex[i - 1] }}</span>
                </div>
                </div>
            </div>
            
            <div class="info-text">
                <p>앞으로 {{ rotation > 0 && !isSpinning ? '...' : '' }} 칸 이동합니다</p>
                <small>(잠시 후 맵 이동 애니메이션)</small>
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
  background: #333;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 10px rgba(0,0,0,0.3);
}

.wheel-section {
  position: absolute;
  width: 100%;
  height: 100%;
  transform-origin: 50% 50%;
  clip-path: polygon(50% 50%, 50% 0%, 100% 0%, 100% 21%);
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
  left: 62%;
  color: #1e293b;
  font-weight: bold;
  font-size: 24px;
  transform: rotate(30deg);
}

.info-text p {
    font-size: 1.2rem;
    font-weight: bold;
    margin: 5px 0;
}

.info-text small {
    color: #6b7280;
}

@keyframes popIn {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}
</style>
