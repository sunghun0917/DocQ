<template>
  <div class="board-container">
    <TresCanvas :shadows="true" alpha clear-color="#222222"> 
      <TresOrthographicCamera
        :position="cameraPosition"
        :look-at="cameraTarget"
        :zoom="cameraZoom"
        :near="-100"
        :far="100"
        :left="camLeft"
        :right="camRight"
        :top="camTop"
        :bottom="camBottom"
      />



      <TresAmbientLight :intensity="1.5" />
      <TresDirectionalLight
  :position="[-5, 50, 5]"
  :intensity="1.5"
  cast-shadow
  :shadow-mapSize-width="2048"
  :shadow-mapSize-height="2048"
  :shadow-camera-left="-30"
  :shadow-camera-right="30"
  :shadow-camera-top="30"
  :shadow-camera-bottom="-30"
  :shadow-bias="-0.0001" 
  :shadow-normal-bias="0.05"
/>

      <primitive v-if="boardScene" :object="boardScene" />

      <!-- Special tile markers (from backend mapEvents) -->
      <TresMesh
        v-for="marker in specialTileMarkers"
        :key="marker.key"
        :position="[marker.x, marker.y + 0.05, marker.z]"
        :scale="[0.25, 0.25, 0.25]"
      >
        <TresSphereGeometry :args="[0.35, 16, 16]" />
        <TresMeshStandardMaterial color="#8b5cf6" />
      </TresMesh>

      <!-- Players -->
      <GameCharacter
        v-for="p in players"
        :key="p.userId"
        :modelUrl="getPlayerModelUrl(p.userId)"
        :position="getPlayerWorldPos(p)"
        :isMoving="isPlayerMoving(p.userId)"
        :lookTarget="getPlayerLookTarget(p)"
      />
    </TresCanvas>

    <div v-if="actionNotice" class="action-notice">
      <div class="action-notice-card">
        {{ actionNotice }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { TresCanvas } from '@tresjs/core'

import * as THREE from 'three'
import { GLTFLoader, type GLTF } from 'three/examples/jsm/loaders/GLTFLoader.js'

import GameCharacter from '@/components/game/board3D/GameCharacter.vue'
import { useGameStore } from '@/stores/gameStore'
import { useWebSocketStore } from '@/stores/websocketStore'
import type { PlayerInfo } from '@/types/docq-api'

import mapDataRaw from '@/data/mapData.json'

type Vec3 = { x: number; y: number; z: number }

type MapTile = {
  name: string
  type: string
  position: Vec3
}

const MAP_START_POSITION = 1
const MAP_GOAL_POSITION = 30

const parseTileNumber = (name: string): number => {
  const match = /Tile_(\d+)_/i.exec(name)
  if (!match) return Number.MAX_SAFE_INTEGER
  return Number(match[1])
}

const mapTiles: MapTile[] = (mapDataRaw as MapTile[])
  .slice()
  .sort((a, b) => parseTileNumber(a.name) - parseTileNumber(b.name))

const getTileByPosition = (position: number): MapTile | null => {
  const clamped = Math.min(Math.max(position, MAP_START_POSITION), MAP_GOAL_POSITION)
  const idx = clamped - MAP_START_POSITION
  return mapTiles[idx] ?? null
}

const computeMapCenter = (): [number, number, number] => {
  let minX = Infinity
  let maxX = -Infinity
  let minY = Infinity
  let maxY = -Infinity
  let minZ = Infinity
  let maxZ = -Infinity

  for (const tile of mapTiles) {
    minX = Math.min(minX, tile.position.x)
    maxX = Math.max(maxX, tile.position.x)
    minY = Math.min(minY, tile.position.y)
    maxY = Math.max(maxY, tile.position.y)
    minZ = Math.min(minZ, tile.position.z)
    maxZ = Math.max(maxZ, tile.position.z)
  }

  return [(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2]
}

const MAP_CENTER = computeMapCenter()

const emit = defineEmits<{
  (e: 'movement-change', moving: boolean): void
}>()

const gameStore = useGameStore()
const wsStore = useWebSocketStore()

const cameraPosition = ref<[number, number, number]>([MAP_CENTER[0] + 10, MAP_CENTER[1] + 20, MAP_CENTER[2] + 10])
// 실제 카메라가 보는 지점을 변경 필요
// MAP_CENTER에서 원하는 만큼 오프셋을 줍니다.
const cameraTarget = ref<[number, number, number]>([
    MAP_CENTER[0] - 1.5, // X축 오프셋 (카메라가 왼쪽을 봄 -> 맵은 오른쪽으로 이동)
    MAP_CENTER[1], 
    MAP_CENTER[2] // Z축 오프셋 (카메라가 아래쪽을 봄 -> 맵은 위쪽으로 이동)
])
const cameraZoom = ref(0.12)
const introPlayed = ref(false)

const aspect = ref(window.innerWidth / window.innerHeight)

const updateSlope = () => {
  aspect.value = window.innerWidth / window.innerHeight
}

const camLeft = computed(() => -1 * aspect.value)
const camRight = computed(() => 1 * aspect.value)
const camTop = computed(() => 1)
const camBottom = computed(() => -1)

onMounted(() => {
  window.addEventListener('resize', updateSlope)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateSlope)
})

type PointerEventsObject3D = THREE.Object3D & {
  setPointerCapture(pointerId: number): void
  releasePointerCapture(pointerId: number): void
  hasPointerCapture(pointerId: number): boolean
}

const boardScene = shallowRef<PointerEventsObject3D | null>(null)

const players = computed(() => gameStore.gameState.players)
const currentTurnPlayer = computed(() => gameStore.currentTurnPlayer)
const mapEvents = computed(() => gameStore.gameState.mapEvents as Record<string, string> | undefined)

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
    const safePosition = p.position ?? MAP_START_POSITION

    if (movingUserId && isMovingGlobal.value && p.userId === movingUserId) {
      return
    }

    lastStablePositions.value[p.userId] = safePosition
    if (animatingPlayers.value[p.userId] === undefined) {
      animatingPlayers.value[p.userId] = safePosition
    } else if (!isMovingGlobal.value) {
      animatingPlayers.value[p.userId] = safePosition
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
        void runIntroCamera()
      }, 2000)
    }
  },
  { immediate: true }
)

