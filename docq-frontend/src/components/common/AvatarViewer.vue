<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'

const props = defineProps<{
  avatarIndex: number;
  scale?: number;
  autoRotate?: boolean;
  cameraX?: number;
  cameraY?: number;
  cameraZ?: number;
  action?: 'idle' | 'win' | 'lose'; 
  modelX?: number; 
}>()

interface AvatarConfig {
  path: string      // GLB 파일 경로 (예: '/assets/character/character_chicken.glb')
  
  // 애니메이션 인덱스 (GLB 파일 내부의 애니메이션 순서)
  winAnim: number   // 승리 시 재생할 애니메이션 번호
  loseAnim: number  // 패배 시 재생할 애니메이션 번호
  idleAnim: number  // 대기 시 재생할 애니메이션 번호
  
  // 승리 화면 설정 (선택 사항)
  winSetup?: {
    scale: number   // 승리 시 모델 크기
    modelY?: number // 승리 시 모델 Y축 위치 조정
  }
  // 패배 화면 설정 (필수 사항)
  loseSetup: {
    scale: number   // 패배 시 모델 크기
    x?: number      // 카메라 X 위치 ... 등등 카메라 세팅값
    y: number
    z: number
    modelY: number  // 패배 시 모델 위치 조정
    modelX: number
  }
}

const avatarConfig: AvatarConfig[] = [
  // 0: Chicken (🐔)
  { 
    path: '/assets/character/character_chicken.glb', 
    winAnim: 1, loseAnim: 3, idleAnim: 2,
    winSetup: { scale: 2.2, modelY: -0.2 }, 
    loseSetup: { scale: 0.5, x:1, y: 1, z: 0.5, modelY: 0, modelX: -0.5 }
  }, 

  // 1: Dog (🐶)
  { 
    path: '/assets/character/character_dog.glb',     
    winAnim: 2, loseAnim: 7, idleAnim: 5,
    winSetup: { scale: 2.6, modelY: -0.2 }, 
    loseSetup: { scale: 1.6, x:4, y: 1, z: 1, modelY: -1.0, modelX: -0.5 }
  }, 

  // 2: Pig (🐷)
  { 
    path: '/assets/character/character_pig.glb',     
    winAnim: 2, loseAnim: 7, idleAnim: 5,
    winSetup: { scale: 2.6, modelY: -0.2 }, 
    loseSetup: { scale: 1.6, x:4, y: 1, z: 1, modelY: -1.0, modelX: -0.5 }
  }, 

  // 3: Sheep (🐑) 
  { 
    path: '/assets/character/character_sheep.glb',   
    winAnim: 2, loseAnim: 7, idleAnim: 5,
    winSetup: { scale: 2.6, modelY: -0.2 }, 
    loseSetup: { scale: 1.6, x:4, y: 1, z: 1, modelY: -1.0, modelX: -0.5 }
  }, 
]

const container = ref<HTMLDivElement | null>(null)
let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let renderer: THREE.WebGLRenderer
let currentModel: THREE.Group | null = null
let mixer: THREE.AnimationMixer | null = null
let animationFrameId: number
let resizeObserver: ResizeObserver
const clock = new THREE.Clock()

// [추가] 메모리 정리 함수 (배포 시 필수)
const disposeModel = () => {
  if (currentModel) {
    scene.remove(currentModel)
    currentModel.traverse((child) => {
      if ((child as THREE.Mesh).isMesh) {
        const mesh = child as THREE.Mesh
        mesh.geometry.dispose()
        if (Array.isArray(mesh.material)) {
          mesh.material.forEach((m) => m.dispose())
        } else {
          mesh.material.dispose()
        }
      }
    })
    currentModel = null
  }
}

const initThree = () => {
  if (!container.value) return
  
  scene = new THREE.Scene()
  
  const { clientWidth, clientHeight } = container.value
  const width = clientWidth || 100
  const height = clientHeight || 100

  camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100)
  camera.position.set(0, props.cameraY || 1.5, props.cameraZ || 3.5)

  renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(window.devicePixelRatio)
  
  // 스타일 강제 적용 (크기 깨짐 방지)
  renderer.domElement.style.width = '100%';
  renderer.domElement.style.height = '100%';
  renderer.domElement.style.display = 'block';
  
  container.value.appendChild(renderer.domElement)

  const ambientLight = new THREE.AmbientLight(0xffffff, 4.0) // 밝기 살짝 조절
  scene.add(ambientLight)
  const dirLight = new THREE.DirectionalLight(0xffffff, 5.0)
  dirLight.position.set(2, 5, 5)
  dirLight.castShadow = true
  scene.add(dirLight)

  loadModel(props.avatarIndex)
  animate()
  
  resizeObserver = new ResizeObserver(() => handleResize())
  resizeObserver.observe(container.value)

  setTimeout(() => {
    handleResize()
  }, 400)
}

