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
        <template #fallback>
          <!-- Loading fallback (optional) -->
          <TresMesh :position="[0, 0.5, 0]" :scale="[0.3, 0.3, 0.3]">
            <TresBoxGeometry />
            <TresMeshStandardMaterial color="yellow" />
          </TresMesh>
        </template>
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

const gameStore = useGameStore()
const roomStore = useRoomStore()

// === Grid & Path Logic (Deterministic) ===
const GRID_SIZE = 12
const PATH_LENGTH = 31

const grid = ref<GridItem[]>([])
const pathCoords = ref<PathCoord[]>([])

// 고정된 경로 생성 (동기화 이슈 방지)
function generateDeterministicPath(): PathCoord[] {
    const path: PathCoord[] = []
    path.push({x:11, y:11})
    for(let i=0; i<5; i++) path.push({x:11, y:10-i})
    for(let i=0; i<5; i++) path.push({x:10-i, y:6})
    for(let i=0; i<5; i++) path.push({x:6, y:5-i})
    for(let i=0; i<5; i++) path.push({x:5-i, y:1})
    for(let i=0; i<5; i++) path.push({x:1, y:2+i})
    
    while(path.length < PATH_LENGTH) {
        const last = path[path.length-1]
        if (!last) break
        
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
const animatingPlayers = ref<Record<string, number>>({})

// 초기화 시 서버 위치로 동기화
watch(() => players.value, (newPlayers) => {
    newPlayers.forEach(p => {
        if (animatingPlayers.value[p.userId] === undefined) {
            animatingPlayers.value[p.userId] = p.position 
        } else {
             if (!isMovingGlobal.value) {
                 animatingPlayers.value[p.userId] = p.position
             }
        }
    })
}, { immediate: true, deep: true })

// === Helpers ===
const PLAYER_COLORS = ['#ff4444', '#44ff44', '#4444ff', '#ffff44']

function getPlayerColor(userId: string) {
    const idx = gameStore.gameState.players.findIndex(p => p.userId === userId)
    return PLAYER_COLORS[idx % 4] || '#ffffff'
}

function getPlayerDisplayCoords(p: Player) {
    const idx = animatingPlayers.value[p.userId] ?? p.position
    return pathCoords.value[idx] || { x: 0, y: 0 }
}

function getPlayerFaceTarget(p: Player) {
    const idx = animatingPlayers.value[p.userId] ?? p.position
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
const isRouletteVisible = ref(false)

// gameStore.currentMove 감지하여 애니메이션 실행
watch(() => gameStore.currentMove, async (move) => {
    if (!move) return

    isMovingGlobal.value = true
    
    // 1. 룰렛 돌리기
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

    if (end > start) {
        for (let i = start + 1; i <= end; i++) {
            animatingPlayers.value[userId] = i
            await sleep(500)
        }
    } else if (end < start) {
        animatingPlayers.value[userId] = end
    }

    isMovingGlobal.value = false
})

function spinRouletteAnimation(resultValue: number) {
    return new Promise<void>(resolve => {
        isSpinning.value = true
        const totalSpins = 360 * 3
        const targetIndex = resultValue - 1
        const sectionAngle = 60
        const targetRotation = -(targetIndex * sectionAngle)
        
        rouletteRotation.value += totalSpins + targetRotation
        
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
    // Initialization if needed
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
