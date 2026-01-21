<template>
  <div class="board-container">
    <div class="isomeric-wrapper" :style="gridOffset">
      <!-- 타일 렌더링 -->
      <div
        v-for="(tile, index) in mapData"
        :key="index"
        class="tile"
        :style="{
          left: `${tile.isoX}px`,
          top: `${tile.isoY}px`,
          zIndex: tile.zIndex
        }"
      >
        <img :src="TILE_TYPES[tile.type]" alt="tile" />
      </div>

      <!-- 캐릭터: 위치용 래퍼 + 점프용 이미지 -->
      <div class="character-wrapper" :style="characterWrapperStyle">
        <div class="character-man" :class="{ 'is-jumping': isJumping }">
          <img :src="man" alt="character" />
        </div>
      </div>
    </div>

    <!-- 룰렛 -->
    <div class="roulette-container">
      <div class="roulette-pointer">▼</div>
      <div class="roulette-wheel" :style="wheelStyle">
        <div
          v-for="i in 6"
          :key="i"
          class="wheel-section"
          :style="sectionStyle(i)"
        >
           <!-- 숫자 대신 섹션 인덱스를 크게 찍어보기 -->
            <!-- <span class="section-label">{{ i }}</span> -->
          <span class="section-label">{{ labelByIndex[i - 1] }}</span>
        </div>
      </div>
      <button
        class="spin-btn"
        @click="spinRoulette"
        :disabled="isMoving || isSpinning"
      >
        {{ isMoving ? '이동 중...' : '룰렛 돌리기' }}
      </button>

      <div class="debug-result" style="color: white;">룰렛 결과: {{ lastResult }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

// 1. 이미지
import base from '@/assets/tile/base.png';
import path from '@/assets/tile/path.png';
import end from '@/assets/tile/end.png';
import man from '@/assets/character/man.png';

// 2. 타일 타입
const TILE_TYPES = {
  0: base, // 배경
  1: path, // 일반 길
  2: end   // 시작/끝
};

// 3. 그리드/타일 설정
const TILE_WIDTH = 38;
const TILE_HEIGHT = 22;
const GRID_WIDTH = 12;
const GRID_HEIGHT = 16;

// 등각 좌표/깊이 공통 함수
const toIso = (gridX, gridY) => {
  return {
    isoX: (gridX - gridY) * TILE_WIDTH,
    isoY: (gridX + gridY) * TILE_HEIGHT
  };
};

const computeDepth = (gridX, gridY) => {
  // 기본적으로 x + y 기준으로 정렬[web:16]
  return gridX + gridY;
};

// 4. 경로 좌표 (grid 기준)
const pathCoords = [
  { x: 11, y: 15 }, { x: 10, y: 15 }, { x: 9, y: 15 }, { x: 8, y: 15 },
  { x: 8, y: 14 }, { x: 8, y: 13 }, { x: 8, y: 12 },
  { x: 7, y: 12 }, { x: 6, y: 12 }, { x: 5, y: 12 },
  { x: 5, y: 11 }, { x: 5, y: 10 }, { x: 5, y: 9 }, { x: 5, y: 8 },
  { x: 4, y: 8 }, { x: 3, y: 8 }, { x: 2, y: 8 },
  { x: 2, y: 7 }, { x: 2, y: 6 }, { x: 2, y: 5 }, { x: 2, y: 4 },
  { x: 3, y: 4 }, { x: 4, y: 4 },
  { x: 4, y: 3 }, { x: 4, y: 2 },
  { x: 3, y: 2 }, { x: 2, y: 2 },
  { x: 2, y: 1 }, { x: 1, y: 1 }, { x: 0, y: 1 }, { x: 0, y: 0 }
];

// 5. 상태
const currentIndex = ref(0);
const isJumping = ref(false);
const isMoving = ref(false);
const isSpinning = ref(false);
const rotation = ref(0);

// 6. 캐릭터 위치 스타일 (transform 충돌 방지)
const characterWrapperStyle = computed(() => {
  const currentPos = pathCoords[currentIndex.value] ?? pathCoords[0];
  const { isoX, isoY } = toIso(currentPos.x, currentPos.y);
  const depth = computeDepth(currentPos.x, currentPos.y);

  return {
    left: `${isoX}px`,
    top: `${isoY}px`,
    zIndex: depth * 10 + 5, // 타일보다 살짝 위
    position: 'absolute',
    transform: 'translate(-50%, -100%)',
    transition: 'left 0.4s ease, top 0.4s ease, z-index 0s'
  };
});

// 7. 룰렛 스타일
const labelByIndex = [2, 3, 4, 5, 6, 1]; // 예시: 위·오른위·오른아래·아래·왼아래·왼위

const indexByLabel = {
  2: 0, 3: 1, 4: 2, 5: 3, 6: 4, 1: 5
};

const wheelStyle = computed(() => ({
  transform: `rotate(${rotation.value}deg)`,
  transition: isSpinning.value
    ? 'transform 3s cubic-bezier(0.1, 0, 0.1, 1)'
    : 'none'
}));

// 섹션 스타일: 6조각, pointer 위쪽(0deg)이 1번
const sectionStyle = (n) => {
  const sectionAngle = 360 / 6; // 60deg
  const startAngle = -90;       // 위쪽을 기준 0으로 두고 싶을 때[web:8]
  const angle = startAngle + (n - 1) * sectionAngle;
  return {
    transform: `rotate(${angle}deg)`
  };
};

// 8. 룰렛 돌리기
const lastResult = ref(null); // script setup 상단에 추가

const spinRoulette = () => {
  if (isMoving.value || isSpinning.value) return;

  isSpinning.value = true;

  // 1~6
  const result = Math.floor(Math.random() * 6) + 1;
  lastResult.value = result;
  console.log('[roulette] result =', result); // ← ① 결과 숫자 확인

  const sectionAngle = 360 / 6; // 60
  const totalSpins = 360 * 5;

  const pointerAngle = -90;
  const startAngle = -90;
  const resultSectionCenter =
    startAngle + sectionAngle * (result - 0.5);

  const relativeAngle = pointerAngle - resultSectionCenter;
  const finalRotate = totalSpins + relativeAngle;
  rotation.value += finalRotate;

  setTimeout(() => {
    isSpinning.value = false;
    console.log('[roulette] spin end, result =', result); // ← ② 이동 직전
    moveStep(result);
  }, 3200);
};

// const spinRoulette = () => {
//   if (isMoving.value || isSpinning.value) return;

//   isSpinning.value = true;

//   // 1~6
//   const result = Math.floor(Math.random() * 6) + 1;

//   const sectionAngle = 360 / 6; // 60
//   const totalSpins = 360 * 5;

//   // pointer 위쪽(0deg)이 1번 섹션 중앙을 가리키도록 각도 계산
//   const pointerAngle = -90; // CSS 기준, 위쪽이 -90deg[web:8]
//   const targetAngle = pointerAngle - (result - 1) * sectionAngle;

//   const finalRotate = totalSpins + (360 - targetAngle);
//   rotation.value += finalRotate;

//   setTimeout(() => {
//     isSpinning.value = false;
//     moveStep(result);
//   }, 3200);
// };

// 9. 지정 숫자만큼 이동
const moveStep = async (steps) => {
  console.log('[moveStep] start, steps =', steps, 'currentIndex =', currentIndex.value);
  isMoving.value = true;

  for (let i = 0; i < steps; i++) {
    if (currentIndex.value >= pathCoords.length - 1) {
      console.log('[moveStep] reached end of path, break. i =', i);
      break;
    }
    await jumpStep();
  }

  isMoving.value = false;
  console.log('[moveStep] end, currentIndex =', currentIndex.value);
};

// const moveStep = async (steps) => {
//   isMoving.value = true;

//   for (let i = 0; i < steps; i++) {
//     // 경계 체크
//     if (currentIndex.value >= pathCoords.length - 1) break;
//     await jumpStep();
//   }

//   isMoving.value = false;
// };

// 10. 한 칸 점프
const jumpStep = () => {
  return new Promise((resolve) => {
    if (currentIndex.value >= pathCoords.length - 1) {
      console.log('[jumpStep] already at end, skip');
      resolve();
      return;
    }

    currentIndex.value++;
    console.log('[jumpStep] moved to index =', currentIndex.value);

    isJumping.value = true;

    setTimeout(() => {
      isJumping.value = false;
      setTimeout(resolve, 100);
    }, 400);
  });
};

// const jumpStep = () => {
//   return new Promise((resolve) => {
//     // 경계 체크 (이중 안전)
//     if (currentIndex.value >= pathCoords.length - 1) {
//       resolve();
//       return;
//     }

//     isJumping.value = true;
//     currentIndex.value++;

//     setTimeout(() => {
//       isJumping.value = false;
//       setTimeout(resolve, 100);
//     }, 400);
//   });
// };

// 11. 맵 오프셋 (타일 실제 범위 기반으로 화면 중앙 정렬)
const createRawTiles = () => {
  const tiles = [];

  for (let i = 0; i < GRID_WIDTH; i++) {
    for (let j = 0; j < GRID_HEIGHT; j++) {
      const { isoX, isoY } = toIso(i, j);
      const depth = computeDepth(i, j);

      tiles.push({
        gridX: i,
        gridY: j,
        isoX,
        isoY,
        zIndex: depth * 10, // 공통 규칙[web:16]
        type: 0
      });
    }
  }

  // 길 적용
  pathCoords.forEach((p, index) => {
    const target = tiles.find(
      (t) => t.gridX === p.x && t.gridY === p.y
    );
    if (target) {
      target.type =
        index === 0 || index === pathCoords.length - 1 ? 2 : 1;
    }
  });

  return tiles;
};

const mapData = ref(createRawTiles());

const gridOffset = computed(() => {
  if (!mapData.value.length) return { transform: 'translate(0, 0)' };

  const xs = mapData.value.map((t) => t.isoX);
  const ys = mapData.value.map((t) => t.isoY);

  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);

  const width = maxX - minX;
  const height = maxY - minY;

  // 뷰포트 중앙에 오도록 (0,0 기준)
  const offsetLeft = -(minX + width / 2);
  const offsetTop = -(minY + height / 2);

  return {
    transform: `translate(${offsetLeft}px, ${offsetTop}px)`
  };
});
</script>

<style scoped>
.board-container {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #222;
  overflow: hidden;
}

.isomeric-wrapper {
  position: relative;
}

/* 타일 */
.tile {
  position: absolute;
  width: 80px;
  transform: translate(-50%, -50%);
  image-rendering: pixelated;
}

.tile img {
  width: 100%;
  display: block;
}

/* 캐릭터: 위치는 wrapper, 점프는 내부에서만 transform */
.character-wrapper {
  position: absolute;
  transform: translate(-50%, -50%);
}

.character-man {
  width: 50px;
  transform: translate(0%, 0%);
  pointer-events: none;
}

.character-man img {
  width: 100%;
}

/* 점프 효과 (위로 튀고 살짝 확대) */
.character-man.is-jumping {
  transform: translate(-50%, -50%) scale(1);
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

/* 룰렛 */
.roulette-container {
  position: absolute;
  right: 50px;
  bottom: 50px;
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
  left: 65%;
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
