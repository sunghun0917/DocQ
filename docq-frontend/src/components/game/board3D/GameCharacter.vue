<!-- src/components/game/board3d/Character.vue -->
<script setup lang="ts">
import { shallowRef, watch, onMounted } from 'vue'
import { useFBX, useAnimations } from '@tresjs/cientos'
import { useIsometric } from '@/composables/useIsometric'
// @ts-ignore
import { clone } from 'three/examples/jsm/utils/SkeletonUtils.js'
import * as THREE from 'three' // THREE 타입 사용을 위해 임포트
import gsap from 'gsap'

// [TS] Props 인터페이스 정의 (상세 주석 포함)
// withDefaults를 사용하여 color의 기본값을 설정
const props = withDefaults(defineProps<{
  x: number         // 현재 그리드 X 좌표
  y: number         // 현재 그리드 Y 좌표
  isMoving: boolean // 이동 중 여부 (true면 애니메이션 시점 보정)
  lookTarget: { x: number, y: number } // 다음 시선 목표 지점
  color?: string    // 캐릭터 색상 (선택 사항, 기본값 흰색)
}>(), {
  color: '#ffffff'
})

// 3D 좌표 변환 훅 가져오기
const { get3DPos } = useIsometric()

/* ===== FBX 모델 로드 (임시 비활성화: 무한 로딩 문제 해결) ===== */
// const idleFbx = await useFBX('/assets/character/Walking.fbx')
const idleFbx = null
const idleClips: any[] = []

/* ===== 캐릭터 대체 모델 (Primitives) 생성 ===== */
const scene = new THREE.Group()

// 1. 몸통 (원기둥)
const bodyGeo = new THREE.CylinderGeometry(0.2, 0.2, 0.6, 16)
const bodyMat = new THREE.MeshStandardMaterial({ color: props.color })
const bodyMesh = new THREE.Mesh(bodyGeo, bodyMat)
bodyMesh.position.y = 0.3 // 중심점 조정
bodyMesh.castShadow = true
scene.add(bodyMesh)

// 2. 머리 (구)
const headGeo = new THREE.SphereGeometry(0.15, 16, 16)
const headMat = new THREE.MeshStandardMaterial({ color: '#ffeaa7' }) // 피부색?
const headMesh = new THREE.Mesh(headGeo, headMat)
headMesh.position.y = 0.7 // 몸통 위에
headMesh.castShadow = true
scene.add(headMesh)

// 3. 눈 (방향 표시용)
const eyeGeo = new THREE.SphereGeometry(0.05, 8, 8)
const eyeMat = new THREE.MeshBasicMaterial({ color: 'black' })
const leftEye = new THREE.Mesh(eyeGeo, eyeMat)
leftEye.position.set(0.1, 0.75, 0.1)
const rightEye = new THREE.Mesh(eyeGeo, eyeMat)
rightEye.position.set(0.1, 0.75, -0.1)
scene.add(leftEye)
scene.add(rightEye)

// 크기 및 위치 초기화
const startPos = get3DPos(props.x, props.y)
scene.position.set(startPos[0], startPos[1], startPos[2])
// scene.scale.set(0.5, 0.5, 0.5) // Primitive는 크기가 적당하므로 스케일 조정 불필요 or 미세조정

/* ===== 애니메이션 액션 (비활성화) ===== */
const { actions } = { actions: {} as any } // Dummy

// 상수로 관리하는 설정값들
const FADE_TIME = 0.2       // 애니메이션 전환 페이드 시간
const MOVE_DURATION = 0.5   // 한 칸 이동 시간
const Y_OFFSET = 0.0        // Primitive는 바닥 기준이므로 0

/*
 * 함수: 다음 이동할 칸을 바라보게 회전시키는 함수
 */
const updateLookDirection = () => {
  if (!scene || !props.lookTarget) return
  const target3D = get3DPos(props.lookTarget.x, props.lookTarget.y)
  scene.lookAt(target3D[0], target3D[1], target3D[2])
}

/*
 * 라이프사이클
 */
onMounted(() => {
  // 애니메이션 없음 (Primitive)
  updateLookDirection()
})

/*
 * 감시자(Watch): 캐릭터 좌표(x, y)가 변경될 때마다 이동 애니메이션 실행
 */
watch(
  () => [props.x, props.y] as [number, number],
  ([newX, newY]: [number, number]) => {
    if (!scene) return

    // 목표 지점 3D 좌표
    const target: [number, number, number] = get3DPos(newX, newY)

    // GSAP를 이용한 부드러운 트윈(Tween) 이동
    gsap.to(scene.position, {
      x: target[0],
      z: target[2],
      y: target[1] + Y_OFFSET,
      duration: MOVE_DURATION,
      ease: 'power1.inOut', // 부드러운 가감속

      // 이동 시작 시: 목적지 바라보기
      onStart: () => {
        const dest3D: [number, number, number] = get3DPos(newX, newY)
        scene.lookAt(dest3D[0], dest3D[1] + Y_OFFSET, dest3D[2])
      },

      // 이동 완료 시: 다음 타겟(Target+1) 바라보기
      onComplete: () => {
        updateLookDirection()
      }
    })
  }
)

/*
 * 감시자(Watch): 이동이 멈췄을 때 시선 재정렬
 */
watch(() => props.isMoving, (moving) => {
  if (!moving) updateLookDirection()
})

/*
 * 감시자(Watch): 다음 목표(lookTarget)가 바뀌면 시선 갱신
 */
watch(() => props.lookTarget, () => updateLookDirection(), { deep: true })
</script>

<template>
  <!-- 씬 객체가 준비되면 렌더링 -->
  <primitive v-if="scene" :object="scene" cast-shadow />
  
  <!-- 로드 실패 시 디버그용 큐브 (빨강) -->
  <TresMesh v-else :position="get3DPos(x, y)" :scale="[0.5, 0.5, 0.5]">
    <TresBoxGeometry />
    <TresMeshStandardMaterial color="red" />
  </TresMesh>
</template>
