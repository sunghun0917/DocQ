<template>
  <div class="infinite-grid-container">
    <div class="grid-layer base-grid">
      <svg width="100%" height="100%">
        <defs>
          <pattern 
            id="grid-pattern-base" 
            :width="gridSize" 
            :height="gridSize" 
            patternUnits="userSpaceOnUse"
            :x="offsetX" 
            :y="offsetY"
          >
            <path 
              :d="`M ${gridSize} 0 L 0 0 0 ${gridSize}`" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="1"
            />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#grid-pattern-base)" />
      </svg>
    </div>

    <div 
      class="grid-layer highlight-grid"
      :style="maskStyle"
    >
      <svg width="100%" height="100%">
        <defs>
          <pattern 
            id="grid-pattern-highlight" 
            :width="gridSize" 
            :height="gridSize" 
            patternUnits="userSpaceOnUse"
            :x="offsetX" 
            :y="offsetY"
          >
            <path 
              :d="`M ${gridSize} 0 L 0 0 0 ${gridSize}`" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="1"
            />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#grid-pattern-highlight)" />
      </svg>
    </div>

    <div class="blur-spheres">
      <div class="sphere orange"></div>
      <div class="sphere primary"></div>
      <div class="sphere blue"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import gsap from 'gsap';

const gridSize = 80;
const speed = 0.5;

// [변경] containerRef 제거 (필요 없음)
const mouseX = ref(0);
const mouseY = ref(0);
const offsetX = ref(0);
const offsetY = ref(0);

// [핵심 변경] window 전체에서 마우스 좌표 계산
const handleMouseMove = (e: MouseEvent) => {
  mouseX.value = e.clientX;
  mouseY.value = e.clientY;
};

const maskStyle = computed(() => {
  const gradient = `radial-gradient(300px circle at ${mouseX.value}px ${mouseY.value}px, black, transparent)`;
  return {
    maskImage: gradient,
    WebkitMaskImage: gradient,
  };
});

const updateLoop = () => {
  offsetX.value = (offsetX.value + speed) % gridSize;
  offsetY.value = (offsetY.value + speed) % gridSize;
};

onMounted(() => {
  gsap.ticker.add(updateLoop);
  // [추가] 전역 이벤트 리스너 등록
  window.addEventListener('mousemove', handleMouseMove);
});

onUnmounted(() => {
  gsap.ticker.remove(updateLoop);
  // [추가] 리스너 해제
  window.removeEventListener('mousemove', handleMouseMove);
});
</script>

<style scoped>
/* 기존 스타일 그대로 유지 */
.infinite-grid-container {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  background-color: #000000;
  overflow: hidden;
  /* pointer-events 관련 설정 제거하거나 auto로 둠 */
}
/* ... 나머지 CSS 동일 ... */
.grid-layer { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.base-grid { opacity: 0.05; color: #ffffff; }
.highlight-grid { opacity: 0.4; color: #ffffff; }
.blur-spheres { position: absolute; inset: 0; pointer-events: none; z-index: 0; }
.grid-layer { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; z-index: 1; }
.sphere { position: absolute; border-radius: 50%; filter: blur(100px); opacity: 0.4; }
.sphere.orange { right: -20%; top: -20%; width: 40%; height: 40%; background: rgba(234, 88, 12, 0.4); }
.sphere.primary { right: 10%; top: -10%; width: 20%; height: 20%; background: rgba(255, 255, 255, 0.2); }
.sphere.blue { left: -10%; bottom: -20%; width: 40%; height: 40%; background: rgba(37, 99, 235, 0.4); }
</style>