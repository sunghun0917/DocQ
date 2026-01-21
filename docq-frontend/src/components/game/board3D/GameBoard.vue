<!-- src/components/game/board3d/Board.vue -->
<template>
  <div class="board-container">
    <!-- 3D 캔버스 초기화: 그림자(shadows) 활성화, 투명 배경(alpha) -->
    <TresCanvas shadows alpha clear-color="#222222">
      <!--
        OrthographicCamera (직교 카메라):
        원근감이 없는 쿼터뷰 시점을 제공하여 보드게임판처럼 보이게 함
        zoom: 확대 배율, position: 카메라 위치
      -->
      <TresOrthographicCamera
        :position="[10, 20, 10]"
        :look-at="[0, 0, 0]"
        :zoom="0.2"
        :near="-100"
        :far="100"
      />

      <!-- 개발용 마우스 컨트롤 (줌/회전 가능) -->
      <OrbitControls :enableZoom="true" />

      <!-- 조명 설정: 전체적으로 밝게(Ambient) + 그림자용(Directional) -->
      <TresAmbientLight :intensity="1.2" />
      <TresDirectionalLight
        :position="[10, 15, 10]"
        :intensity="2"
        cast-shadow
      />

      <!--
        맵 타일 렌더링:
        v-for를 사용해 grid 배열의 모든 타일을 3D로 그림
        type 0: 빈칸, 1: 길, 2: 시작/끝
      -->
      <Tile3D
        v-for="tile in grid"
        :key="`${tile.x}-${tile.y}`"
        :x="tile.x"
        :y="tile.y"
        :type="tile.type"
      />

      <!--
        플레이어 캐릭터 렌더링:
        Suspense 컴포넌트는 비동기 모델 로딩(FBX)을 기다려줌
      -->
      <Suspense>
        <GameCharacter
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

    <!-- UI: 현재 누구 턴인지 표시 -->
    <div v-if="gameStarted" class="turn-info">
      <h2>Current Turn: Player {{ currentTurn + 1 }}</h2>
    </div>

    <!-- UI: 게임 시작 전 인원수 선택 화면 -->
    <div v-if="!gameStarted" class="start-screen">
      <h1>Select Player Count</h1>
      <div class="buttons">
        <!-- 인원수 버튼 (2~4명) -->
        <button @click="startGame(2)">2 Players</button>
        <button @click="startGame(3)">3 Players</button>
        <button @click="startGame(4)">4 Players</button>
      </div>
    </div>

    <!-- UI: 룰렛 컴포넌트 -->
    <GameRoulette
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

// [컴포넌트 Import]
// 주의: 파일명을 GameCharacter.vue 등으로 바꿨다면 경로 수정 필요
import Tile3D from '@/components/game/board3D/Tile3D.vue'
import GameCharacter from '@/components/game/board3D/GameCharacter.vue'
import GameRoulette from '@/components/game/board3D/GameRoulette.vue'

/* ========== [TS] 인터페이스 정의 (TypeScript 빨간 줄 해결용) ========== */

// 플레이어 정보 타입
interface Player {
  id: number           // 플레이어 고유 번호 (0, 1, 2...)
  currentIndex: number // 현재 위치한 경로상의 인덱스 (0 = 시작점)
  color: string        // 캐릭터 색상 (Hex 코드)
}

// 그리드 타일 정보 타입
interface GridItem {
  x: number     // 그리드 X 좌표
  y: number     // 그리드 Y 좌표
  type: number  // 0: 빈칸, 1: 일반 길, 2: 특수(시작/끝)
}

// 경로 좌표 타입 (X, Y 쌍)
interface PathCoord {
  x: number
  y: number
}

// 이동 방향 벡터 타입
interface Dir {
  dx: number
  dy: number
}

/* ========== 게임 설정 상수 ========== */
const GRID_SIZE = 12       // 보드판 크기 (12x12)
const PATH_LENGTH = 31     // 생성할 경로의 총 길이 (칸 수)

/* ========== 그리드 초기화 ========== */
// 12x12 격자를 모두 0(빈칸)으로 초기화
const grid = ref<GridItem[]>([])

for (let x = 0; x < GRID_SIZE; x++) {
  for (let y = 0; y < GRID_SIZE; y++) {
    grid.value.push({ x, y, type: 0 })
  }
}

/* ========== 랜덤 경로 생성 로직 (DFS 알고리즘) ========== */
const pathCoords = ref<PathCoord[]>([]) // 실제 생성된 경로 좌표들을 저장할 배열

/**
 * 랜덤 경로 생성 함수 (DFS 사용)
 * (11,11)에서 시작하여 (0,0)까지 이어지는 랜덤한 길을 만듭니다.
 *
 * @param gridSize 그리드 크기
 * @param targetLength 목표 경로 길이
 * @returns 생성된 경로 좌표 배열 (실패 시 null)
 */
