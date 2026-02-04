<template>
  <div ref="container" class="character-widget"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted,watch } from 'vue';
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

// 1. 모델 파일들 임포트 (파일 경로 확인 필수!)
import chickenPath from '/assets/character/character_chicken.glb';
import dogPath from '/assets/character/character_dog.glb';
import sheepPath from '/assets/character/character_sheep.glb';
import pigPath from '/assets/character/character_pig.glb';

const props = defineProps({
  isRunning: {
    type: Boolean,
    default: false
  }
});

const container = ref<HTMLElement | null>(null);
let scene: THREE.Scene;
let camera: THREE.PerspectiveCamera;
let renderer: THREE.WebGLRenderer;

// 여러 개의 믹서를 관리하기 위한 배열
const mixers: THREE.AnimationMixer[] = []; 
let clock = new THREE.Clock();
let animationId: number;

// 캐릭터 설정 데이터 (위치 X좌표, 크기 등)
const characters = [
  // y 값을 추가했습니다. 숫자가 커지면 위로, 작아지면(마이너스) 아래로 내려갑니다.
  { file: chickenPath, x: 3.3, y: -0.4, scale: 2.4, speed: 0.5 }, 
  { file: dogPath,     x: -1.6, y: -0.2, scale: 2.2, speed: 1.0 }, 
  { file: sheepPath,   x: 1.6,  y: -0.2, scale: 2.2, speed: 1.2 }, 
  { file: pigPath,     x: -3.4,  y: -0.5, scale: 2.4, speed: 1.0 }, 
];

const initThree = () => {
  if (!container.value) return;

  const width = container.value.clientWidth;
  const height = container.value.clientHeight;

  scene = new THREE.Scene();
  
  // 4마리가 다 보여야 하므로 카메라를 조금 더 뒤로 뺍니다 (z: 5 -> 6.5)
  camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 100);
  camera.position.set(0, 1.0, 6.5); 

  renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
  renderer.setSize(width, height);
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.outputColorSpace = THREE.SRGBColorSpace; 
  container.value.appendChild(renderer.domElement);

  // 조명 설정 (밝게)
  const ambientLight = new THREE.AmbientLight(0xffffff, 5.0);
  scene.add(ambientLight);
  
  const dirLight = new THREE.DirectionalLight(0xffffff, 3.0);
  dirLight.position.set(5, 10, 7);
  scene.add(dirLight);
  
  const frontLight = new THREE.DirectionalLight(0xffffff, 1.5);
  frontLight.position.set(0, 0, 10);
  scene.add(frontLight);

  // 2. 반복문으로 캐릭터 로드
  const loader = new GLTFLoader();
  
  // 모델과 속도 정보를 저장할 배열
  const loadedModels: { scene: THREE.Group; speed: number }[] = [];

  characters.forEach((charData, index) => {
    loader.load(charData.file, (gltf) => {
      const model = gltf.scene;

      // 밝기 치트키 적용
      model.traverse((child: any) => {
        if (child.isMesh) {
          child.material.roughness = 1;
          child.material.metalness = 0;
          child.material.emissive = new THREE.Color(0x222222);
          child.material.emissiveIntensity = 0.2;
        }
      });

      // 위치 및 크기 설정
      let currentScale = charData.scale;

      if (props.isRunning) {
         // [Running Mode] 왼쪽 밖에서 시작, 순차적 배치
         const startX = -10 - (index * 4); 
         model.position.set(startX, charData.y, 0); 
         // 달리기 모드일 때 크기 확대 (약 1.25배)
         currentScale = charData.scale * 2;
      } else {
         // [Standing Mode] 기존 고정 위치
         model.position.set(charData.x, charData.y, 0); 
      }
      
      model.scale.set(currentScale, currentScale, currentScale);
      model.rotation.y = Math.PI / 2; // 오른쪽 보기

      scene.add(model);
      
      // 애니메이션 루프에서 위치 업데이트를 위해 저장
      loadedModels.push({ scene: model, speed: charData.speed });

      // 애니메이션 설정
      if (gltf.animations.length > 0) {
        const mixer = new THREE.AnimationMixer(model);
        mixers.push(mixer); // 배열에 추가
        
        const clip = gltf.animations[0];
        if (clip) {
          const action = mixer.clipAction(clip);
          
          // [디테일] 걷는 동작이 로봇처럼 똑같지 않게 시작 시간을 랜덤하게 섞음
          action.time = Math.random() * 2; 
          
          // 속도도 미세하게 다르게 (선택사항)
          action.timeScale = charData.speed; 
          
          action.play();
        }
      }
    });
  });

  const animate = () => {
    animationId = requestAnimationFrame(animate);
    const delta = clock.getDelta();
    
    // [수정] 모든 믹서 업데이트
    mixers.forEach((mixer) => mixer.update(delta));
    
    // [추가] 캐릭터 이동 로직 (isRunning이 true일 때만)
    if (props.isRunning) {
        loadedModels.forEach((item) => {
            item.scene.position.x += item.speed * delta * 3.5; // 속도 증가
            
            // 화면 오른쪽 끝(약 8)을 넘어가면 다시 왼쪽 끝으로 이동
            if (item.scene.position.x > 8) {
                item.scene.position.x = -12;
            }
        });
    }

    renderer.render(scene, camera);
  };
  
  animate();
};

onMounted(() => setTimeout(initThree, 50));
onUnmounted(() => {
  cancelAnimationFrame(animationId);
  if (renderer) renderer.dispose();
});
</script>

<style scoped>
.character-widget {
  width: 100%;
  height: 100%;
}
</style>