<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import gsap from 'gsap';
import { Observer } from 'gsap/all';
import { useUserStore } from '@/stores/userStore';
import { getErrorMessage } from '@/services/api';
import CharacterBackground from '@/components/lobby/CharacterBackground.vue';
import AlertModal from '@/components/common/AlertModal.vue';
import CreateRoomModal from '@/components/lobby/CreateRoomModal.vue';
import JoinRoomModal from '@/components/lobby/JoinRoomModal.vue';

gsap.registerPlugin(Observer);

// 메뉴 데이터
const menuItems = [
  { id: 'profile', label: '내 정보', description: '닉네임을 수정할 수 있어요.', icon: 'face' },
  { id: 'create', label: '방 만들기', description: '새로운 게임 방을 만들어요.', icon: 'add_circle' },
  { id: 'join', label: '참여하기', description: '코드를 입력해 방에 들어가요.', icon: 'login' },
  { id: 'guide', label: '게임 가이드', description: '게임 방법을 알아볼까요?', icon: 'menu_book' },
  { id: 'stack', label: '기술 스택', description: '개발 도구 정보입니다.', icon: 'code_blocks' },
  { id: 'team', label: '만든 사람들', description: '우리를 소개합니다!', icon: 'groups' },
];

const userStore = useUserStore();
const nickname = ref('');
const isNicknameSet = ref(false);
const showCreateModal = ref(false);
const showJoinModal = ref(false);
const isErrorModalOpen = ref(false);
const errorMessage = ref('');
const isShaking = ref(false);

// --- 룰렛 변수 ---
const totalItems = menuItems.length;
const anglePerItem = 360 / totalItems;
const wheelRef = ref<HTMLElement | null>(null);
const arrowRef = ref<HTMLElement | null>(null);
const activeIndex = ref(0);
let currentRotation = 0;
let targetRotation = 0;
let isInteracting = false;
let idleTimer: ReturnType<typeof setTimeout>;

// [추가] 틱 애니메이션 변수
let lastSectionIndex = 0;
let lastDelta = 0; // 회전 방향 감지용

const activeItem = computed(() => {
  const item = menuItems[activeIndex.value];
  return (item ?? menuItems[0])!;
});

const confirmNickname = async () => {
  if (nickname.value.trim().length < 2) {
    triggerShake();
    errorMessage.value = "닉네임은 두 글자 이상이어야 해요!";
    isErrorModalOpen.value = true;
    return;
  }
  const result = await userStore.loginWithApi(nickname.value.trim());
  if (!result.success) {
    triggerShake();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    errorMessage.value = getErrorMessage((result as any).error);
    isErrorModalOpen.value = true;
    return;
  }
  isNicknameSet.value = true;
  startRoulette();
};

const triggerShake = () => {
  isShaking.value = true;
  setTimeout(() => isShaking.value = false, 500);
};

const syncNicknameFromStore = () => {
  if (userStore.isAuthenticated && userStore.username && userStore.username !== 'Guest') {
    nickname.value = userStore.username;
    isNicknameSet.value = true;
    startRoulette();
  }
};

const handleAction = (action: string) => {
  if (action === 'create') showCreateModal.value = true;
  if (action === 'join') showJoinModal.value = true;
  if (action === 'profile') {
      userStore.logout();
      nickname.value = '';
      isNicknameSet.value = false;
      stopRoulette();
  }
};

// [수정] 양방향 틱 애니메이션
const playTickAnimation = (direction: number) => {
    if (!arrowRef.value) return;
    
    // direction > 0 이면 시계방향 회전 -> 핀이 오른쪽에서 왼쪽으로 침 -> 화살표는 왼쪽으로 튕김 (-각도)
    // direction < 0 이면 반시계 회전 -> 핀이 왼쪽에서 오른쪽으로 침 -> 화살표는 오른쪽으로 튕김 (+각도)
    const tickAngle = direction > 0 ? -25 : 25;

    gsap.killTweensOf(arrowRef.value);
    
    // 1. 충격 (빠르게 꺾임)
    gsap.to(arrowRef.value, {
        rotation: tickAngle,
        duration: 0.05,
        ease: "power1.out",
        onComplete: () => {
            // 2. 복귀 (탄성)
            gsap.to(arrowRef.value, {
                rotation: 0,
                duration: 0.3,
                ease: "elastic.out(1.2, 0.5)" 
            });
        }
    });
};