const DEFAULT_MODEL_URL = '/assets/character/character_chicken.glb'

const CHARACTER_MODELS = [
  DEFAULT_MODEL_URL,
  '/assets/character/character_dog.glb',
  '/assets/character/character_pig.glb',
  '/assets/character/character_sheep.glb'
] as const

const START_SPREAD_RADIUS = 0.35

const playerOrderIds = computed(() => players.value.map(p => p.userId).slice().sort())

const getPlayerSlotIndex = (userId: string) => {
  const idx = playerOrderIds.value.indexOf(userId)
  return idx >= 0 ? idx : 0
}

const getPlayerModelUrl = (userId: string): string => {
  const idx = getPlayerSlotIndex(userId)
  return CHARACTER_MODELS[idx % CHARACTER_MODELS.length] ?? DEFAULT_MODEL_URL
}

const applyStartSpread = (base: Vec3, userId: string): Vec3 => {
  const count = Math.min(playerOrderIds.value.length, CHARACTER_MODELS.length)
  if (count <= 1) return base

  const slot = getPlayerSlotIndex(userId) % count
  const angle = (2 * Math.PI * slot) / count

  return {
    x: base.x + Math.cos(angle) * START_SPREAD_RADIUS,
    y: base.y,
    z: base.z + Math.sin(angle) * START_SPREAD_RADIUS
  }
}

const getDisplayPosition = (p: PlayerInfo): number => {
  const userId = p.userId
  return (
    animatingPlayers.value[userId]
    ?? lastStablePositions.value[userId]
    ?? p.position
    ?? MAP_START_POSITION
  )
}