const handleResize = () => {
  if (!container.value || !camera || !renderer) return
  const w = container.value.clientWidth
  const h = container.value.clientHeight
  
  if (w === 0 || h === 0) return

  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

const loadModel = (index: number) => {
  // [수정] 기존 모델 메모리 해제 후 로드
  disposeModel()

  const config = avatarConfig[index] || avatarConfig[0]!
  const loader = new GLTFLoader()

  loader.load(
    config.path, 
    (gltf) => {
      const model = gltf.scene
      
      let s = props.scale || 1.5
      let posY = 0
      let posX = 0

      // 카메라 초기화
      if (camera) {
         camera.position.set(props.cameraX || 0, props.cameraY || 1.5, props.cameraZ || 3.5);
         camera.lookAt(0, 0, 0);
      }

      // 승리(Win) 설정
      if (props.action === 'win' && config.winSetup) {
        s = config.winSetup.scale;
        if (config.winSetup.modelY !== undefined) {
          posY = config.winSetup.modelY;
        }
      }

      // 패배(Lose) 설정
      if (props.action === 'lose' && config.loseSetup) {
        s = config.loseSetup.scale;
        posX = props.modelX !== undefined ? props.modelX : (config.loseSetup.modelX || 0);
        posY = config.loseSetup.modelY;
        
        if (camera) {
          camera.position.x = config.loseSetup.x || 0;
          camera.position.y = config.loseSetup.y;
          camera.position.z = config.loseSetup.z;
          camera.lookAt(0, 0.5, 0);
        }
      }

      model.scale.set(s, s, s)
      model.position.set(posX, posY, 0)

      model.traverse((child) => {
        if ((child as THREE.Mesh).isMesh) {
          child.castShadow = true
          child.receiveShadow = true
        }
      })

      scene.add(model)
      currentModel = model

      // 애니메이션 로직
      if (gltf.animations.length > 0) {
        mixer = new THREE.AnimationMixer(model)
        
        let targetIndex = config.idleAnim
        let loopMode: THREE.AnimationActionLoopStyles = THREE.LoopRepeat
        let clamp = false

        if (props.action === 'win') {
          targetIndex = config.winAnim
        } else if (props.action === 'lose') {
          targetIndex = config.loseAnim
          loopMode = THREE.LoopOnce
          clamp = true
        }

        const clip = gltf.animations[targetIndex] || gltf.animations[0]
        if (clip) {
          const action = mixer.clipAction(clip)
          
          action.reset()
          action.setLoop(loopMode, Infinity)
          action.clampWhenFinished = clamp

          if (props.action === 'lose') {
            action.timeScale = 0.2; 
          } else {
            action.timeScale = 1.0; 
          }

          action.play()
        }
      }
    },
    // [중요 수정] 세 번째 인자는 undefined (Progress 콜백 자리임)
    undefined,
    (error) => { console.error('모델 로드 실패:', error) }
  )
}

const animate = () => {
  animationFrameId = requestAnimationFrame(animate)
  const delta = clock.getDelta()
  if (mixer) mixer.update(delta)

  if (props.autoRotate && currentModel) {
    currentModel.rotation.y += 0.01
  }

  renderer.render(scene, camera)
}

onMounted(() => initThree())

onUnmounted(() => {
  cancelAnimationFrame(animationFrameId)
  if (resizeObserver) resizeObserver.disconnect()
  
  // [수정] 컴포넌트 해제 시 메모리 정리
  disposeModel()
  if (renderer) renderer.dispose()
})

watch(() => [props.avatarIndex, props.action], () => {
  loadModel(props.avatarIndex)
})
</script>

<template>
  <div ref="container" class="w-full h-full min-h-[50px] relative overflow-visible"></div>
</template>