const updateLoop = () => {
  if (!isNicknameSet.value) return;

  if (!isInteracting) {
      targetRotation -= 0.05; 
  }
  
  // 현재 프레임의 이동량 (속도)
  const diff = targetRotation - currentRotation;
  
  // 이동량이 매우 작으면(멈춤) 틱 소리 안 나게
  if (Math.abs(diff) > 0.01) {
      lastDelta = diff; // 방향 저장
  }

  currentRotation += diff * 0.08;

  if (wheelRef.value) {
    gsap.set(wheelRef.value, { rotation: currentRotation });
  }

  // --- 핀 충돌 로직 ---
  // 각도 정규화 (음수 방지 및 0~360 보정 없이 절대값으로 계산하여 경계선 추적)
  // 핀은 각 섹션 사이(경계선)에 위치하므로 offset 없이 계산
  const angle = Math.abs(currentRotation);
  
  // 현재 지나고 있는 핀 인덱스 (반올림하여 가장 가까운 핀 감지)
  const currentPinIndex = Math.floor((angle + anglePerItem / 2) / anglePerItem);

  if (currentPinIndex !== lastSectionIndex) {
      playTickAnimation(lastDelta); // 저장된 방향으로 튕김
      lastSectionIndex = currentPinIndex;
  }

  // 메뉴 인덱스 계산
  let normalized = currentRotation % 360;
  if (normalized < 0) normalized += 360;
  let index = Math.round(((360 - normalized + 90) + anglePerItem/2 ) / anglePerItem) % totalItems;

  if (activeIndex.value !== index) {
    activeIndex.value = index;
  }
};

let observer: any = null;
const startRoulette = () => {
    if (observer) return;
    gsap.ticker.add(updateLoop);
    observer = Observer.create({
        target: window,
        type: "wheel,touch,pointer",
        wheelSpeed: -1.3,
        onPress: () => { isInteracting = true; clearTimeout(idleTimer); },
        onRelease: () => { clearTimeout(idleTimer); idleTimer = setTimeout(() => isInteracting = false, 1000); },
        onChange: (self: any) => {
            if (!isNicknameSet.value) return;
            isInteracting = true;
            let delta = self.deltaY * 0.3;
            delta = Math.max(-60, Math.min(60, delta));
            targetRotation -= delta;
            clearTimeout(idleTimer);
            idleTimer = setTimeout(() => isInteracting = false, 1000);
        }
    });
};

const stopRoulette = () => {
    gsap.ticker.remove(updateLoop);
    if (observer) { observer.kill(); observer = null; }
    clearTimeout(idleTimer);
    currentRotation = 0; targetRotation = 0; activeIndex.value = 0;
};

// HomeView.vue의 script setup 내부

// 기존 clouds 타입 정의 수정 (typeId 추가)
const clouds = ref<{ id: number; typeId: number; style: any }[]>([]);

const generateClouds = () => {
  const cloudCount = 12; // 개수 조금 더 늘림
  const newClouds = [];

  for (let i = 0; i < cloudCount; i++) {
    // 크기, 속도 랜덤 (기존 동일)
    const sizeScale = 0.6 + Math.random() * 0.8; 
    const duration = 25 + Math.random() * 35; 
    const delay = -(Math.random() * duration); 
    
    // [추가] 1, 2, 3 중 하나의 타입을 랜덤하게 선택
    const typeId = Math.floor(Math.random() * 3) + 1;

    newClouds.push({
      id: i,
      typeId: typeId, // 타입 ID 저장
      style: {
        top: `${2 + Math.random() * 35}%`, // 상단 위치 영역 약간 넓힘
        opacity: 0.7 + Math.random() * 0.3,
        transform: `scale(${sizeScale})`,
        animationDuration: `${duration}s`,
        animationDelay: `${delay}s`,
      }
    });
  }
  clouds.value = newClouds;
};

