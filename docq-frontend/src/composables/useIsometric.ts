// src/composables/useIsometric.js

// 보드의 전체 가로/세로 칸 수 (반드시 Board.vue의 GRID_SIZE와 일치해야 함)
const GRID_SIZE = 10
// 타일 하나의 물리적 크기 (1.0이면 간격 없이 딱 붙음, 0.5 등으로 축소 가능)
const TILE_SIZE = 0.5

export function useIsometric() {

  // 전체 보드를 중앙 정렬하기 위한 중간값 계산
  // 예: 12칸이면 (11 / 2) = 5.5, 즉 (5.5, 5.5)를 (0,0,0)으로 맞춤
  const half = (GRID_SIZE - 1) / 2

  /*
   * 2D 그리드 좌표 (x, y)를 3D 등각투영(Isometric) 월드 좌표 (x, y, z)로 변환
   * @param {number} gridX - 그리드 상의 X 인덱스 (0 ~ 11)
   * @param {number} gridY - 그리드 상의 Y 인덱스 (0 ~ 11)
   * @returns {Array} [x, y, z] - Three.js 월드 좌표
   */
  const get3DPos = (gridX: number, gridY: number): [number, number, number] => {
    // (현재 인덱스 - 중간값) * 타일 크기 = 중앙 기준 상대 좌표
    const x = (gridX - half) * TILE_SIZE
    const z = (gridY - half) * TILE_SIZE
    const y = 0 // 바닥면이므로 Y는 0으로 고정
    return [x, y, z]
  }

  // 컴포넌트에서 쓸 수 있도록 함수와 상수 반환
  return { get3DPos, TILE_SIZE }
}
