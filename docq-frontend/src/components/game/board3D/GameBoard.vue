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
        <template #fallback>
          <!-- Loading fallback (optional) -->
          <TresMesh :position="[0, 0.5, 0]" :scale="[0.3, 0.3, 0.3]">
            <TresBoxGeometry />
            <TresMeshStandardMaterial color="yellow" />
          </TresMesh>
        </template>
      </Suspense>
    </TresCanvas>

    <div v-if="actionNotice" class="action-notice">
      <div class="action-notice-card">
        {{ actionNotice }}
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { TresCanvas } from '@tresjs/core'
import { OrbitControls } from '@tresjs/cientos'
import Tile3D from '@/components/game/board3D/Tile3D.vue'
import GameCharacter from '@/components/game/board3D/GameCharacter.vue'
import { useGameStore } from '@/stores/gameStore'
import { useRoomStore } from '@/stores/roomStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import type { PlayerInfo } from '@/types/docq-api'
import { useIsometric } from '@/composables/useIsometric'

// === Imports & Types ===
interface GridItem {
  x: number; y: number; type: number;
}
interface PathCoord {
  x: number; y: number;
}

const emit = defineEmits<{
  (e: 'movement-change', moving: boolean): void
}>()

const gameStore = useGameStore()
const roomStore = useRoomStore()
const wsStore = useWebSocketStore()
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
const animatingPlayers = ref<Record<string, number>>({})
const lastStablePositions = ref<Record<string, number>>({})
const isMovingGlobal = ref(false)
const actionNotice = ref<string | null>(null)
let actionNoticeTimer: number | null = null
const isMovePending = computed(() => gameStore.gamePhase === 'MOVE' && !gameStore.currentMove)

// 초기화 시 서버 위치로 동기화
watch(() => players.value, (newPlayers) => {
    if (isMovePending.value) {
        return
    }

    const movingUserId = gameStore.currentMove?.userId || null
    newPlayers.forEach(p => {
        const safePosition = p.position ?? 0
        if (movingUserId && isMovingGlobal.value && p.userId === movingUserId) {
            return
        }
        lastStablePositions.value[p.userId] = safePosition
        if (animatingPlayers.value[p.userId] === undefined) {
            animatingPlayers.value[p.userId] = safePosition 
        } else {
             if (!isMovingGlobal.value) {
                 animatingPlayers.value[p.userId] = safePosition
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
    const idx = gameStore.gameState.players.findIndex(p => p.userId === userId)
    return PLAYER_COLORS[idx % 4] || '#ffffff'
}

function getPlayerDisplayCoords(p: PlayerInfo) {
    const idx = animatingPlayers.value[p.userId]
        ?? lastStablePositions.value[p.userId]
        ?? p.position
        ?? 0
    return pathCoords.value[idx] || { x: 0, y: 0 }
}

function getPlayerFaceTarget(p: PlayerInfo) {
    const idx = animatingPlayers.value[p.userId] ?? p.position ?? 0
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
const MOVE_STEP_MS = 350
const lastMoveKey = ref<string | null>(null)

// gameStore.currentMove 감지하여 애니메이션 실행
const waitForMoveUnlock = async () => {
    while (gameStore.moveLocked || gameStore.roulettePending || gameStore.rouletteTargetId) {
        await sleep(100)
    }
}

function formatMoveDelta(before: number, after: number) {
    const delta = after - before
    if (delta > 0) return `${delta}칸 더 이동합니다.`
    if (delta < 0) return `${Math.abs(delta)}칸 뒤로 이동합니다.`
    return '이동이 없습니다.'
}

function getBuffLabel(buffType?: string) {
    switch (buffType) {
        case 'DOUBLE_SCORE':
            return '더블 이동'
        case 'PLUS_THREE':
            return '+3 이동'
        default:
            return buffType || '아이템'
    }
}

function getEffectLabel(effectType?: string) {
    switch (effectType) {
        case 'TILE_X2':
            return 'X2'
        default:
            return effectType || '특수칸'
    }
}

const showActionNotice = (text: string, duration = 1500) => {
    if (actionNoticeTimer !== null) {
        clearTimeout(actionNoticeTimer)
        actionNoticeTimer = null
    }
    actionNotice.value = text
    return new Promise<void>((resolve) => {
        actionNoticeTimer = window.setTimeout(() => {
            actionNotice.value = null
            actionNoticeTimer = null
            resolve()
        }, duration)
    })
}

const moveStep = async (userId: string, from: number, to: number) => {
    if (to > from) {
        for (let i = from + 1; i <= to; i++) {
            animatingPlayers.value[userId] = i
            await sleep(MOVE_STEP_MS)
        }
    } else if (to < from) {
        for (let i = from - 1; i >= to; i--) {
            animatingPlayers.value[userId] = i
            await sleep(MOVE_STEP_MS)
        }
    }
}

watch(() => gameStore.currentMove, async (move) => {
    if (!move) return
    const moveKey = JSON.stringify({
        userId: move.userId,
        start: move.startPosition,
        end: move.endPosition,
        process: move.moveProcess
    })
    if (lastMoveKey.value === moveKey) return
    lastMoveKey.value = moveKey

    await waitForMoveUnlock()

    isMovingGlobal.value = true
    gameStore.setMovementInProgress(true)
    emit('movement-change', true)

    // 1. 이동 애니메이션 (액션 기반)
    const userId = move.userId
    let currentPos = move.startPosition
    const actions = Array.isArray(move.moveProcess) ? move.moveProcess : []

    if (actions.length === 0) {
        await moveStep(userId, currentPos, move.endPosition)
        currentPos = move.endPosition
    } else {
        for (const action of actions) {
            if (action.type === 'CATCH') {
                continue
            }
            const before = action.before ?? currentPos
            const after = action.after ?? currentPos

            if (before !== currentPos) {
                await moveStep(userId, currentPos, before)
                currentPos = before
            }

            if (action.type === 'BUFF') {
                await showActionNotice(`아이템 '${getBuffLabel(action.buffType)}'이 적용됩니다.`, 1500)
            }

            if (action.type === 'TILE_EFFECT') {
                const label = getEffectLabel(action.effectType)
                await showActionNotice(`특수칸 '${label}'을 밟아서 ${formatMoveDelta(before, after)}`, 1500)
            }

            await moveStep(userId, currentPos, after)
            currentPos = after
        }
    }

    // 2. 잡기 처리: 잡힌 플레이어는 즉시 출발지로 이동
    const catchActions = move.moveProcess?.filter((item: any) => item.type === 'CATCH') || []
    catchActions.forEach((action: any) => {
        if (action.targetUserId !== undefined && action.after !== undefined) {
            animatingPlayers.value[action.targetUserId] = action.after
        }
    })

    isMovingGlobal.value = false
    gameStore.setMovementInProgress(false)
    emit('movement-change', false)
    wsStore.sendAck('MOVE_COMPLETE', gameStore.currentQuiz?.quizId, gameStore.gameState.round ?? undefined)
})


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

.action-notice {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  z-index: 1200;
  pointer-events: none;
}

.action-notice-card {
  background: white;
  padding: 14px 24px;
  border-radius: 14px;
  font-size: 1.3rem;
  font-weight: 900;
  color: #111827;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
  text-align: center;
}
</style>
