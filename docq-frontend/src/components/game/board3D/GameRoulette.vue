<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  rotation: { type: Number, required: true },    // 현재 회전 각도 (Board.vue가 계산해줌)
  isMoving: { type: Boolean, required: true },   // 말이 이동 중인지 (버튼 비활성화용)
  isSpinning: { type: Boolean, required: true }, // 룰렛이 도는 중인지 (애니메이션 트리거용)
  labelByIndex: { type: Array, required: true }, // 룰렛 칸에 표시할 라벨 배열 (예: [1,2,3...])
});

// 부모에게 알릴 이벤트 정의
defineEmits(['spin']);

/*
 * 룰렛 원판 스타일 (회전 애니메이션)
 * props.rotation 값에 따라 CSS transform을 적용함
 * isSpinning이 true일 때만 transition(부드러운 움직임)을 줌
 */
const wheelStyle = computed(() => ({
  transform: `rotate(${props.rotation}deg)`,
  transition: props.isSpinning ? 'transform 3s cubic-bezier(0.1, 0, 0.1, 1)' : 'none',
}));

/*
 * 각 조각(Section)의 스타일
 * 원형을 6등분(60도)하여 배치함
 */
const sectionStyle = (n:number) => {
  const sectionAngle = 360 / 6;
  const startAngle = -30; // 0도를 12시 방향에 맞추기 위한 보정값 (조각 모양에 따라 다름)
  const angle = startAngle + (n - 1) * sectionAngle;
  return {
    transform: `rotate(${angle}deg)`
  };
};
</script>

<template>
  <div class="roulette-container">
    <!-- 룰렛 화살표 (고정됨) -->
    <div class="roulette-pointer">▼</div>

    <!-- 돌아가는 룰렛 원판 -->
    <div class="roulette-wheel" :style="wheelStyle">
      <!-- 6개의 숫자 칸 생성 -->
      <div
        v-for="i in 6"
        :key="i"
        class="wheel-section"
        :style="sectionStyle(i)"
      >
        <!-- 실제 숫자는 props로 받은 labelByIndex 배열을 참조 -->
        <span class="section-label">{{ labelByIndex[i - 1] }}</span>
      </div>
    </div>

    <!-- 돌리기 버튼 -->
    <button
      class="spin-btn"
      @click="$emit('spin')"
      :disabled="isMoving || isSpinning"
    >
      {{ isMoving ? '이동 중...' : '룰렛 돌리기' }}
    </button>
  </div>
</template>

<style scoped>
.roulette-container {
  position: absolute;
  left: 50px;
  top: 50px;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1000;
}
.roulette-pointer {
  color: #ff4d4d;
  font-size: 24px;
  z-index: 1100;
  margin-bottom: -15px;
  filter: drop-shadow(0 2px 2px rgba(0, 0, 0, 0.5));
}
.roulette-wheel {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  border: 6px solid #ffffff;
  background-color: #333;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.5);
}
.wheel-section {
  position: absolute;
  width: 100%;
  height: 100%;
  transform-origin: 50% 50%;
  clip-path: polygon(50% 50%, 50% 0%, 100% 0%, 100% 21%);
}
.wheel-section:nth-child(odd) {
  background-color: #4b5563;
}
.wheel-section:nth-child(even) {
  background-color: #374151;
}
.section-label {
  position: absolute;
  top: 15%;
  left: 62%;
  color: white;
  font-weight: bold;
  font-size: 24px;
  transform: rotate(30deg);
}
.spin-btn {
  margin-top: 20px;
  padding: 10px 20px;
  background-color: #fbbf24;
  border: none;
  border-radius: 20px;
  font-weight: bold;
  cursor: pointer;
}
.spin-btn:active {
  transform: scale(0.95);
}
.spin-btn:disabled {
  background-color: #666;
  cursor: not-allowed;
}
</style>