const getPlayerWorldPos = (p: PlayerInfo): Vec3 => {
  const posNumber = getDisplayPosition(p)
  const tile = getTileByPosition(posNumber)
  const base = tile?.position ?? mapTiles[0]?.position ?? { x: 0, y: 0, z: 0 }

  if (posNumber === MAP_START_POSITION) {
    const startCount = players.value.filter(other => getDisplayPosition(other) === MAP_START_POSITION).length
    if (startCount > 1) {
      return applyStartSpread(base, p.userId)
    }
  }

  return base
}

const getPlayerLookTarget = (p: PlayerInfo): Vec3 => {
  const posNumber = getDisplayPosition(p)

  let nextPos = posNumber

  if (isMovingGlobal.value && gameStore.currentMove?.userId === p.userId) {
    const start = gameStore.currentMove.startPosition
    const end = gameStore.currentMove.endPosition
    const step = end > start ? 1 : (end < start ? -1 : 0)
    if (step !== 0) {
      nextPos = posNumber + step
    }
  } else {
    nextPos = posNumber < MAP_GOAL_POSITION ? posNumber + 1 : posNumber
  }

  const tile = getTileByPosition(nextPos)
  return tile?.position ?? getPlayerWorldPos(p)
}

const isPlayerMoving = (userId: string) => isMovingGlobal.value && gameStore.currentMove?.userId === userId

const specialTileMarkers = computed(() => {
  const events = mapEvents.value
  if (!events) return []

  return Object.entries(events)
    .map(([key, eventType]) => {
      const pos = Number(key)
      if (!Number.isFinite(pos)) return null
      const tile = getTileByPosition(pos)
      if (!tile) return null
      return {
        key: `${key}-${eventType}`,
        x: tile.position.x,
        y: tile.position.y,
        z: tile.position.z
      }
    })
    .filter((v): v is NonNullable<typeof v> => v !== null)
})

const lerp = (a: number, b: number, t: number) => a + (b - a) * t
const easeInOut = (t: number) => (t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t)

