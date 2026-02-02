<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  rotation: number
  isMoving: boolean
  isSpinning: boolean
  labelByIndex: number[]
}>()

// 부모에게 알릴 이벤트 정의
defineEmits(['spin']);

/*
 * 룰렛 원판 스타일 (회전 애니메이션)
 * props.rotation 값에 따라 CSS transform을 적용함
 * isSpinning이 true일 때만 transition(부드러운 움직임)을 줌
 */
const segmentCount = computed(() => Math.max(props.labelByIndex.length, 1))
const sectionAngle = computed(() => 360 / segmentCount.value)
const startAngle = computed(() => -sectionAngle.value / 2)

const wheelStyle = computed(() => {
  const light = '#4b5563'
  const dark = '#374151'
  const step = sectionAngle.value
  const stops: string[] = []
  for (let i = 0; i < segmentCount.value; i++) {
    const color = i % 2 === 0 ? light : dark
    const from = i * step
    const to = (i + 1) * step
    stops.push(`${color} ${from}deg ${to}deg`)
  }
  return {
    transform: `rotate(${props.rotation}deg)`,
    transition: props.isSpinning ? 'transform 1.8s cubic-bezier(0.1, 0, 0.1, 1)' : 'none',
    background: `conic-gradient(${stops.join(',')})`
  }
})

const labelStyle = (index: number) => {
  const angle = (index * sectionAngle.value) + (sectionAngle.value / 2)
  return {
    transform: `rotate(${angle}deg)`
  }
}
</script>

<template>
  <div class="roulette-container">
    <!-- 룰렛 화살표 (고정됨) -->
    <div class="roulette-pointer">▼</div>

    <!-- 돌아가는 룰렛 원판 -->
    <div class="roulette-wheel" :style="wheelStyle">
      <div
        v-for="(label, idx) in labelByIndex"
        :key="`${label}-${idx}`"
        class="wheel-section"
        :style="labelStyle(idx)"
      >
        <span class="section-label">{{ label }}</span>
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
  right: 30px;
  top: 50%;
  transform: translateY(-50%);
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
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.5);
}
.wheel-section {
  position: absolute;
  width: 100%;
  height: 100%;
  transform-origin: 50% 50%;
  pointer-events: none;
}
.section-label {
  position: absolute;
  top: 15%;
  left: 50%;
  color: white;
  font-weight: bold;
  font-size: 24px;
  transform: translateX(-50%);
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