function generateRandomPath(gridSize: number, targetLength: number): PathCoord[] | null {
  const start = { x: 11, y: 11 } // 시작점
  const end = { x: 0, y: 0 }     // 도착점

  // 상하좌우 이동 방향
  const dirs: Dir[] = [
    { dx: 1, dy: 0 }, { dx: -1, dy: 0 },
    { dx: 0, dy: 1 }, { dx: 0, dy: -1 },
  ]

  // 재귀적 DFS 함수
  function dfs(current: PathCoord, path: PathCoord[], visited: Set<string>): PathCoord[] | null {
    // 1. 기저 조건: 도착점에 도달했을 때
    if (current.x === end.x && current.y === end.y) {
      if (path.length === targetLength) return path // 목표 길이와 딱 맞으면 성공!
      else return null
    }

    // 2. 가지치기 (Pruning): 남은 거리가 필요한 스텝 수보다 멀면 포기
    const distToEnd = Math.abs(current.x - end.x) + Math.abs(current.y - end.y)
    const remainingSteps = targetLength - path.length
    if (distToEnd > remainingSteps) return null

    // 3. 랜덤성 부여: 이동 방향을 무작위로 섞음
    const shuffledDirs = [...dirs].sort(() => Math.random() - 0.5)

    for (const dir of shuffledDirs) {
      const nx = current.x + dir.dx
      const ny = current.y + dir.dy
      const key = `${nx},${ny}`

      // 유효성 검사: 보드 범위 내 && 방문하지 않음
      if (
        nx >= 0 && nx < gridSize &&
        ny >= 0 && ny < gridSize &&
        !visited.has(key)
      ) {
        // [중요] 인접 체크: 길이 꼬여서 자기 자신과 붙는 것을 방지
        // 현재 진행방향을 제외하고 이미 방문한 타일이 주변에 1개보다 많으면 안됨
        let visitedNeighbors = 0
        for (const checkDir of dirs) {
          const cx = nx + checkDir.dx
          const cy = ny + checkDir.dy
          if (visited.has(`${cx},${cy}`)) visitedNeighbors++
        }

        if (visitedNeighbors > 1) continue

        // 경로에 추가하고 재귀 호출
        visited.add(key)
        path.push({ x: nx, y: ny })

        const result = dfs({ x: nx, y: ny }, path, visited)
        if (result) return result // 성공한 경로를 찾았으면 바로 반환

        // 실패 시 백트래킹 (경로에서 제거하고 다시 시도)
        path.pop()
        visited.delete(key)
      }
    }
    return null
  }

  // DFS 시작
  return dfs(start, [{ x: 11, y: 11 }], new Set(['11,11']))
}

// 그리드 타입 리셋 함수 (새 게임 시작 시)
function resetGridTypes() {
  grid.value.forEach((t) => (t.type = 0))
}

/* ========== 플레이어 및 게임 상태 관리 ========== */
const players = ref<Player[]>([])     // 플레이어 목록
const currentTurn = ref(0)            // 현재 턴 (0 ~ players.length-1)
const gameStarted = ref(false)        // 게임 시작 여부

// 플레이어별 색상 정의 (빨강, 초록, 파랑, 노랑)
const PLAYER_COLORS = ['#ff4444', '#44ff44', '#4444ff', '#ffff44']

/**
 * 맵을 생성하고 grid 데이터에 반영하는 함수
 */
function buildPathOnGrid() {
  resetGridTypes()
  let path: PathCoord[] | null = null

  // 유효한 경로가 나올 때까지 반복 시도
  while (!path) {
    path = generateRandomPath(GRID_SIZE, PATH_LENGTH)
  }
  pathCoords.value = path

  // 생성된 경로를 화면(grid)에 표시
  // 시작점(index 0)과 끝점(마지막)은 type 2 (노란색), 나머지는 type 1 (흰색)
  path.forEach((p, index) => {
    const tile = grid.value.find((t) => t.x === p.x && t.y === p.y)
    if (!tile) return
    tile.type = (index === 0 || index === (path?.length ?? 0) - 1) ? 2 : 1
  })
}

/**
 * 게임 시작 함수
 * @param count 선택한 플레이어 수
 */
const startGame = (count: number) => {
  buildPathOnGrid() // 맵 생성

  // 플레이어 초기화
  players.value = []
  for (let i = 0; i < count; i++) {
    players.value.push({
      id: i,
      currentIndex: 0, // 모두 시작점에서 출발
      color: PLAYER_COLORS[i % PLAYER_COLORS.length]
    })
  }

  currentTurn.value = 0
  gameStarted.value = true
}

/* [헬퍼 함수] 플레이어의 현재/다음 좌표 계산 */

