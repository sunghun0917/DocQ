<script setup lang="ts">
/**
 * 바나나 타일 호버 감지: 레이-평면 교차로 마우스 아래 월드 좌표 계산 후 타일 매칭
 */
import { useTres } from '@tresjs/core'
import { ref, watch, onUnmounted } from 'vue'
import * as THREE from 'three'

const props = defineProps<{
  enabled: boolean
  tiles: Array<{ position: number; x: number; y: number; z: number }>
  /** 부모 TresGroup의 Y 오프셋 (GameBoard 기준 1.5) */
  planeYOffset?: number
}>()

const emit = defineEmits<{
  (e: 'hover', position: number | null): void
}>()

const { camera, renderer } = useTres()
const hoveredPosition = ref<number | null>(null)
const pointer = new THREE.Vector2()
const raycaster = new THREE.Raycaster()
const intersection = new THREE.Vector3()

const TILE_HALF = 0.5
const PLANE_Y_OFFSET = 1.5

/** 레이-평면 교차로 마우스 아래 월드 좌표를 구한 뒤, 해당 좌표가 속한 타일 반환 */
function getTileAtPointer(
  cam: THREE.Camera,
  tiles: Array<{ position: number; x: number; y: number; z: number }>
): number | null {
  const first = tiles[0]
  if (!first) return null
  const planeY = (props.planeYOffset ?? PLANE_Y_OFFSET) + first.y + 0.01
  const plane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -planeY)
  raycaster.setFromCamera(pointer, cam)
  if (!raycaster.ray.intersectPlane(plane, intersection)) return null

  const ix = intersection.x
  const iz = intersection.z
  for (const tile of tiles) {
    if (
      ix >= tile.x - TILE_HALF &&
      ix <= tile.x + TILE_HALF &&
      iz >= tile.z - TILE_HALF &&
      iz <= tile.z + TILE_HALF
    ) {
      return tile.position
    }
  }
  return null
}

function onPointerMove(e: PointerEvent) {
  if (!props.enabled || props.tiles.length === 0) return
  const cam = (camera as { value?: THREE.Camera })?.value
  const target = e.target as HTMLCanvasElement
  if (!cam || !target?.getBoundingClientRect) return

  const rect = target.getBoundingClientRect()
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1

  const hit = getTileAtPointer(cam, props.tiles)

  if (hit !== hoveredPosition.value) {
    hoveredPosition.value = hit
    emit('hover', hit)
  }
}

function onPointerLeave() {
  if (hoveredPosition.value !== null) {
    hoveredPosition.value = null
    emit('hover', null)
  }
}

let canvas: HTMLCanvasElement | null = null

function attach(canvasEl: HTMLCanvasElement | undefined) {
  if (canvas) {
    canvas.removeEventListener('pointermove', onPointerMove as EventListener)
    canvas.removeEventListener('pointerleave', onPointerLeave)
    canvas = null
  }
  if (canvasEl) {
    canvas = canvasEl
    canvas.addEventListener('pointermove', onPointerMove as EventListener)
    canvas.addEventListener('pointerleave', onPointerLeave)
  }
}

watch(
  () => {
    const r = (renderer as { value?: { domElement?: HTMLCanvasElement }; domElement?: HTMLCanvasElement }) ?? {}
    return r.domElement ?? r.value?.domElement
  },
  (el) => attach(el ?? undefined),
  { immediate: true }
)

onUnmounted(() => attach(undefined))
</script>

<template>
  <TresGroup />
</template>