onMounted(() => { userStore.restoreUser(); syncNicknameFromStore();generateClouds(); });
onUnmounted(() => { stopRoulette(); });
</script>

<template>
  <div class="page-container cartoon-theme">
<div class="cartoon-bg">
  <div class="sky"></div>

  <div 
    v-for="cloud in clouds" 
    :key="cloud.id" 
    :class="['cloud', `cloud-type-${cloud.typeId}`]"
    :style="cloud.style"
  ></div>

  <div class="ground-back"></div>
  <div class="ground-front"></div>

  <div class="decoration tree-1"></div>
  <div class="decoration tree-2"></div>
</div>

    <transition name="intro-fade">
        <div v-if="!isNicknameSet" class="intro-container">
            <div class="intro-panel wood-panel bounce-in">
                <div class="logo-area">
                    <h1 class="game-title">DocQ</h1>
                    <p class="game-subtitle">PDF기반 퀴즈 게임</p>
                </div>
                <div class="input-area">
                    <label>닉네임을 설정해 주세요</label>
                    <div class="input-group stone-input" :class="{ 'shake-anim': isShaking }">
                        <input type="text" v-model="nickname" placeholder="닉네임 입력" @keyup.enter="confirmNickname" autofocus />
                        <button @click="confirmNickname" class="go-btn large">시작하기</button>
                    </div>
                </div>
            </div>
        </div>
    </transition>

    <transition name="main-slide">
        <div v-if="isNicknameSet" class="main-container">
            
            <div class="active-content">
                <transition name="menu-fade" mode="out-in">
                    <div :key="activeItem.id" class="wood-panel">
                        <div class="content-header">
                            <div class="icon-circle"><span class="leaf-icon material-symbols-outlined">{{ activeItem.icon }}</span></div>
                            <span class="index-number">Menu</span>
                        </div>
                        <h1 class="wood-text">{{ activeItem.label }}</h1>
                        <p class="description">{{ activeItem.description }}</p>
                        <div class="action-area mt-6">
                            <div v-if="activeItem.id === 'profile'">
                                <div class="welcome-msg mb-4"><span class="material-symbols-outlined check-icon">check_circle</span><span><span class="highlight">{{ nickname }}</span>님</span></div>
                                <button @click="handleAction('profile')" class="cartoon-btn secondary"><span class="material-symbols-outlined">logout</span>로그아웃</button>
                            </div>
                            <div v-else-if="activeItem.id === 'create'">
                                <button @click="handleAction('create')" class="cartoon-btn primary large-btn animate-pulse-slow"><span class="material-symbols-outlined text-3xl">add_circle</span>방 만들기</button>
                            </div>
                            <div v-else-if="activeItem.id === 'join'">
                                <button @click="handleAction('join')" class="cartoon-btn secondary large-btn"><span class="material-symbols-outlined text-3xl">login</span>참여하기</button>
                            </div>
                            <div v-else class="info-msg"><span class="material-symbols-outlined text-4xl text-leaf-green mb-2">touch_app</span><p>휠을 돌려 메뉴를 선택하세요!</p></div>
                        </div>
                    </div>
                </transition>
            </div>

            <div class="roulette-wrapper">
                <div class="wheel-shadow-static"></div>
                
                <div class="roulette-wheel" ref="wheelRef">
                    
                    <div class="wood-wheel-enhanced">
                        <div class="wood-texture-overlay"></div>
                    </div>

                    <div class="pins-container">
                        <div 
                            v-for="n in (totalItems * 2)" 
                            :key="'pin-'+n"
                            class="pin"
                            :style="{ 
                                transform: `rotate(${(n - 1) * (anglePerItem / 2)}deg) translateY(-985px)` 
                            }"
                        ></div>
                    </div>
                </div>

                <div class="character-sticker">
                    <CharacterBackground />
                </div>

                <div class="central-arrow" ref="arrowRef">
    <svg viewBox="0 0 100 300" xmlns="http://www.w3.org/2000/svg">
        <g>
            <path d="M 50 200 L 15 30 L 85 30 Z" 
                  fill="#ff5252" 
                  stroke="#ff5252" 
                  stroke-width="6"
                  stroke-linejoin="round"
            />
            
        </g>
    </svg>