// 현재 플레이어가 위치한 그리드 좌표(x,y) 반환
const playerCoords = (idx: number): PathCoord => {
  if (!players.value[idx]) return { x: 0, y: 0 }
  const pIdx = players.value[idx].currentIndex
  return pathCoords.value[pIdx] || (pathCoords.value[0] ?? { x: 0, y: 0 })
}

// 현재 플레이어가 이동 시 바라봐야 할 다음 좌표 반환 (시선 처리용)
const playerNextTarget = (idx: number): PathCoord => {
  if (!players.value[idx]) return { x: 0, y: 0 }
  const pIdx = players.value[idx].currentIndex
  // 마지막 칸이면 제자리, 아니면 다음 칸 리턴
  if (pIdx >= pathCoords.value.length - 1) {
    return pathCoords.value[pIdx] ?? { x: 0, y: 0 }
  }
  return pathCoords.value[pIdx + 1] ?? { x: 0, y: 0 }
}

/* ========== 룰렛 및 이동 로직 ========== */
const isMoving = ref(false)     // 말이 이동 중인지 (이동 중엔 룰렛 클릭 방지)
const isSpinning = ref(false)   // 룰렛이 도는 중인지
const rotation = ref(0)         // 룰렛 회전 각도 (애니메이션용)
const labelByIndex: number[] = [1, 2, 3, 4, 5, 6] // 룰렛 숫자 배열

/**
 * 룰렛 돌리기 함수
 */
const spinRoulette = () => {
  if (isMoving.value || isSpinning.value) return // 이미 동작 중이면 무시

  isSpinning.value = true

  // 1~6 랜덤값 생성
  const result = Math.floor(Math.random() * 6) + 1
  const targetIndex = labelByIndex.indexOf(result)

  // 회전 애니메이션 각도 계산
  const sectionAngle = 360 / 6
  const totalSpins = 360 * 5 // 최소 5바퀴 회전

  const currentDegrees = ((rotation.value % 360) + 360) % 360
  const extraToZero = (360 - currentDegrees) % 360
  const targetRotation = -(targetIndex * sectionAngle)

  // 실제 회전 값 적용 (Vue가 감지하여 CSS 애니메이션 발생)
  rotation.value += totalSpins + extraToZero + targetRotation

  // 회전 종료 후(3.2초 뒤) 이동 로직 실행
  setTimeout(() => {
    isSpinning.value = false
    moveStep(result)
  }, 3200)
}

/**
 * 말 이동 로직 (한 칸씩 점프)
 * @param steps 이동할 칸 수
 */
const moveStep = async (steps: number) => {
  isMoving.value = true

  const activePlayer = players.value[currentTurn.value]

    // [중요] activePlayer가 없을 경우를 대비해 예외 처리 (이게 빨간 줄 해결!)
  if (!activePlayer) {
    console.error("No active player found!")
    isMoving.value = false
    return
  }

  // 한 칸씩 차례대로 이동
  for (let i = 0; i < steps; i++) {
    // 도착점에 도달하면 멈춤
    if (activePlayer.currentIndex >= pathCoords.value.length - 1) break
    await jumpStep(activePlayer)
  }

  isMoving.value = false

  /* [규칙] 잡기(Knockback) 로직 */
  // 이동이 끝난 후, 도착한 자리에 다른 플레이어가 있는지 확인
  if (activePlayer.currentIndex > 0 && activePlayer.currentIndex < pathCoords.value.length - 1) {
    players.value.forEach((p) => {
      // 나 자신이 아니고, 같은 위치에 있는 플레이어라면?
      if (p.id !== activePlayer.id && p.currentIndex === activePlayer.currentIndex) {
        console.log(`Player ${p.id + 1} caught!`)
        p.currentIndex = 0 // 태초마을(0번 인덱스)로 강제 이동시킴
      }
    })
  }

  // 턴 넘기기
  currentTurn.value = (currentTurn.value + 1) % players.value.length
}

// 한 칸 이동 애니메이션 (Promise로 비동기 대기)
const jumpStep = (player: Player) =>
  new Promise<void>((resolve) => {
    if (player.currentIndex >= pathCoords.value.length - 1) {
      resolve()
      return
    }

    player.currentIndex++

    // 애니메이션 시간만큼 대기 (Character.vue의 이동 시간과 합을 맞춰야 함)
    setTimeout(() => {
      setTimeout(resolve, 100)
    }, 600)
  })
</script>

<style scoped>
/* 화면 전체를 가득 채우는 컨테이너 */
.board-container {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
}

/* 시작 화면(인원 선택) 오버레이 */
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

/* 현재 턴 표시 UI */
.turn-info {
  position: absolute;
  top: 10px;
  left: 10px;
  color: white;
  z-index: 10;
}

.buttons {
  display: flex;
  gap: 20px;
  margin-top: 20px;
}

/* 버튼 스타일 */
button {
  padding: 15px 30px;
  font-size: 1.2rem;
  background: white;
  color: black;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
}

button:hover {
  background: #ddd;
}
</style>
