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
      <Suspense v-for="(p, idx) in players" :key="p.id">
        <GameCharacter
          :x="playerCoords(idx).x"
          :y="playerCoords(idx).y"
          :isMoving="isMoving && currentTurn === idx"
          :lookTarget="playerNextTarget(idx)"
          :color="p.color"
        />
        <template #fallback>
            <!-- 로딩 중일 때 보일 노란 큐브 -->
             <TresMesh :position="[playerCoords(idx).x, 0.5, playerCoords(idx).y]" :scale="[0.3, 0.3, 0.3]">
                <TresBoxGeometry />
                <TresMeshStandardMaterial color="yellow" />
             </TresMesh>
        </template>
      </Suspense>
    </TresCanvas>

    <!-- UI: 현재 누구 턴인지 표시 -->
    <div v-if="gameStarted" class="turn-info">
      <h2>Current Turn: Player {{ currentTurn + 1 }}</h2>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue' // onMounted 추가
import { TresCanvas } from '@tresjs/core'
import { OrbitControls } from '@tresjs/cientos'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomtStore'

// [컴포넌트 Import]
import Tile3D from '@/components/game/board3D/Tile3D.vue'
import GameCharacter from '@/components/game/board3D/GameCharacter.vue'
// import GameRoulette from '@/components/game/board3D/GameRoulette.vue' // Removed as it's likely an overlay now

const gameStore = useGameStore()
const roomStore = useRoomStore()
const myUserId = computed(() => roomStore.myUser?.id || '')

// 좌표 인터페이스
interface PathCoord {
  x: number
  y: number
}

// 플레이어 인터페이스
interface Player {
  id: string
  currentIndex: number
  color: string
}

/* ========== 게임 설정 상수 ========== */
const GRID_SIZE = 10
const PATH_LENGTH = 20

/* ========== 상태 변수 ========== */
// 10x10 격자 데이터 생성
const grid = ref(
  Array.from({ length: GRID_SIZE * GRID_SIZE }, (_, i) => ({
    x: i % GRID_SIZE,
    y: Math.floor(i / GRID_SIZE),
    type: 0 // 0: empty, 1: path, 2: start/end
  }))
)

// 생성된 길(Path)의 좌표 배열
const pathCoords = ref<PathCoord[]>([])

/**
 * 격자 타입 초기화 (모두 0으로)
 */
function resetGridTypes() {
  grid.value.forEach((t) => (t.type = 0))
}

/**
 * 랜덤 경로 생성 알고리즘 (DFS/Random Walk)
 */
function generateRandomPath(size: number, length: number): PathCoord[] | null {
  const startX = 0
  const startY = 0
  const path: PathCoord[] = [{ x: startX, y: startY }]
  const visited = new Set([`0,0`])

  let currentX = startX
  let currentY = startY

  // 4방향 (상, 하, 좌, 우)
  const directions = [
    { dx: 0, dy: 1 },
    { dx: 1, dy: 0 },
    { dx: 0, dy: -1 },
    { dx: -1, dy: 0 }
  ]

  for (let i = 0; i < length; i++) {
    // 이동 가능한 후보 찾기
    const candidates = directions
      .map(d => ({ x: currentX + d.dx, y: currentY + d.dy }))
      .filter(pos => 
        pos.x >= 0 && pos.x < size &&
        pos.y >= 0 && pos.y < size &&
        !visited.has(`${pos.x},${pos.y}`)
      )

    if (candidates.length === 0) return null // 막다름 -> 실패

    // 랜덤 선택
    const next = candidates[Math.floor(Math.random() * candidates.length)]!
    path.push(next)
    visited.add(`${next.x},${next.y}`)
    currentX = next.x
    currentY = next.y
  }

  return path
}

/* ========== 플레이어 좌표 헬퍼 ========== */
// 플레이어의 현재 grid index를 3D 좌표로 변환
const playerCoords = (index: number) => {
  const p = players.value[index]
  if (!p || !pathCoords.value.length) return { x: 0, y: 0 }
  
  const coord = pathCoords.value[p.currentIndex] ?? pathCoords.value[0]
  if (!coord) return { x: 0, y: 0 } // Safety check

  // Tile 크기가 1x1이라고 가정, 높이 등을 고려해 조정 가능
  return { x: coord.x, y: coord.y }
}

// 플레이어가 바라볼 다음 좌표 (LookAt)
const playerNextTarget = (index: number): { x: number, y: number } => {
  const p = players.value[index]
  if (!p || !pathCoords.value.length) return { x: 0, y: 0 }

  const nextIndex = Math.min(p.currentIndex + 1, pathCoords.value.length - 1)
  const coord = pathCoords.value[nextIndex]
  return { x: coord.x, y: coord.y }
}

// ...