</div>
            </div>

            <div class="scroll-indicator wood-text">
                SCROLL TO PLAY
            </div>
        </div>
    </transition>

    <AlertModal :isOpen="isErrorModalOpen" type="error" :message="errorMessage" @close="isErrorModalOpen = false" />
    <CreateRoomModal v-if="showCreateModal" @close="showCreateModal = false" />
    <JoinRoomModal v-if="showJoinModal" @close="showJoinModal = false" />
  </div>

</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&family=Nanum+Pen+Script&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,1,0');

.cartoon-theme {
  --text-brown: #5d4037; --text-light: #efebe9; --wood-light: #d7ccc8; --wood-medium: #bcaaa4; --wood-dark: #8d6e63;
  --wood-border: #5d4037; --leaf-green: #7cb342; --leaf-dark: #558b2f; --gem-yellow: #ffca28; --font-family: 'Jua', sans-serif;
}
.page-container { position: relative; width: 100vw; height: 100vh; overflow: hidden; background-color: #81d4fa; color: var(--text-brown); font-family: var(--font-family); }
.mt-6 { margin-top: 1.5rem; } .mb-4 { margin-bottom: 1rem; } .mb-2 { margin-bottom: 0.5rem; } .text-3xl { font-size: 1.875rem; } .text-4xl { font-size: 2.25rem; } .text-leaf-green { color: var(--leaf-green); }

/* 배경 */
.cartoon-bg { position: absolute; inset: 0; z-index: 0; background: linear-gradient(to bottom, #4fc3f7 0%, #b3e5fc 100%); }
.ground-back { position: absolute; bottom: 0; width: 100%; height: 35%; background: #9ccc65; transform: skewY(-2deg); transform-origin: bottom right; }
.ground-front { position: absolute; bottom: -50px; width: 100%; height: 30%; background: #c5e1a5; border-top: 10px solid #aed581; border-radius: 50% 50% 0 0 / 10% 10% 0 0; }
/* style scoped 내부 */
/* HomeView.vue의 style scoped 내부 */

/* [수정] 공통 기본 스타일 (움직임, 위치) */
.cloud {
  position: absolute;
  background: #fff; 
  left: 110%; /* 시작 위치 */
  
  /* 애니메이션 (우측 -> 좌측) */
  animation-name: moveClouds;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}
/* 공통 가상요소 설정 */
.cloud::after, .cloud::before {
  content: '';
  position: absolute;
  background: #fff;
  border-radius: 50%;
}

/* --- [신규] 구름 타입 1: 기본형 (기존과 비슷) --- */
.cloud-type-1 {
  width: 120px; height: 40px;
  border-radius: 20px;
}
.cloud-type-1::after { width: 50px; height: 50px; top: -25px; left: 15px; }
.cloud-type-1::before { width: 40px; height: 40px; top: -15px; left: 55px; }

/* --- [신규] 구름 타입 2: 길쭉하고 평평한 형 --- */
.cloud-type-2 {
  width: 150px; height: 35px;
  border-radius: 50px;
}
.cloud-type-2::after { width: 45px; height: 30px; top: -15px; left: 25px; border-radius: 50% 50% 0 0; }
.cloud-type-2::before { width: 60px; height: 40px; top: -20px; left: 60px; border-radius: 50% 50% 0 0; }

/* --- [신규] 구름 타입 3: 몽글몽글 뭉게구름형 --- */
.cloud-type-3 {
  width: 100px; height: 30px;
  border-radius: 15px;
  /* 베이스를 좀 작게 하고 덩어리를 크게 */
}
.cloud-type-3::after { width: 60px; height: 60px; top: -30px; left: 10px; }
.cloud-type-3::before { width: 50px; height: 50px; top: -20px; left: 50px; }
.cloud-type-3 {
    /* 덩어리 하나 더 추가 (box-shadow 이용) */
    box-shadow: 30px -10px 0 5px #fff; 
}


/* 이동 애니메이션 (기존 동일) */
@keyframes moveClouds {
  0% { left: 110%; }
  100% { left: -25%; }
}

/* 인트로 */
.intro-container { position: absolute; inset: 0; z-index: 20; display: flex; justify-content: center; align-items: center; }
.intro-panel { width: 100%; max-width: 500px; background: #fff8e1; border: 8px solid var(--wood-border); border-radius: 30px; padding: 50px 40px; box-shadow: 0 20px 0 rgba(0,0,0,0.15); text-align: center; }
.logo-icon { font-size: 80px; color: var(--leaf-green); margin-bottom: 10px; animation: bounce 2s infinite; }
.game-title { font-size: 5rem; margin: 0; color: var(--text-brown); text-shadow: 4px 4px 0 #fff; line-height: 1; }
.game-subtitle { font-size: 1.5rem; color: var(--wood-dark); font-family: 'Nanum Pen Script', cursive; margin-top: 5px; }
.input-area label { display: block; font-size: 1.4rem; margin-bottom: 15px; color: var(--text-brown); }
.input-area input { font-size: 1.8rem; text-align: center; }
.go-btn.large { font-size: 1.5rem; padding: 12px 30px; border-radius: 15px; }

/* 메인 컨텐츠 */
.main-container { position: absolute; inset: 0; z-index: 10; }
.active-content { position: absolute; z-index: 30; top: 10%; left: 50%; transform: translateX(-50%); width: 90%; max-width: 420px; text-align: center; }
.wood-panel { background: rgba(255, 248, 225, 0.9); backdrop-filter: blur(5px); border: 6px solid var(--wood-border); border-radius: 24px; padding: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.15); position: relative; }
.wood-panel::before, .wood-panel::after { content: ''; position: absolute; width: 12px; height: 12px; background: var(--wood-border); border-radius: 50%; top: 15px; }
.wood-panel::before { left: 15px; } .wood-panel::after { right: 15px; }
.content-header { display: flex; align-items: center; justify-content: center; gap: 12px; margin-bottom: 12px; }
.icon-circle { width: 48px; height: 48px; background: var(--leaf-green); border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 3px solid var(--leaf-dark); color: white; box-shadow: inset 0 -4px 0 rgba(0,0,0,0.2); }
.index-number { font-size: 1.4rem; color: var(--wood-dark); font-family: 'Nanum Pen Script', cursive; }
h1.wood-text { font-size: 2.8rem; margin: 0; line-height: 1.1; color: var(--text-brown); text-shadow: 2px 2px 0px rgba(0,0,0,0.1); }
.description { margin-top: 8px; font-size: 1rem; color: var(--wood-dark); line-height: 1.3; }

/* 룰렛: 하단 고정, 초대형 */
.roulette-wrapper {
  position: absolute; z-index: 5;
  width: 2000px; height: 2000px;
  bottom: -1700px; left: 50%;
  transform: translateX(-50%);
}

.wheel-shadow-static { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border-radius: 50%; background: rgba(0,0,0,0.15); z-index: -1; filter: blur(20px); transform: scale(0.95); }
.roulette-wheel { position: relative; width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; }

/* [수정] 룰렛 배경: overflow hidden 제거! 핀이 밖으로 보여야 하니까요 */
/* (참고) 기존 wood-wheel-enhanced에서 overflow: hidden은 유지해도 됩니다. 
   핀이 밖으로 나왔으니까요. */
.wood-wheel-enhanced {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  border-radius: 50%; border: 30px solid #8d6e63;
  background: repeating-conic-gradient(#f5f5f5 0deg 30deg, #d7ccc8 30deg 60deg);
  /* box-shadow: inset 0 0 100px rgba(93, 64, 55, 0.5), 0 20px 40px rgba(0,0,0,0.3); */
  overflow: hidden; /* 배경 무늬 정리용 */
}
.wood-texture-overlay { position: absolute; inset: 0; border-radius: 50%; background: repeating-radial-gradient(circle, transparent 0, transparent 20px, rgba(93, 64, 55, 0.05) 20px, rgba(93, 64, 55, 0.05) 22px); mix-blend-mode: multiply; pointer-events: none; overflow: hidden; /* 오버레이만 잘리게 */ }

/* [수정] 핀 컨테이너 및 핀 스타일 */
/* style scoped 내부에 추가/수정하세요 */

/* 핀 컨테이너: 룰렛 전체를 덮음 */
.pins-container {
    position: absolute; 
    top: 0; left: 0; 
    width: 100%; height: 100%;
    pointer-events: none;
    z-index: 20; /* 나무 배경(z-index:0)보다 위에 */
}

/* 개별 핀 디자인 */
.pin {
    position: absolute;
    top: 50%; left: 50%;
    width: 22px; height: 22px;
    
    /* [수정] 노란색 그라데이션으로 입체감 추가 */
    background-color: #d4d42e; 
    
    border: 2px solid #d4d42e;
    border-radius: 50%;
    box-shadow: 0 4px 6px rgba(0,0,0,0.5);

    margin-top: -11px;
    margin-left: -11px;
}


.character-sticker {
    position: absolute; top: -200px; left: 50%; transform: translateX(-50%);
    width: 600px; height: 300px; z-index: 25;
}

/* 화살표 */
/* style scoped 내부 */

.central-arrow {
  position: absolute;
  top: 0; 
  left: 50%;
  
  /* 크기 설정 */
  width: 90px; 
  height: 220px; 

  /* 회전 기준점: 화살표의 상단 핀 부분 (위에서 30px 지점) */
  /* SVG 내부 좌표계에 맞춰서 설정 */
  transform-origin: 50% 25%; 
  
  /* 초기 위치 잡기 */
  transform: translate(-50%, -63%);
  z-index: 25; 
  
  /* 2D 느낌을 위해 단순한 그림자 하나만 딱 줍니다 (선택사항) */
  filter: drop-shadow(2px 4px 0px rgba(0,0,0,0.2));
}

/* .central-arrow svg {
    width: 100%;
    height: 100%;
    overflow: visible;
} */

.scroll-indicator { position: absolute; bottom: 40px; left: 50%; transform: translateX(-50%); font-size: 1.2rem; color: white; text-shadow: 2px 2px 0 rgba(0,0,0,0.2); display: flex; align-items: center; gap: 8px; z-index: 10; }
.arrow-icon { animation: bounce 1s infinite alternate; }
.animate-pulse-slow { animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
.shake-anim { animation: shake 0.4s cubic-bezier(.36,.07,.19,.97) both; }
.bounce-in { animation: bounceIn 0.8s; }
@keyframes bounce { from { transform: translateY(0); } to { transform: translateY(-8px); } }
@keyframes shake { 10%, 90% { transform: translate3d(-2px, 0, 0); } 20%, 80% { transform: translate3d(4px, 0, 0); } }
@keyframes pulse { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: .95; transform: scale(0.98); } }
@keyframes bounceIn { 0% { transform: scale(0.3); opacity: 0; } 50% { transform: scale(1.05); } 70% { transform: scale(0.9); } 100% { transform: scale(1); opacity: 1; } }
.intro-fade-enter-active, .intro-fade-leave-active { transition: opacity 0.5s ease, transform 0.5s ease; }
.intro-fade-enter-from { opacity: 0; transform: scale(0.9); }
.intro-fade-leave-to { opacity: 0; transform: scale(1.1); pointer-events: none; }
.main-slide-enter-active { transition: all 0.8s cubic-bezier(0.34, 1.56, 0.64, 1); }
.main-slide-enter-from { opacity: 0; transform: translateY(100px); }
.menu-fade-enter-active, .menu-fade-leave-active { transition: all 0.2s ease; }
.menu-fade-enter-from { opacity: 0; transform: scale(0.95); }
.menu-fade-leave-to { opacity: 0; transform: scale(1.05); }


</style>