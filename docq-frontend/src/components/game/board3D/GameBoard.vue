<!-- src/components/game/board3d/GameBoard.vue -->
<template>
  <div class="board-container">
    <TresCanvas shadows alpha clear-color="#222222">
      <TresOrthographicCamera
        :position="[10, 20, 10]"
        :look-at="[0, 0, 0]"
        :zoom="0.2"
        :near="-100"
        :far="100"
      />

      <OrbitControls :enableZoom="true" />

      <TresAmbientLight :intensity="1.2" />
      <TresDirectionalLight
        :position="[10, 15, 10]"
        :intensity="2"
        cast-shadow
      />

      <!-- Grid Tiles -->
      <Tile3D
        v-for="tile in grid"
        :key="`${tile.x}-${tile.y}`"
        :x="tile.x"
        :y="tile.y"
        :type="tile.type"
      />

      <!-- Players -->
      <Suspense>
        <GameCharacter
          v-for="p in players"
          :key="p.userId"
          :x="getPlayerDisplayCoords(p).x"
          :y="getPlayerDisplayCoords(p).y"
          :isMoving="isPlayerMoving(p.userId)"
          :lookTarget="getPlayerFaceTarget(p)"
          :color="getPlayerColor(p.userId)"
        />
      </Suspense>
    </TresCanvas>

    <!-- Turn Info -->
    <div v-if="currentTurnPlayer" class="turn-info">
        <h2 :style="{ color: getPlayerColor(currentTurnPlayer.userId) }">
            Current Turn: {{ currentTurnPlayer.nickname }}
        </h2>
    </div>

    <!-- Roulette (Visual Only) -->
    <GameRoulette
      v-if="isRouletteVisible"
      :rotation="rouletteRotation"
      :isMoving="isMovingGlobal"
      :isSpinning="isSpinning"
      :labelByIndex="[1,2,3,4,5,6]" 
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { TresCanvas } from '@tresjs/core'
import { OrbitControls } from '@tresjs/cientos'
import Tile3D from '@/components/game/board3D/Tile3D.vue'
import GameCharacter from '@/components/game/board3D/GameCharacter.vue'
import GameRoulette from '@/components/game/board3D/GameRoulette.vue'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomStore'
import type { Player } from '@/types/docq-api'

// === Imports & Types ===
interface GridItem {
  x: number; y: number; type: number;
}
interface PathCoord {
  x: number; y: number;
}
interface AnimatingPlayer {
    userId: string
    currentPathIndex: number // 애니메이션 중의 임시 위치
} 

const gameStore = useGameStore()
const roomStore = useRoomStore()

// === Grid & Path Logic (Deterministic) ===
const GRID_SIZE = 12
const PATH_LENGTH = 31

const grid = ref<GridItem[]>([])
const pathCoords = ref<PathCoord[]>([])

// 고정된 시드(혹은 단순화된 로직)로 항상 같은 맵 생성
function generateDeterministicPath(): PathCoord[] {
    // 'ㄹ'자 혹은 나선형으로 단순 생성 (MVP)
    // 실제로는 복잡한 알고리즘이 필요할 수 있으나 동기화 이슈 방지를 위해 고정함
    const path: PathCoord[] = []
    let cx = 11, cy = 11
    // 단순화된 고정 경로 예시
    const moves = [
        {dx:0, dy:-1, c:5}, {dx:-1, dy:0, c:5}, 
        {dx:0, dy:1, c:4}, {dx:1, dy:0, c:4},
        {dx:0, dy:-1, c:3}, {dx:-1, dy:0, c:3},
    ] // 총 24칸 + ... 
    
    // (MVP) 더 확실한 고정 경로 (Start: 11,11 -> End: 0,0)
    // 11,11 -> 11,6 -> 6,6 -> 6,1 -> 1,1 -> 1,0 -> 0,0
    path.push({x:11, y:11})
    for(let i=0; i<5; i++) path.push({x:11, y:10-i}) // up
    for(let i=0; i<5; i++) path.push({x:10-i, y:6}) // left
    for(let i=0; i<5; i++) path.push({x:6, y:5-i})  // up
    for(let i=0; i<5; i++) path.push({x:5-i, y:1})  // left
    for(let i=0; i<5; i++) path.push({x:1, y:2+i})  // down (twist)
    // ... 대충 채움 (31칸 맞추기)
    while(path.length < PATH_LENGTH) {
        const last = path[path.length-1]
        if (!last) break // Safety check
        
        if(last.x > 0) path.push({x:last.x-1, y:last.y})
        else if(last.y > 0) path.push({x:last.x, y:last.y-1})
        else break
    }
    return path.slice(0, PATH_LENGTH)
}

