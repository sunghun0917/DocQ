<!-- src/components/Board.vue -->
<template>
  <div class="board-container">
    <TresCanvas
      shadows
      alpha
      clear-color="#222222"
    >
      <!-- 카메라 -->
      <!-- 카메라 설정: 쿼터뷰(Orthographic) -->
      <!-- position: 카메라 위치 (멀리서 비스듬히 내려다봄) -->
      <!-- zoom: 화면 확대/축소 비율 -->
      <TresOrthographicCamera
        :position="[10, 20, 10]"
        :look-at="[0, 0, 0]"
        :zoom="0.2"
        :near="-100"
        :far="100"
      />

      <OrbitControls :enableZoom="true" />

      <!-- 조명 -->
      <TresAmbientLight :intensity="1.2" />
      <TresDirectionalLight
        :position="[10, 15, 10]"
        :intensity="2"
        cast-shadow
      />

      <!-- 3D 타일 -->
      <Tile3D
        v-for="tile in grid"
        :key="`${tile.x}-${tile.y}`"
        :x="tile.x"
        :y="tile.y"
        :type="tile.type"
      />

      <!-- 캐릭터 4명 렌더링 -->
      <!-- 캐릭터 렌더링 영역 (Suspense: 3D 모델 비동기 로딩 대기) -->
      <Suspense>
        <!-- v-for: players 배열만큼 캐릭터 생성 -->
        <Character
          v-for="(p, idx) in players"
          :key="p.id"
          :x="playerCoords(idx).x"
          :y="playerCoords(idx).y"
          :isMoving="isMoving && currentTurn === idx"
          :lookTarget="playerNextTarget(idx)"
          :color="p.color"
        />
      </Suspense>
    </TresCanvas>

    <!-- 턴 표시 UI -->
    <div v-if="gameStarted" style="position: absolute; top: 10px; left: 10px; color: white; z-index: 10;">
      <h2>Current Turn: Player {{ currentTurn + 1 }}</h2>
    </div>

    <!-- 게임 시작 전 플레이어 수 선택 UI -->
    <div v-if="!gameStarted" class="start-screen">
      <h1>Select Player Count</h1>
      <div class="buttons">
        <button @click="startGame(2)">2 Players</button>
        <button @click="startGame(3)">3 Players</button>
        <button @click="startGame(4)">4 Players</button>
      </div>
    </div>

    <Roulette
      v-if="gameStarted"
      :rotation="rotation"
      :isMoving="isMoving"
      :isSpinning="isSpinning"
      :labelByIndex="labelByIndex"
      @spin="spinRoulette"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { TresCanvas } from '@tresjs/core'
import { OrbitControls } from '@tresjs/cientos'

import Tile3D from '@/components/Tile3D.vue'
import Character from '@/components/Character.vue'
import Roulette from '@/components/Roulette.vue'

/* ===== 설정값 상수 ===== */
const GRID_SIZE = 12       // 보드판 가로/세로 크기 (12x12)
const PATH_LENGTH = 31     // 생성할 경로의 총 길이 (칸 수)

/* ===== 그리드 초기화 ===== */
// 전체 보드판의 좌표 정보를 담을 배열
const grid = ref([])

// 12x12 모든 칸을 우선 'type: 0' (빈 칸)으로 채움
for (let x = 0; x < GRID_SIZE; x++) {
  for (let y = 0; y < GRID_SIZE; y++) {
    grid.value.push({ x, y, type: 0 })
  }
}

/* ===== 랜덤 경로 생성 알고리즘 (DFS) ===== */
// 실제 게임에서 쓰일 경로의 좌표 리스트 [start, ..., end]
const pathCoords = ref([])

/*
 * 함수: 깊이 우선 탐색(DFS)으로 랜덤한 꼬불꼬불한 길 만들기
 * @param gridSize - 맵 크기
 * @param targetLength - 목표하는 길의 총 칸 수
 */
