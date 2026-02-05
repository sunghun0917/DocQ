<script setup lang="ts">
import { computed, onMounted, shallowRef, watch } from 'vue'
import * as THREE from 'three'
import gsap from 'gsap'
import { clone as cloneSkeleton } from 'three/examples/jsm/utils/SkeletonUtils.js'
import { GLTFLoader, type GLTF } from 'three/examples/jsm/loaders/GLTFLoader.js'

type Vec3 = { x: number; y: number; z: number }

type PointerEventsObject3D = THREE.Object3D & {
  setPointerCapture(pointerId: number): void
  releasePointerCapture(pointerId: number): void
  hasPointerCapture(pointerId: number): boolean
}

const props = withDefaults(defineProps<{
  modelUrl: string
  position: Vec3
  lookTarget: Vec3
  isMoving: boolean
  isBananaHit?: boolean
  color?: string
}>(), {
  color: '#ffffff',
  isBananaHit: false
})

const MODEL_YAW_OFFSET = 0
const STEP_DURATION_MS = 350
const STEP_DURATION_S = STEP_DURATION_MS / 1000
const JUMP_HEIGHT = 0.35
const CHARACTER_Y_OFFSET = 0

// 바나나 효과용 상수
const BANANA_DURATION_S = 1.0
const ROTATION_360 = Math.PI * 2
const ROTATION_1080 = Math.PI * 6

const gltfCache = new Map<string, GLTF>()
const gltfPromiseCache = new Map<string, Promise<GLTF>>()

const loadGltf = async (url: string): Promise<GLTF> => {
  const cached = gltfCache.get(url)
  if (cached) return cached

  const cachedPromise = gltfPromiseCache.get(url)
  if (cachedPromise) return cachedPromise

  const loader = new GLTFLoader()

  const loadPromise = loader.loadAsync(url)
    .then((gltf) => {
      gltfCache.set(url, gltf)
      gltfPromiseCache.delete(url)
      return gltf
    })
    .catch((error) => {
      gltfPromiseCache.delete(url)
      throw error
    })

  gltfPromiseCache.set(url, loadPromise)

  return loadPromise
}

const scene = shallowRef<PointerEventsObject3D | null>(null)

const applyPlayerColor = (root: THREE.Object3D) => {
  if (!props.color || props.color === '#ffffff') return

  root.traverse((child) => {
    if (!(child instanceof THREE.Mesh)) return

    const mesh = child
    if (!mesh.material) return

    if (Array.isArray(mesh.material)) {
      mesh.material = mesh.material.map((material: THREE.Material) => material.clone())
      mesh.material.forEach((material: THREE.Material) => {
        if (material instanceof THREE.MeshStandardMaterial) {
          material.color.setStyle(props.color)
        }
      })
      return
    }

    const nextMaterial = mesh.material.clone()
    if (nextMaterial instanceof THREE.MeshStandardMaterial) {
      nextMaterial.color.setStyle(props.color)
    }

    mesh.material = nextMaterial
  })
}

const easeInOut = (t: number) => (t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t)

const getYawTowards = (from: THREE.Vector3, to: THREE.Vector3) => {
  const dx = to.x - from.x
  const dz = to.z - from.z
  return Math.atan2(dx, dz) + MODEL_YAW_OFFSET
}

const updateLookDirection = () => {
  const currentScene = scene.value
  if (!currentScene) return

  const from = currentScene.position.clone()
  const to = new THREE.Vector3(props.lookTarget.x, props.lookTarget.y, props.lookTarget.z)
  currentScene.rotation.set(0, getYawTowards(from, to), 0)
}

let activeTween: gsap.core.Tween | null = null

const animateTo = (target: THREE.Vector3) => {
  const currentScene = scene.value
  if (!currentScene) return

  if (activeTween) {
    activeTween.kill()
    activeTween = null
  }

  const from = currentScene.position.clone()
  const to = target.clone()

  // 바나나 밟음 여부에 따른 설정
  const isBanana = props.isBananaHit
  const duration = isBanana ? BANANA_DURATION_S : STEP_DURATION_S

  // 기본적으로 바라볼 방향 설정
  const baseRotation = getYawTowards(from, to)
  currentScene.rotation.set(0, baseRotation, 0)

  const progress = { t: 0, rot: 0 } // rot: 추가 회전값 (바나나용)

  activeTween = gsap.to(progress, {
    t: 1,
    rot: isBanana ? ROTATION_1080 : 0, // 바나나면 1080도 회전
    duration: duration,
    ease: 'none',
    onUpdate: () => {
      const eased = easeInOut(progress.t)

      // 위치 이동
      currentScene.position.x = THREE.MathUtils.lerp(from.x, to.x, eased)
      currentScene.position.z = THREE.MathUtils.lerp(from.z, to.z, eased)

      const baseY = THREE.MathUtils.lerp(from.y, to.y, eased)
      const jumpY = JUMP_HEIGHT * Math.sin(Math.PI * eased)
      currentScene.position.y = baseY + jumpY

      // 회전 적용 (기본 방향 + 뱅글뱅글)
      // 바나나일 때: baseRotation + progress.rot
      if (isBanana) {
        currentScene.rotation.y = baseRotation + progress.rot
      }
    },
    onComplete: () => {
      currentScene.position.copy(to)
      activeTween = null

      if (!props.isMoving) {
        updateLookDirection()
      }
    }
  })
}

const targetWorldPos = computed(() => new THREE.Vector3(
  props.position.x,
  props.position.y + CHARACTER_Y_OFFSET,
  props.position.z
))

const loadCharacterScene = async () => {
  if (activeTween) {
    activeTween.kill()
    activeTween = null
  }

  const gltf = await loadGltf(props.modelUrl)
  const cloned = cloneSkeleton(gltf.scene as unknown as PointerEventsObject3D) as PointerEventsObject3D
  applyPlayerColor(cloned)

  cloned.position.copy(targetWorldPos.value)
  scene.value = cloned

  updateLookDirection()
}

onMounted(() => {
  void loadCharacterScene()
})

watch(
  () => props.modelUrl,
  () => {
    void loadCharacterScene()
  }
)

watch(
  () => [props.position.x, props.position.y, props.position.z] as const,
  () => {
    if (!scene.value) return
    animateTo(targetWorldPos.value)
  }
)

watch(
  () => [props.lookTarget.x, props.lookTarget.y, props.lookTarget.z] as const,
  () => {
    if (!props.isMoving) {
      updateLookDirection()
    }
  }
)

watch(
  () => props.isMoving,
  (moving) => {
    if (!moving) {
      updateLookDirection()
    }
  }
)
</script>

<template>
  <primitive v-if="scene" :object="scene" cast-shadow />

  <TresMesh v-else :position="[position.x, position.y, position.z]" :scale="[0.3, 0.3, 0.3]">
    <TresBoxGeometry />
    <TresMeshStandardMaterial color="red" />
  </TresMesh>
</template>
