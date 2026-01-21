<!-- src/components/Tile3D.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import { useIsometric } from '@/composables/useIsometric.js'
import { BoxGeometry, EdgesGeometry } from 'three'

const props = defineProps({
  x: { type: Number, required: true },    // 타일의 그리드 X 좌표
  y: { type: Number, required: true },    // 타일의 그리드 Y 좌표
  type: { type: Number, required: true }, // 타일 타입 (0: 빈 공간, 1: 길, 2: 시작/도착점)
})

// 3D 좌표 변환 훅 및 타일 크기 상수 가져오기
const { get3DPos, TILE_SIZE } = useIsometric()

// 타입별 타일 색상 매핑
const COLORS = {
    0: '#888',     // 회색 (빈 공간)
    1: '#fff',     // 흰색 (경로)
    2: '#ffd54f',  // 노란색 (특수 지점)
}

// 현재 타일 타입에 맞는 색상 계산
const color = computed(() => COLORS[props.type] || '#fff')

/* 현재 타일의 3D 월드 좌표 계산 (computed로 반응형 처리) */
const pos = computed(() => get3DPos(props.x, props.y))

/* 테두리(Edge) 지오메트리 미리 생성 (성능 최적화) */
// BoxGeometry(가로, 세로, 높이) -> 타일 크기보다 약간 작게(0.95) 해서 간격 둠
const boxGeo = new BoxGeometry(TILE_SIZE * 0.95, TILE_SIZE * 0.95, 0.5)
const edgesGeo = new EdgesGeometry(boxGeo) // 모서리 선 따기
</script>

<template>
  <TresMesh
    :position="pos"
    :rotation-x="-Math.PI / 2"
    receive-shadow
    :cast-shadow="type !== 0"
  >
    <!-- type이 0이 아니면(길이면) 입체 박스 생성 -->
    <TresBoxGeometry
      v-if="type !== 0"
      :args="[TILE_SIZE * 0.95, TILE_SIZE * 0.95, 0.5]"
    />
    <!-- type이 0이면(배경이면) 얇은 평면 생성 (혹은 아예 렌더링 안 할 수도 있음) -->
    <TresPlaneGeometry
      v-else
      :args="[TILE_SIZE * 0.95, TILE_SIZE * 0.95]"
    />

    <!-- 재질 적용: 색상만 사용 (Color) -->
    <TresMeshStandardMaterial :color="color" />

    <!-- 테두리 선 그리기 (길인 경우에만) -->
    <TresLineSegments v-if="type !== 0" :geometry="edgesGeo">
      <TresLineBasicMaterial color="black" />
    </TresLineSegments>
  </TresMesh>
</template>
