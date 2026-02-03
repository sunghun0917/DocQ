<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';

const cursorRef = ref<HTMLDivElement | null>(null);

// 초기 위치를 화면 중앙으로 설정
let mouseX = window.innerWidth / 2;
let mouseY = window.innerHeight / 2;
let currentX = window.innerWidth / 2;
let currentY = window.innerHeight / 2;

const isPointer = ref(false);
const isInput = ref(false);
const isClicking = ref(false);

const updateMouse = (e: MouseEvent) => {
  mouseX = e.clientX;
  mouseY = e.clientY;

  const target = e.target as HTMLElement;
  const inputEl = target.closest('input, textarea, [contenteditable="true"]');
  const pointerEl = target.closest('button, a, select, [role="button"], .cursor-pointer, label');

  isInput.value = !!inputEl;
  isPointer.value = !inputEl && !!pointerEl;
};

const handleMouseDown = () => { 
  if (isClicking.value) return; // 이미 재생 중이면 무시 (또는 리셋 로직 추가 가능)
  isClicking.value = true;
  setTimeout(() => {
    isClicking.value = false;
  }, 450); // 애니메이션 0.4초보다 약간 길게 잡아서 끊김 방지
};
// 마우스 UP은 애니메이션에 영향 주지 않도록 제거 (원한다면 별도 상태로 분리 가능)
const handleMouseUp = () => { /* isClicking.value = false; (이거 때문에 끊김) */ };

const animate = () => {
  if (cursorRef.value) {
    const speed = 0.25; 
    currentX += (mouseX - currentX) * speed;
    currentY += (mouseY - currentY) * speed;
    // 기존에 잘 된다고 하신 좌표 계산 유지
    cursorRef.value.style.transform = `translate3d(${currentX - 16}px, ${currentY - 16}px, 0)`;
  }
  requestAnimationFrame(animate);
};

onMounted(() => {
  console.log("CustomCursor Mounted");
  window.addEventListener('mousemove', updateMouse, { passive: true });
  window.addEventListener('mousedown', handleMouseDown);
  window.addEventListener('mouseup', handleMouseUp);
  requestAnimationFrame(animate);
});

onUnmounted(() => {
  window.removeEventListener('mousemove', updateMouse);
  window.removeEventListener('mousedown', handleMouseDown);
  window.removeEventListener('mouseup', handleMouseUp);
});
</script>

<template>
  <div 
    ref="cursorRef" 
    class="roulette-cursor"
    :class="{ 'is-pointer': isPointer, 'is-input': isInput, 'is-clicking': isClicking }"
  >
    <div class="cursor-body">
      <div class="cursor-cap"></div>
    </div>
  </div>
</template>
<style scoped>
.roulette-cursor {
  position: fixed;
  top: 0;
  left: 0;
  width: 32px;
  height: 32px;
  z-index: 9999; 
  pointer-events: none;
  will-change: transform;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cursor-body {
  width: 24px;
  height: 24px;
  border-radius: 0 50% 50% 50%;
  background: linear-gradient(135deg, #66BB6A, #43A047);
  box-shadow: 2px 2px 5px rgba(0,0,0,0.3);
  border: 2px solid #1B5E20;
  
  /* [수정] 기본 상태: 각도 0도 명시 */
  transform: rotate(0deg) scale(1);
  /* [수정] transition을 transform에만 적용하되, 클릭 땐 없앨 거임 */
  transition: transform 0.2s, background-color 0.2s, border-color 0.2s; 
}

.cursor-cap {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 8px;
  height: 8px;
  background-color: white;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: inset 1px 1px 2px rgba(0,0,0,0.2);
}

/* --- 상태별 스타일 --- */

/* 1. 버튼 위 (Hover) */
.roulette-cursor.is-pointer .cursor-body {
  background: linear-gradient(135deg, #EF5350, #E53935);
  border-color: #B71C1C;
  transform: rotate(0deg) scale(1.1);
}

/* 2. 클릭 중 (Active) - 여기가 핵심 수정! */
.roulette-cursor.is-clicking .cursor-body {
  /* [핵심] 애니메이션 중에는 트랜지션을 꺼야 팅기는 효과가 즉시 나옴 */
  transition: none; 
  
  /* 애니메이션 재생 */
  animation: tick 0.4s ease-out forwards;
}

/* 3. 입력창 (Input) */
.roulette-cursor.is-input .cursor-body {
  top: -12px;
  left: -2px;
  width: 4px;
  height: 24px;
  border-radius: 2px;
  background: #29B6F6;
  border: none;
  transform: rotate(0deg);
  box-shadow: none;
}
.roulette-cursor.is-input .cursor-cap { display: none; }


/* 팅기는 애니메이션 (각도를 좀 더 과장되게 수정함) */
@keyframes tick {
  0% { transform: rotate(0deg) scale(1); }
  30% { transform: rotate(-30deg) scale(0.9); }
  50% { transform: rotate(20deg) scale(1.1); }
  70% { transform: rotate(-10deg) scale(0.95); }
  85% { transform: rotate(5deg) scale(1.02); }
  100% { transform: rotate(0deg) scale(1); }
}
</style>

<style>
/* 1. 모든 요소, 모든 가상 클래스에 대해 커서 숨김 강제 */
*, *::before, *::after {
  cursor: none !important;
}

/* 2. hover, active, focus 상태일 때도 무조건 숨김 (손바닥 방지) */
a:hover, button:hover, input:hover, select:hover, 
[role="button"]:hover, .cursor-pointer:hover {
  cursor: none !important;
}

/* 3. 혹시 모를 html, body까지 확인 사살 */
html, body {
  cursor: none !important;
  height: 100%; /* 스크롤 방지용 (필요시 삭제) */
}
</style>