/* ========== 플레이어 및 게임 상태 관리 ========== */
const players = ref<Player[]>([])     // 플레이어 목록
const currentTurn = ref(0)            // 현재 턴 (0 ~ players.length-1)
// const gameStarted = ref(false)     // (기존)
const gameStarted = computed(() => !gameStore.isGameEnded) // Store 연동

// 플레이어별 색상 정의 (빨강, 초록, 파랑, 노랑)
const PLAYER_COLORS = ['#ff4444', '#44ff44', '#4444ff', '#ffff44']

/**
 * 맵을 생성하고 grid 데이터에 반영하는 함수
 */
function buildPathOnGrid() {
  resetGridTypes() // Assuming this function exists elsewhere
  let path: PathCoord[] | null = null

  // 유효한 경로가 나올 때까지 반복 시도
  while (!path) {
    path = generateRandomPath(GRID_SIZE, PATH_LENGTH) // Assuming these exist elsewhere
  }
  pathCoords.value = path // Assuming pathCoords is defined

  // 생성된 경로를 화면(grid)에 표시
  // 시작점(index 0)과 끝점(마지막)은 type 2 (노란색), 나머지는 type 1 (흰색)
  path.forEach((p, index) => {
    const tile = grid.value.find((t) => t.x === p.x && t.y === p.y) // Assuming grid is defined
    if (!tile) return
    tile.type = (index === 0 || index === (path?.length ?? 0) - 1) ? 2 : 1
  })
}

/**
 * 게임 초기화 (자동 시작)
 */
const initializeGame = () => {
  console.log('InitializeGame called')
  buildPathOnGrid() // 맵 생성

  // 플레이어 초기화 (RoomStore 기반)
  const users = roomStore.users.length > 0 ? roomStore.users : [{id: '1'}, {id: '2'}]
  console.log('Room Users:', roomStore.users)
  console.log('Using Users:', users)
  
  players.value = users.map((u, i) => ({
    id: u.id, 
    currentIndex: 0,
    color: PLAYER_COLORS[i % PLAYER_COLORS.length]
  })) as any // Interface mismatch fix later
  
  console.log('Players initialized:', players.value)
  currentTurn.value = 0
}

onMounted(() => {
    initializeGame()
})

// ...

/* ========== 이동 로직 (룰렛 결과 반영) ========== */
const isMoving = ref(false)     

// [NEW] 룰렛 결과 감지 Watcher
// RouletteOverlay에서 결과가 세팅되면 캐릭터 이동 시작
watch(
  () => gameStore.rouletteResult,
  (result) => {
    if (result !== null && !isMoving.value) {
      console.log('Roulette Result received:', result)
      moveStep(result)
    }
  }
)

/**
 * 말 이동 로직 (한 칸씩 점프) -> 종료 후 Store에 알림
 * @param steps 이동할 칸 수
 */
const moveStep = async (steps: number) => {
  isMoving.value = true

  // 이동할 플레이어 찾기 (rouletteTargetId 기반)
  const targetId = gameStore.rouletteTargetId
  const activePlayer = players.value.find(p => p.id === Number(targetId) || p.id === targetId) // ID 타입 주의 (string vs number)
  // 임시: players.value의 id가 지금 number(idx)로 되어있어서 매핑 필요할 수 있음. 
  // initializeGame에서 roomStore.user.id를 매핑하지 않고 idx(0,1..)를 쓰고 있다면 문제 발생.
  // 이 부분은 initializeGame 로직을 확인해야 함. (현재 id: i 로 되어있음)
  
  // [HOTFIX] 현재 GameBoard init 로직상 id는 0,1,2 인덱스임.
  // roomStore.users[id] === targetId 인 플레이어를 찾아야 함.
  
  // 실제 타겟 플레이어 객체 찾기
  let targetPlayer: Player | undefined
  roomStore.users.forEach((u, idx) => {
      if (u.id === targetId) {
          targetPlayer = players.value[idx]
      }
  })

  if (!targetPlayer) {
    console.error("No active player found for ID:", targetId)
    isMoving.value = false
    gameStore.handleRouletteComplete() // 에러 나더라도 넘어가야 함
    return
  }

  // 한 칸씩 차례대로 이동
  for (let i = 0; i < steps; i++) {
    if (targetPlayer.currentIndex >= pathCoords.value.length - 1) break
    await jumpStep(targetPlayer)
  }

  isMoving.value = false

  /* [규칙] 잡기(Knockback) 로직 */
  if (targetPlayer.currentIndex > 0 && targetPlayer.currentIndex < pathCoords.value.length - 1) {
    players.value.forEach((p) => {
      if (p.id !== targetPlayer?.id && p.currentIndex === targetPlayer.currentIndex) {
        console.log(`Player ${p.id} caught!`)
        p.currentIndex = 0 
      }
    })
  }

  // 이동 완료 -> Store에 알림 (다음 퀴즈로)
  gameStore.handleRouletteComplete()
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


</style>
