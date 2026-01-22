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

/* ===== 씬 복제 (Independent Instances) ===== */
// 원본 씬을 그대로 쓰면 모든 플레이어가 똑같이 움직이므로, clone()으로 복제해서 사용
// [TS] 타입 단언(as THREE.Group)을 사용하여 타입 에러 방지
let scene: THREE.Group | null = null
if (idleSceneRaw) {
  scene = (typeof idleSceneRaw.clone === 'function' ? clone(idleSceneRaw) : idleSceneRaw) as THREE.Group
}

/* ===== 색상 적용 (Material Clone) ===== */
// 각 플레이어마다 다른 색상을 입히기 위해 재질(Material)도 복제해야 함
if (scene) {
  scene.traverse((child: any) => {
    // Mesh인 경우에만 재질 변경
    if (child.isMesh && child.material) {
      const mesh = child as THREE.Mesh
      // 재질을 복제하여 독립적인 색상 적용 가능하게 함
      if (Array.isArray(mesh.material)) {
        mesh.material = mesh.material.map(m => m.clone())
      } else {
        mesh.material = mesh.material.clone()
        if ('color' in mesh.material) {
            (mesh.material as THREE.MeshStandardMaterial).color.setStyle(props.color)
        }
      }
    }
  })
}

// 애니메이션 클립 준비 (첫 번째 클립이 걷기 동작이라고 가정)
const idleClip = idleClips[0] || null
const allClips = shallowRef(idleClip ? [idleClip] : [])

// 로드 실패 시 에러 로그
if (!scene) {
  console.error('Character scene is null; check FBX load.')
}

/* ===== 초기 위치 설정 ===== */
if (scene) {
  // 시작점 3D 좌표 계산
  const startPos = get3DPos(props.x, props.y)

  // Y축(높이)에 0.25를 더해 타일 위에 올라가게 함
  scene.position.set(startPos[0], startPos[1] + 0.25, startPos[2])

  // 모델 크기 축소 (FBX 원본 크기에 맞춰 조절)
  scene.scale.set(0.01, 0.01, 0.01)

  // 기본 회전값 (모델이 뒷면을 보고 있다면 180도 회전)
  scene.rotation.y = Math.PI
}

/* ===== 애니메이션 액션 설정 ===== */
// scene이 null일 수 있으므로 타입 단언 또는 대체값 사용
const { actions } = useAnimations(allClips, scene || new THREE.Object3D())

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
  if (idleClip && actions[idleClip.name]) {
    const action = actions[idleClip.name]
    // 속도를 2배로 설정 (씩씩하게 걷는 느낌)
    action.setEffectiveTimeScale(2.0)
    // 페이드인과 함께 재생
    action.reset().fadeIn(FADE_TIME).play()
  }
  // 초기 시선 처리
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
