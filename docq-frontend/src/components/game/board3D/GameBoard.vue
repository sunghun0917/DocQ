<!-- src/components/game/board3d/GameBoard.vue -->
<template>
  <div class="board-container">
    <TresCanvas shadows alpha clear-color="#222222">
    <TresOrthographicCamera
        :position="cameraPosition"
        :look-at="cameraTarget"
        :zoom="cameraZoom"
        :near="-100"
        :far="100"
      />

      <OrbitControls :enableZoom="true" :enabled="controlsEnabled" />

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

    <!-- Roulette (Visual Only) -->
    <GameRoulette
      v-if="isRouletteVisible"
      :rotation="rouletteRotation"
      :isMoving="isMovingGlobal"
      :isSpinning="isSpinning"
      :labelByIndex="rouletteLabels"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { TresCanvas } from '@tresjs/core'
import { OrbitControls } from '@tresjs/cientos'
import Tile3D from '@/components/game/board3D/Tile3D.vue'
import GameCharacter from '@/components/game/board3D/GameCharacter.vue'
import GameRoulette from '@/components/game/board3D/GameRoulette.vue'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomStore'
import type { Player } from '@/types/docq-api'
import { useIsometric } from '@/composables/useIsometric'

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
const { get3DPos } = useIsometric()

const cameraPosition = ref<[number, number, number]>([10, 20, 10])
const cameraTarget = ref<[number, number, number]>([0, 0, 0])
const cameraZoom = ref(0.2)
const controlsEnabled = ref(true)
const introPlayed = ref(false)

// === Grid & Path Logic (Deterministic) ===
const GRID_SIZE = 12
const PATH_LENGTH = 31

const grid = ref<GridItem[]>([])
const pathCoords = ref<PathCoord[]>([])
const rouletteLabels = ref<number[]>([1, 2, 3, 4])

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

function applySpecialTiles(mapEvents: Record<string, string> | null | undefined) {
    const path = pathCoords.value
    path.forEach((p, idx) => {
        const tile = grid.value.find(t => t.x === p.x && t.y === p.y)
        if (tile) tile.type = (idx === 0 || idx === path.length - 1) ? 2 : 1
    })

    if (!mapEvents) return
    Object.keys(mapEvents).forEach((key) => {
        const index = Number(key)
        if (Number.isNaN(index)) return
        const coord = path[index]
        if (!coord) return
        const tile = grid.value.find(t => t.x === coord.x && t.y === coord.y)
        if (tile && tile.type === 1) {
            tile.type = 3
        }
    })
}

// === Player State ===
const players = computed(() => gameStore.gameState.players)
const currentTurnPlayer = computed(() => gameStore.currentTurnPlayer)
const mapEvents = computed(() => gameStore.gameState.mapEvents as Record<string, string> | undefined)

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

watch(
    () => gameStore.gamePhase,
    (phase) => {
        if (phase === 'WAITING') {
            introPlayed.value = false
        }
        if (phase === 'STARTING' && !introPlayed.value && players.value.length > 0) {
            introPlayed.value = true
            setTimeout(() => {
                runIntroCamera()
            }, 2000)
        }
    },
    { immediate: true }
)

watch(mapEvents, (events) => {
    applySpecialTiles(events)
}, { immediate: true })


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

function lerp(a: number, b: number, t: number) {
    return a + (b - a) * t
}

function easeInOut(t: number) {
    return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t
}

function animate(duration: number, update: (t: number) => void) {
    return new Promise<void>(resolve => {
        const start = performance.now()
        const step = (now: number) => {
            const t = Math.min(1, (now - start) / duration)
            update(t)
            if (t < 1) {
                requestAnimationFrame(step)
            } else {
                resolve()
            }
        }
        requestAnimationFrame(step)
    })
}

async function runIntroCamera() {
    controlsEnabled.value = false

    const center: [number, number, number] = [0, 0, 0]
    cameraTarget.value = center

    const orbitRadius = 22
    const orbitHeight = 20
    await animate(2800, (t) => {
        const p = easeInOut(t)
        const angle = p * Math.PI * 2
        cameraPosition.value = [
            center[0] + Math.cos(angle) * orbitRadius,
            orbitHeight,
            center[2] + Math.sin(angle) * orbitRadius
        ]
        cameraZoom.value = lerp(0.18, 0.22, p)
    })

    const focusTargetPlayer = currentTurnPlayer.value || players.value[0]
    if (focusTargetPlayer) {
        const coords = getPlayerDisplayCoords(focusTargetPlayer)
        const target3D = get3DPos(coords.x, coords.y)
        const startPos = cameraPosition.value.slice() as [number, number, number]
        const startZoom = cameraZoom.value
        const focusPos: [number, number, number] = [target3D[0] + 6, target3D[1] + 10, target3D[2] + 6]

        await animate(1400, (t) => {
            const p = easeInOut(t)
            cameraTarget.value = [
                lerp(center[0], target3D[0], p),
                lerp(center[1], target3D[1], p),
                lerp(center[2], target3D[2], p)
            ]
            cameraPosition.value = [
                lerp(startPos[0], focusPos[0], p),
                lerp(startPos[1], focusPos[1], p),
                lerp(startPos[2], focusPos[2], p)
            ]
            cameraZoom.value = lerp(startZoom, 0.3, p)
        })
    }

    const returnStart = cameraPosition.value.slice() as [number, number, number]
    const returnZoom = cameraZoom.value
    const defaultPos: [number, number, number] = [10, 20, 10]
    await animate(1400, (t) => {
        const p = easeInOut(t)
        cameraTarget.value = [
            lerp(cameraTarget.value[0], center[0], p),
            lerp(cameraTarget.value[1], center[1], p),
            lerp(cameraTarget.value[2], center[2], p)
        ]
        cameraPosition.value = [
            lerp(returnStart[0], defaultPos[0], p),
            lerp(returnStart[1], defaultPos[1], p),
            lerp(returnStart[2], defaultPos[2], p)
        ]
        cameraZoom.value = lerp(returnZoom, 0.2, p)
    })

    controlsEnabled.value = true
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
        rouletteLabels.value = getRouletteLabels(rouletteStep.value, move.userId)
        isRouletteVisible.value = true
        await spinRouletteAnimation(rouletteStep.value, rouletteLabels.value)
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

async function spinRouletteAnimation(resultValue: number, labels: number[]) {
    isSpinning.value = true
    await nextTick()
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))

    const totalSpins = 360 * 3
    const targetIndex = labels.indexOf(resultValue)
    const safeIndex = targetIndex >= 0 ? targetIndex : 0
    const sectionAngle = 360 / Math.max(labels.length, 1)
    const targetRotation = -((safeIndex * sectionAngle) + (sectionAngle / 2))
    const current = ((rouletteRotation.value % 360) + 360) % 360
    let delta = targetRotation - current
    if (delta < -180) delta += 360
    if (delta > 180) delta -= 360

    rouletteRotation.value += totalSpins + delta

    return new Promise<void>(resolve => {
        setTimeout(() => {
            isSpinning.value = false
            resolve()
        }, 3000)
    })
}

function getRouletteLabels(resultValue: number, winnerUserId: string) {
    if (resultValue < 0) {
        return [-2, -1, 1, 2]
    }
    const isTurnWinner = winnerUserId === gameStore.gameState.currentTurnUserId
    return isTurnWinner ? [1, 2, 3, 4] : [-2, -1, 1, 2]
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
</style>