function generateRandomPath(gridSize, targetLength) {
  // 시작점과 도착점 좌표 지정
  const start = { x: 11, y: 11 } // 우하단
  const end = { x: 0, y: 0 }     // 좌상단

  // 이동 가능한 4방향 (상, 하, 좌, 우)
  const dirs = [
    { dx: 1, dy: 0 }, { dx: -1, dy: 0 },
    { dx: 0, dy: 1 }, { dx: 0, dy: -1 },
  ]

  // 재귀적 탐색 함수
  function dfs(current, path, visited) {
    // 1. 종료 조건: 도착점에 도달했을 때
    if (current.x === end.x && current.y === end.y) {
      // 정확히 목표 길이에 맞췄다면 성공(return path)
      if (path.length === targetLength) return path
      else return null // 길이가 안 맞으면 실패
    }

    // 2. 가지치기: 현재 위치에서 도착점까지의 최단 거리(Manhattan Distance) 계산
    const distToEnd = Math.abs(current.x - end.x) + Math.abs(current.y - end.y)
    const remainingSteps = targetLength - path.length // 남은 허용 걸음 수

    // 남은 걸음 수보다 최단 거리가 더 멀면, 절대 도착 불가능 -> 포기
    if (distToEnd > remainingSteps) return null

    // 3. 방향 섞기: 매번 랜덤한 방향으로 시도하기 위해 셔플
    const shuffledDirs = [...dirs].sort(() => Math.random() - 0.5)

    for (const dir of shuffledDirs) {
      const nx = current.x + dir.dx
      const ny = current.y + dir.dy
      const key = `${nx},${ny}`

      // 범위 체크 및 방문 여부 체크
      if (
        nx >= 0 && nx < gridSize &&
        ny >= 0 && ny < gridSize &&
        !visited.has(key)
      ) {
        // [중요] 길끼리 너무 다닥다닥 붙지 않게 체크 (인접한 길이 2개 이상이면 패스)
        // 이렇게 해야 "ㄹ"자 모양으로 꼬불꼬불 예쁘게 나옴
        let visitedNeighbors = 0
        for (const checkDir of dirs) {
          const cx = nx + checkDir.dx
          const cy = ny + checkDir.dy
          if (visited.has(`${cx},${cy}`)) visitedNeighbors++
        }

        if (visitedNeighbors > 1) continue

        // 길 추가
        visited.add(key)
        path.push({ x: nx, y: ny })

        // 다음 단계 탐색
        const result = dfs({ x: nx, y: ny }, path, visited)
        if (result) return result // 성공하면 결과 반환

        // 실패했으면 백트래킹 (다시 돌아나옴)
        path.pop()
        visited.delete(key)
      }
    }
    return null
  }

  // 탐색 시작 (시작점 포함)
  return dfs(start, [{ x: 11, y: 11 }], new Set(['11,11']))
}

function resetGridTypes() {
  grid.value.forEach((t) => (t.type = 0))
}

/* ===== 게임 상태 및 플레이어 관리 ===== */
const players = ref([])       // 플레이어 목록 (id, 위치, 색상 등)
const currentTurn = ref(0)    // 현재 턴인 플레이어 인덱스 (0 ~ 3)
const gameStarted = ref(false) // 게임 시작 여부 (false면 대기화면)

// 플레이어별 고유 색상 (빨, 초, 파, 노)
const PLAYER_COLORS = ['#ff4444', '#44ff44', '#4444ff', '#ffff44']

/**
 * 함수: 경로를 생성하고 grid 데이터에 type(길 모양)을 표시하는 함수
 * (type 1: 길, type 2: 시작/끝)
 */
function buildPathOnGrid() {
  resetGridTypes()
  let path = null
  // 랜덤 경로가 만들어질 때까지 계속 시도 (가끔 실패할 수 있어서 while문 사용)
  while (!path) {
    path = generateRandomPath(GRID_SIZE, PATH_LENGTH)
  }
  pathCoords.value = path

  // grid 배열 업데이트 (화면에 길 그리기)
  path.forEach((p, index) => {
    const tile = grid.value.find((t) => t.x === p.x && t.y === p.y)
    if (!tile) return
    tile.type = index === 0 || index === path.length - 1 ? 2 : 1
  })
}

/*
 * 함수: 게임 시작 버튼 클릭 시 실행
 * 선택된 인원수(count)만큼 플레이어 객체를 생성함
 */
const startGame = (count) => {
  buildPathOnGrid() // 맵 생성

  // 플레이어 배열 초기화
  players.value = []
  for (let i = 0; i < count; i++) {
    players.value.push({
      id: i,
      currentIndex: 0,                     // 시작점 인덱스 (0)
      color: PLAYER_COLORS[i % 4]          // 색상 할당
    })
  }

  currentTurn.value = 0
  gameStarted.value = true
}

/*
 * elper: 인덱스에 해당하는 플레이어의 현재 [X, Y] 좌표 반환
 * (Character 컴포넌트에 props로 넘겨주기 위함)
 */
const playerCoords = (idx) => {
  if (!players.value[idx]) return { x: 0, y: 0 }
  const pIdx = players.value[idx].currentIndex
  // 경로 데이터(pathCoords)에서 해당 인덱스의 좌표를 찾음
  return pathCoords.value[pIdx] || pathCoords.value[0]
}

/*
 * elper: 해당 플레이어가 다음으로 이동해야 할 '목적지' 좌표 반환
 * (캐릭터가 바라볼 방향 결정을 위해 사용)
 */
