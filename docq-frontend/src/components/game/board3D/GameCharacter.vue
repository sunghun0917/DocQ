<template>
  <primitive v-if="scene" :object="scene" cast-shadow />
</template>

<script setup lang="ts">
import { shallowRef, watch, onMounted } from 'vue'
import { useFBX, useAnimations } from '@tresjs/cientos'
import { useIsometric } from '../composables/useIsometric'
import { clone } from 'three/examples/jsm/utils/SkeletonUtils.js'
import gsap from 'gsap'

// 부모 컴포넌트(Board.vue)에서 전달받는 props 정의
const props = defineProps({
  x: { type: Number, required: true },         // 현재 그리드 X 좌표
  y: { type: Number, required: true },         // 현재 그리드 Y 좌표
  isMoving: { type: Boolean, required: true }, // 현재 이동 중인지 여부 (true면 걷는 애니메이션)
  lookTarget: {                                // 다음에 바라볼 목표 지점 (다음 타일 좌표)
    type: Object,
    required: true,
  },
  color: { type: String, default: '#ffffff' }, // 캐릭터 색상 (플레이어 구분용, 기본값 흰색)
})

// 2D 그리드 좌표를 3D 등각투영 좌표로 변환하는 훅 가져오기
const { get3DPos } = useIsometric()

/* ===== FBX 모델 로드 (단일 애니메이션: Walking.fbx 사용) ===== */
// public/assets/characters/Walking.fbx 파일을 비동기로 로드
const idleFbx = await useFBX('/assets/characters/Walking.fbx')

// 로드된 FBX 객체에서 상태, 씬, 애니메이션 클립 추출
const idleState = idleFbx.state?.value || idleFbx
const idleSceneRaw = idleState.scene || idleState
const idleClips = idleState.animations || []

// 원본 씬을 복제하여 사용 (여러 플레이어가 동일한 모델을 쓸 때 충돌 방지)
let scene = null
if (idleSceneRaw) {
  scene = typeof idleSceneRaw.clone === 'function' ? clone(idleSceneRaw) : idleSceneRaw
}

/* ===== 색상 적용 (Material Clone) ===== */
// 각 플레이어마다 다른 색상을 입히기 위해 재질(Material)을 복제하여 색상 변경
if (scene) {
  scene.traverse((child) => {
    if (child.isMesh) {
      // 재질을 복제해야 다른 캐릭터에게 영향이 안 감 (독립적인 색상 적용)
      child.material = child.material.clone()
      child.material.color.set(props.color)
    }
  })
}

// 추출한 애니메이션 클립 중 첫 번째를 사용 (걷기 동작)
const idleClip = idleClips[0] || null

// useAnimations 훅에 사용할 클립 목록 생성
const allClips = shallowRef([
  ...(idleClip ? [idleClip] : []),
])

// 모델 로드 실패 시 에러 로그
if (!scene) {
  console.error('Character scene is null; check FBX load.')
}

/* ===== 초기 위치 설정 ===== */
if (scene) {
  // 시작점 좌표 계산
  const startPos = get3DPos(props.x, props.y)

  // 캐릭터 위치 설정
  // Y축(높이)에 0.25를 더하는 이유: 3D 타일 위에 발이 묻히지 않고 올라가게 하기 위함
  scene.position.set(startPos[0], startPos[1] + 0.25, startPos[2])

  // 모델 크기 축소 (FBX 원본 크기에 따라 조절 필요)
  scene.scale.set(0.01, 0.01, 0.01)

  // 기본 회전값 설정 (모델이 뒤를 보고 있다면 180도 회전)
  scene.rotation.y = Math.PI
}

/* ===== 애니메이션 세팅 ===== */
// 모델에 대한 애니메이션 액션들을 가져옴
const { actions } = useAnimations(allClips, scene)

const FADE_TIME = 0.2       // 애니메이션 전환 시 부드럽게 섞이는 시간 (초)
const MOVE_DURATION = 0.5   // 한 칸 이동하는 데 걸리는 시간 (0.5초 = 빠른 이동)
const Y_OFFSET = 0.25       // 캐릭터 높이 보정값 (상수로 관리)

/*
 * 함수: 다음 이동할 칸을 바라보게 회전시키는 함수
 * lookAt 메소드를 사용하여 3D 공간 상의 특정 좌표를 바라보게 함
 */
const updateLookDirection = () => {
  if (!scene || !props.lookTarget) return
  // 목표 지점(lookTarget)의 3D 좌표 계산
  const target3D = get3DPos(props.lookTarget.x, props.lookTarget.y)
  // 캐릭터가 해당 지점을 바라보도록 회전 (Y축은 캐릭터 높이에 맞춤)
  scene.lookAt(target3D[0], target3D[1] + Y_OFFSET, target3D[2])
}

/*
 * 라이프사이클 훅: 컴포넌트 마운트 시 실행
 * 초기 애니메이션 재생 및 방향 설정
 */
onMounted(() => {
  if (idleClip) {
    // 걷기 애니메이션 액션 가져오기
    const action = actions?.[idleClip.name]
    if (action) {
      // 속도를 2배로 설정하여 제자리걸음을 힘차게 하도록 함
      action.setEffectiveTimeScale(2.0)
      // 페이드인 효과와 함께 재생 시작
      action.reset().fadeIn(FADE_TIME).play()
    }
  }
  // 초기 바라보는 방향 업데이트
  updateLookDirection()
})

/*
 * 감시자(Watch): 캐릭터 좌표(x, y)가 변경될 때마다 실행
 * GSAP를 사용하여 부드러운 이동 애니메이션 처리
 */
watch(
  () => [props.x, props.y], // 감시 대상: x, y 좌표
  ([newX, newY]) => {
    if (!scene) return

    // 이동할 목적지의 3D 좌표 계산
    const target = get3DPos(newX, newY)

    // GSAP 애니메이션 실행 (Tween)
    gsap.to(scene.position, {
      x: target[0],             // 목표 X 좌표
      z: target[2],             // 목표 Z 좌표
      y: target[1] + Y_OFFSET,  // 목표 Y 좌표 (높이 보정 포함)
      duration: MOVE_DURATION,  // 이동 시간
      ease: 'power1.inOut',     // 가속/감속 효과 (부드럽게 출발/정지)

      // 이동 시작 시 실행: 목적지 바라보기
      onStart: () => {
        const dest3D = get3DPos(newX, newY)
        scene.lookAt(dest3D[0], dest3D[1] + Y_OFFSET, dest3D[2])
      },

      // 이동 완료 시 실행: 다음 타겟(또 이동할 곳) 바라보기
      onComplete: () => {
        updateLookDirection()
      }
    })
  }
)

/*
 * 감시자(Watch): 이동 상태(isMoving)가 변경될 때 실행
 * 이동이 멈췄을 때 방향을 한 번 더 바로잡아줌
 */
watch(
  () => props.isMoving,
  (moving) => {
    // 이동 멈췄을 때만 방향 재정렬 (혹시 모를 오차 보정)
    if (!moving) {
      updateLookDirection()
    }
  }
)

/*
 * 감시자(Watch): 다음 목표 지점(lookTarget) 데이터 자체가 바뀌면 실행
 * 실시간으로 시선 처리 갱신
 */
watch(
  () => props.lookTarget,
  () => {
    updateLookDirection()
  },
  { deep: true } // 객체 내부 값 변경까지 감지
)
</script>