const animate = (duration: number, update: (t: number) => void) => new Promise<void>(resolve => {
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

// 카메라 이동 헬퍼 함수
async function animateCameraTo(options: {
  target: [number, number, number],
  zoom?: number,
  duration: number,
  offset?: [number, number, number] // 목표 지점으로부터의 카메라 상대 위치 (없으면 현재 각도 유지)
}) {
  const startTarget = [...cameraTarget.value] as [number, number, number]
  const startPos = [...cameraPosition.value] as [number, number, number]
  const startZoom = cameraZoom.value

  // 오프셋이 없으면 현재 오프셋(각도)을 유지하며 이동
  const currentOffset : [number, number, number] = [
    startPos[0] - startTarget[0],
    startPos[1] - startTarget[1],
    startPos[2] - startTarget[2]
  ]
  const finalOffset = options.offset || currentOffset

  await animate(options.duration, (t) => {
    const p = easeInOut(t)
    
    // 1. Target Interpolation
    const nx = lerp(startTarget[0], options.target[0], p)
    const ny = lerp(startTarget[1], options.target[1], p)
    const nz = lerp(startTarget[2], options.target[2], p)
    cameraTarget.value = [nx, ny, nz]

    // 2. Position Interpolation (Target + Offset)
    cameraPosition.value = [
      nx + finalOffset[0],
      ny + finalOffset[1],
      nz + finalOffset[2]
    ]

    // 3. Zoom Interpolation
    if (options.zoom !== undefined) {
      cameraZoom.value = lerp(startZoom, options.zoom, p)
    }
  })
}

async function runIntroCamera() {
  if (players.value.length === 0) return

  // 0. 현재 상태 저장 (복귀용)
  const initialPos = [...cameraPosition.value] as [number, number, number]
  const initialTarget = [...cameraTarget.value] as [number, number, number]
  const initialZoom = cameraZoom.value

  // 기본 오프셋 (ZOOM OUT 상태의 각도)
  const baseOffset: [number, number, number] = [
    initialPos[0] - initialTarget[0],
    initialPos[1] - initialTarget[1],
    initialPos[2] - initialTarget[2]
  ]

  // === 1. 캐릭터별 줌인 (하이라이트) ===
  const ZOOM_IN_LEVEL = 0.35
  for (const p of players.value) {
    const pPos = getPlayerWorldPos(p)
    
    // 캐릭터에게 이동 & 줌인
    await animateCameraTo({
      target: [pPos.x, pPos.y, pPos.z],
      zoom: ZOOM_IN_LEVEL,
      duration: 800,
      offset: baseOffset // 같은 각도 유지
    })
    
    // 잠깐 대기 (포커싱)
    await sleep(600)
  }

  // === 2. 맵 경로 프리뷰 (Start -> End, 3칸씩 건너뛰며 스무스하게) ===
  const PATH_ZOOM = 0.25
  if (mapTiles.length > 0) {
    // 시작점으로 이동
    const startTile = mapTiles[0]!.position
    await animateCameraTo({
      target: [startTile.x, startTile.y, startTile.z],
      zoom: PATH_ZOOM,
      duration: 1000,
      offset: baseOffset
    })

    // 3칸 간격으로 웨이포인트 생성 (경로 단순화)
    const STEP = 3
    const waypoints = mapTiles.filter((_, i) => i % STEP === 0 || i === mapTiles.length - 1)

    // 경로 따라가기 (부드럽게)
    await animate(3000, (t) => {
      // 전체 웨이포인트 구간을 0~1로 매핑하여 현재 위치 계산
      const totalSegments = waypoints.length - 1
      const currentFloatIndex = t * totalSegments
      
      const idx = Math.floor(currentFloatIndex)
      const nextIdx = Math.min(idx + 1, totalSegments)
      const subT = currentFloatIndex - idx // 두 웨이포인트 사이의 진행률

      const p1 = waypoints[idx]!.position
      const p2 = waypoints[nextIdx]!.position

      const tx = lerp(p1.x, p2.x, subT)
      const ty = lerp(p1.y, p2.y, subT)
      const tz = lerp(p1.z, p2.z, subT)

      // 카메라 이동
      cameraTarget.value = [tx, ty, tz]
      cameraPosition.value = [
        tx + baseOffset[0],
        ty + baseOffset[1],
        tz + baseOffset[2]
      ]
    })
    await sleep(200)
  }

  // === 3. 원래 시점으로 복귀 ===
  await animateCameraTo({
    target: initialTarget,
    zoom: initialZoom,
    duration: 1200,
    offset: baseOffset // 저장해둔 오프셋으로 정확히 복귀
  })

  // === 4. 카메라 애니메이션 종료 ===
  wsStore.sendAck('CAMERA_END')
}

// === Animation Logic ===
const MOVE_STEP_MS = 350
const lastMoveKey = ref<string | null>(null)

const waitForMoveUnlock = async () => {
  while (gameStore.moveLocked || gameStore.roulettePending || gameStore.rouletteTargetId) {
    await sleep(100)
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

// script setup 내부
onMounted(async () => {
  const loader = new GLTFLoader()
  const gltf: GLTF = await loader.loadAsync('/assets/models/game_map.glb')
  
  const model = gltf.scene
  
  // 모델 내부의 모든 메쉬를 순회하며 그림자 설정
  model.traverse((child: any) => {
    if (child.isMesh) {
      child.castShadow = true    // 이 물체는 그림자를 만듦 (ex: 나무, 배)
      child.receiveShadow = true // 이 물체 위에 그림자가 생김 (ex: 바닥 타일)
      
      // (선택사항) 재질이 빛에 더 잘 반응하도록 설정
      // child.material.roughness = 1
      // child.material.metalness = 0
    }
  })

  boardScene.value = model as unknown as PointerEventsObject3D
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