const playerNextTarget = (idx) => {
  if (!players.value[idx]) return { x: 0, y: 0 }
  const pIdx = players.value[idx].currentIndex
  // 마지막 칸이면 자기 자신이 타겟 (더 못 감)
  if (pIdx >= pathCoords.value.length - 1) {
    return pathCoords.value[pIdx]
  }
  // 아니면 다음 칸 리턴
  return pathCoords.value[pIdx + 1]
}

/* ===== 룰렛 & 턴 진행 로직 ===== */
const isMoving = ref(false)   // 현재 말이 움직이는 중인가? (중복 실행 방지)
const isSpinning = ref(false) // 룰렛이 돌아가는 중인가?
const rotation = ref(0)       // 룰렛의 회전 각도 (누적값)
const labelByIndex = [1, 2, 3, 4, 5, 6] // 룰렛판 숫자 배치

/*
 * 함수: 룰렛 돌리기 버튼 클릭 시 실행
 */
const spinRoulette = () => {
  if (isMoving.value || isSpinning.value) return // 이미 동작 중이면 무시

  isSpinning.value = true

  // 1. 결과값 랜덤 결정 (1~6) -> 나중에 백엔드에서 받아올 부분!
  const result = Math.floor(Math.random() * 6) + 1
  const targetIndex = labelByIndex.indexOf(result)

  // 2. 회전 각도 계산 (360도 * 5바퀴 + 목표 지점까지의 각도)
  const sectionAngle = 360 / 6
  const totalSpins = 360 * 5 // 최소 5바퀴 회전 연출

  const currentDegrees = ((rotation.value % 360) + 360) % 360
  const extraToZero = (360 - currentDegrees) % 360
  const targetRotation = -(targetIndex * sectionAngle) // 반시계 방향 고려

  // 최종 회전 각도 적용 (CSS transition이 애니메이션 처리)
  rotation.value += totalSpins + extraToZero + targetRotation

  // 3. 3.2초 뒤(애니메이션 끝난 뒤) 실제 이동 시작
  setTimeout(() => {
    isSpinning.value = false
    moveStep(result) // 이동 함수 호출
  }, 3200)
}

/*
 * 함수: 주사위 결과만큼 캐릭터 한 칸씩 이동
 * async/await를 써서 한 칸 이동이 끝나야 다음 칸으로 가도록 함
 */
const moveStep = async (steps) => {
  isMoving.value = true

  const activePlayer = players.value[currentTurn.value]

  // 한 칸씩 점프하며 이동
  for (let i = 0; i < steps; i++) {
    // 도착점에 도달했으면 즉시 중단 (더 이상 못 감)
    if (activePlayer.currentIndex >= pathCoords.value.length - 1) break

    // 점프함수 호출 (0.6초 대기)
    await jumpStep(activePlayer)
  }

  isMoving.value = false

  /*
   * [충돌 체크 & 잡기 로직]
   * 이동을 마친 후 타일에 다른 플레이어가 있는지 확인
   */
  // 시작점(0)과 도착점(끝)은 안전지대라 제외
  if (activePlayer.currentIndex > 0 && activePlayer.currentIndex < pathCoords.value.length - 1) {
    players.value.forEach((p) => {
      // 나 자신이 아니고, 내 위치랑 같은 플레이어를 발견하면
      if (p.id !== activePlayer.id && p.currentIndex === activePlayer.currentIndex) {
        console.log(`[잡기 발생] Player ${p.id + 1} -> Start 지점 복귀`)
        p.currentIndex = 0 // 상대를 시작점으로 강제 이동!
      }
    })
  }

  // 다음 사람 턴으로 넘기기 (0 -> 1 -> 2 -> 3 -> 0 ...)
  currentTurn.value = (currentTurn.value + 1) % players.value.length
}

/*
 * 함수: 실제 좌표값(currentIndex)을 1 증가시키는 함수
 * Promise를 반환하여, 애니메이션 시간만큼 기다리게 함
 */
const jumpStep = (player) =>
  new Promise((resolve) => {
    // 이미 끝이면 무시
    if (player.currentIndex >= pathCoords.value.length - 1) {
      resolve()
      return
    }

    // 좌표 1 증가 (이때 Character.vue의 watch가 반응해서 애니메이션 실행됨)
    player.currentIndex++

    // 이동 애니메이션 시간(0.5초) + 여유시간(0.1초) 대기 후 완료 신호
    setTimeout(() => {
      setTimeout(resolve, 100)
    }, 600)
  })
</script>

<style scoped>
.board-container {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
}

.start-screen {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  background: rgba(0,0,0,0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  z-index: 100;
}

.start-screen .buttons {
  display: flex;
  gap: 20px;
  margin-top: 20px;
}

.start-screen button {
  padding: 15px 30px;
  font-size: 1.2rem;
  background: white;
  color: black;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
}

.start-screen button:hover {
  background: #ddd;
}
</style>