function initGrid() {
    grid.value = []
    for (let x = 0; x < GRID_SIZE; x++) {
        for (let y = 0; y < GRID_SIZE; y++) {
            grid.value.push({ x, y, type: 0 })
        }
    }
    const path = generateDeterministicPath()
    pathCoords.value = path
    
    path.forEach((p, idx) => {
        const tile = grid.value.find(t => t.x === p.x && t.y === p.y)
        if (tile) tile.type = (idx === 0 || idx === path.length - 1) ? 2 : 1
    })
}

initGrid()

// === Player State ===
const players = computed(() => gameStore.gameState.players)
const currentTurnPlayer = computed(() => gameStore.currentTurnPlayer)

// 애니메이션 상태 관리
const animatingPlayers = ref<Record<string, number>>({}) // userId -> currentPathIndex

// 초기화 시 서버 위치로 동기화
watch(() => players.value, (newPlayers) => {
    newPlayers.forEach(p => {
        if (animatingPlayers.value[p.userId] === undefined) {
            animatingPlayers.value[p.userId] = p.position 
        } else {
             // 애니메이션 중이 아닐 때만 강제 동기화 (오차 수정)
             if (!isMovingGlobal.value) {
                 animatingPlayers.value[p.userId] = p.position
             }
        }
    })
}, { immediate: true, deep: true })


// === Helpers ===
const PLAYER_COLORS = ['#ff4444', '#44ff44', '#4444ff', '#ffff44']

function getPlayerColor(userId: string) {
    // userId 해시 또는 순서 기반 색상
    const idx = gameStore.gameState.players.findIndex(p => p.userId === userId)
    return PLAYER_COLORS[idx % 4] || '#ffffff'
}

function getPlayerDisplayCoords(p: Player) {
    const idx = animatingPlayers.value[p.userId] ?? p.position
    return pathCoords.value[idx] || { x: 0, y: 0 }
}

function getPlayerFaceTarget(p: Player) {
    const idx = animatingPlayers.value[p.userId] ?? p.position
    // 다음 칸을 바라봄
    if (idx < pathCoords.value.length - 1) {
        return pathCoords.value[idx+1] || pathCoords.value[idx] || { x: 0, y: 0 }
    }
    return pathCoords.value[idx] || { x: 0, y: 0 }
}

function isPlayerMoving(userId: string) {
    return isMovingGlobal.value && gameStore.currentMove?.userId === userId
}

// === Animation Logic ===
const isMovingGlobal = ref(false)
const isSpinning = ref(false)
const rouletteRotation = ref(0)
const isRouletteVisible = ref(false) // 룰렛 HUD 표시 여부

// gameStore.currentMove 감지하여 애니메이션 실행
watch(() => gameStore.currentMove, async (move) => {
    if (!move) return

    isMovingGlobal.value = true
    
    // 1. 룰렛 돌리기 (있다면)
    const rouletteStep = move.moveProcess.find((item: any) => item.type === 'ROULETTE')
    if (rouletteStep && rouletteStep.value) {
        isRouletteVisible.value = true
        await spinRouletteAnimation(rouletteStep.value)
        isRouletteVisible.value = false
    }

    // 2. 이동 애니메이션
    const start = move.startPosition
    const end = move.endPosition
    const userId = move.userId

    // 한 칸씩 이동
    if (end > start) {
        for (let i = start + 1; i <= end; i++) {
            animatingPlayers.value[userId] = i
            await sleep(500) // 캐릭터 점프 시간
        }
    } else if (end < start) {
        // 뒤로 가기 or 태초마을 (순간이동 처리 or 역방향)
        // MVP: 일단 순간이동/빠른이동
        animatingPlayers.value[userId] = end
    }

    isMovingGlobal.value = false
    // 애니메이션 끝난 후 스토어 상태가 완료된 것과 일치하는지 확인 (자동으로 watch가 맞춰줌)
})

function spinRouletteAnimation(resultValue: number) {
    return new Promise<void>(resolve => {
        isSpinning.value = true
        // ... 회전 계산 로직 (기존 코드 재사용/간소화)
        const totalSpins = 360 * 3
        // resultValue (1~6)에 매핑되는 각도
        // labelByIndex = [1,2,3,4,5,6] -> index = resultValue - 1
        const targetIndex = resultValue - 1
        const sectionAngle = 60
        const targetRotation = -(targetIndex * sectionAngle)
        
        rouletteRotation.value += totalSpins + targetRotation // 누적
        
        setTimeout(() => {
            isSpinning.value = false
            resolve()
        }, 3000)
    })
}

function sleep(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms))
}

onMounted(() => {
    // 
})
</script>

<style scoped>
.board-container {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
}
.turn-info {
    position: absolute;
    top: 20px;
    left: 20px;
    background: rgba(0,0,0,0.5);
    padding: 10px 20px;
    border-radius: 15px;
    color: white;
    z-index: 10;
}
</style>
