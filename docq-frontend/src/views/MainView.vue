<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { gsap, Observer } from 'gsap/all';
import { useUserStore } from '@/stores/userStore';
import { getErrorMessage } from '@/services/api';

// 컴포넌트 임포트 (경로 확인 필요)
import CharacterBackground from '@/components/lobby/CharacterBackground.vue';
import AlertModal from '@/components/common/AlertModal.vue';
import CreateRoomModal from '@/components/lobby/CreateRoomModal.vue';
import JoinRoomModal from '@/components/lobby/JoinRoomModal.vue';

gsap.registerPlugin(Observer);

// --- 1. 데이터 ---
const menuItems = [
  { id: 'profile', label: '내 정보', description: '닉네임을 수정할 수 있어요.', icon: 'face' },
  { id: 'create', label: '방 만들기', description: '새로운 게임 방을 만들어요.', icon: 'add_circle' },
  { id: 'join', label: '참여하기', description: '코드를 입력해 방에 들어가요.', icon: 'login' },
  { id: 'guide', label: '게임 가이드', description: '게임 방법을 알아볼까요?', icon: 'menu_book' },
  { id: 'stack', label: '기술 스택', description: '개발 도구 정보입니다.', icon: 'code_blocks' },
  { id: 'team', label: '만든 사람들', description: '우리를 소개합니다!', icon: 'groups' },
];

const totalItems = menuItems.length; // [중요] 변수 선언 위치 확인
const anglePerItem = 360 / totalItems;

const userStore = useUserStore();
const nickname = ref('');
const isNicknameSet = ref(false);
const showCreateModal = ref(false);
const showJoinModal = ref(false);
const isErrorModalOpen = ref(false);
const errorMessage = ref('');
const isShaking = ref(false);

// --- 2. 룰렛 변수 ---
const wheelRef = ref<HTMLElement | null>(null);
const arrowRef = ref<HTMLElement | null>(null);
const activeIndex = ref(0);

let currentRotation = 0;
let targetRotation = 0;
let isInteracting = false;
let idleTimer: ReturnType<typeof setTimeout>;

// 3D 캐릭터 속도 & 틱 애니메이션
const rouletteSpeed = ref(0);
let lastSectionIndex = 0;
let lastDelta = 0; 

// --- 3. Computed Props ---
const activeItem = computed(() => itemAt(activeIndex.value));
const prevItemData = computed(() => itemAt(activeIndex.value - 1));
const nextItemData = computed(() => itemAt(activeIndex.value + 1));

// 안전하게 아이템 가져오는 헬퍼 함수
const itemAt = (index: number) => {
    // 음수 인덱스 처리 및 범위 보정
    const adjustedIndex = ((index % totalItems) + totalItems) % totalItems;
    return menuItems[adjustedIndex] || menuItems[0];
};

// --- 4. 핵심 로직 ---

// [수정] 아이템 위치 계산 함수 (안전장치 추가)
const getItemPosition = (index: number) => {
    if (!menuItems || menuItems.length === 0) return 'pos-hidden';
    
    const active = activeIndex.value;
    const total = totalItems;
    
    // 현재 activeIndex를 기준으로 상대적 거리 계산
    let diff = (index - active + total) % total;
    
    if (diff === 0) return 'pos-center';       
    if (diff === 1) return 'pos-right';        
    if (diff === total - 1) return 'pos-left'; 
    return 'pos-hidden';
};

// [회전 로직] 최단 경로 이동 (인덱스 기반)
const goToIndex = (targetIndex: number) => {
    isInteracting = true;

    // 현재 각도에서 가장 가까운 '현재 인덱스' 역산
    const currentRawIndex = -targetRotation / anglePerItem;
    const currentRoundIndex = Math.round(currentRawIndex);
    const currentVisibleIndex = ((currentRoundIndex % totalItems) + totalItems) % totalItems;

    let diff = targetIndex - currentVisibleIndex;

    // 지름길 보정
    if (diff > totalItems / 2) diff -= totalItems;
    if (diff < -totalItems / 2) diff += totalItems;

    // 목표 각도 설정 (누적 인덱스 + 차이)
    targetRotation = -(currentRoundIndex + diff) * anglePerItem;

    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => isInteracting = false, 1000);
};

// 버튼 액션: 이전/다음 (물리적 한 칸 이동)
const prevItem = () => {
    isInteracting = true;
    targetRotation += anglePerItem; 
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => isInteracting = false, 1000);
};

const nextItem = () => {
    isInteracting = true;
    targetRotation -= anglePerItem;
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => isInteracting = false, 1000);
};

// 사이드 카드 클릭 핸들러
const clickItem = (index: number) => {
    // 중앙 클릭 시 무시 (이미 활성화됨)
    if (index === activeIndex.value) return; 
    
    // 바로 옆이면 물리 이동, 아니면 최단거리 이동
    goToIndex(index);
};

// 틱 애니메이션
const playTickAnimation = (direction: number) => {
    if (!arrowRef.value) return;
    const tickAngle = direction > 0 ? -25 : 25;
    gsap.killTweensOf(arrowRef.value);
    gsap.to(arrowRef.value, {
        rotation: tickAngle, duration: 0.05, ease: "power1.out",
        onComplete: () => { gsap.to(arrowRef.value, { rotation: 0, duration: 0.3, ease: "elastic.out(1.2, 0.5)" }); }
    });
};

// 메인 루프 (항상 실행)
const updateLoop = () => {
  // 로그인 여부와 상관없이 배경 룰렛은 돕니다.
  if (!isInteracting) {
      targetRotation -= 0.05; // 자동 회전
  }
  
  const diff = targetRotation - currentRotation;
  currentRotation += diff * 0.08;

  const dynamicSpeed = Math.abs(diff) * 0.8;
  rouletteSpeed.value = dynamicSpeed < 0.05 ? 0 : 0.5 + dynamicSpeed; // 멈춤 처리

  if (Math.abs(diff) > 0.01) lastDelta = diff; 

  if (wheelRef.value) {
    gsap.set(wheelRef.value, { rotation: currentRotation });
  }

  // 핀 충돌 감지
  const pinSpacing = anglePerItem / 2;
  const collisionBias = 2;
  const absRotation = Math.abs(currentRotation);
  const currentPinIndex = Math.floor((absRotation + collisionBias) / pinSpacing);

  if (currentPinIndex !== lastSectionIndex) {
      playTickAnimation(lastDelta);
      lastSectionIndex = currentPinIndex;
  }

  // 인덱스 업데이트
  let rawIndex = Math.round(-currentRotation / anglePerItem);
  let index = ((rawIndex % totalItems) + totalItems) % totalItems;

  if (activeIndex.value !== index) {
    activeIndex.value = index;
  }
};

// 룰렛 초기화
let observer: any = null;
const startRoulette = () => {
    if (observer) return;
    gsap.ticker.add(updateLoop);
    observer = Observer.create({
        target: window,
        type: "wheel,touch,pointer",
        wheelSpeed: -1.3,
        // 로그인 전에는 클릭으로 멈추지 않게 함
        onPress: () => { 
            if (!isNicknameSet.value) return; 
            isInteracting = true; clearTimeout(idleTimer); 
        },
        onRelease: () => { 
            if (!isNicknameSet.value) return; 
            clearTimeout(idleTimer); idleTimer = setTimeout(() => isInteracting = false, 1000); 
        },
        onChange: (self: any) => {
            if (!isNicknameSet.value) return; // 로그인 전 휠 막기
            isInteracting = true;
            const rawDelta = self.deltaY;
            if (Math.abs(rawDelta) < 8) return;
            const direction = rawDelta > 0 ? 1 : -1;
            targetRotation += direction * anglePerItem; // 스크롤 1회당 1칸 이동
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

// 기타 핸들러
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
    errorMessage.value = getErrorMessage((result as any).error);
    isErrorModalOpen.value = true;
    return;
  }
  isNicknameSet.value = true;
};

const triggerShake = () => {
  isShaking.value = true;
  setTimeout(() => isShaking.value = false, 500);
};

const syncNicknameFromStore = () => {
  if (userStore.isAuthenticated && userStore.username && userStore.username !== 'Guest') {
    nickname.value = userStore.username;
    isNicknameSet.value = true;
  }
};

const handleAction = (action: string) => {
  if (action === 'create') showCreateModal.value = true;
  if (action === 'join') showJoinModal.value = true;
  if (action === 'profile') {
      userStore.logout();
      nickname.value = '';
      isNicknameSet.value = false;
  }
};

// 구름 생성
const clouds = ref<{ id: number; typeId: number; style: any }[]>([]);
const generateClouds = () => {
  const cloudCount = 12;
  const newClouds = [];
  for (let i = 0; i < cloudCount; i++) {
    const sizeScale = 0.6 + Math.random() * 0.8; 
    const duration = 25 + Math.random() * 35; 
    const delay = -(Math.random() * duration); 
    const typeId = Math.floor(Math.random() * 3) + 1;
    newClouds.push({
      id: i, typeId: typeId,
      style: {
        top: `${2 + Math.random() * 35}%`,
        opacity: 0.7 + Math.random() * 0.3,
        transform: `scale(${sizeScale})`,
        animationDuration: `${duration}s`,
        animationDelay: `${delay}s`,
      }
    });
  }
  clouds.value = newClouds;
};

onMounted(() => { 
    userStore.restoreUser(); 
    syncNicknameFromStore();
    generateClouds();
    startRoulette();
});
onUnmounted(() => { stopRoulette(); });
</script>

<template>
  <div class="page-container cartoon-theme">
    
    <div class="cartoon-bg">
      <div class="sky"></div>
      <div v-for="cloud in clouds" :key="cloud.id" :class="['cloud', `cloud-type-${cloud.typeId}`]" :style="cloud.style"></div>
      <div class="ground-back"></div>
      <div class="ground-front"></div>
      <div class="decoration tree-1"></div>
      <div class="decoration tree-2"></div>
    </div>

    <div class="roulette-wrapper">
        <div class="wheel-shadow-static"></div>
        <div class="roulette-wheel" ref="wheelRef">
            <div class="wood-wheel-enhanced"><div class="wood-texture-overlay"></div></div>
            <div class="pins-container">
                <div v-for="n in (totalItems * 2)" :key="'pin-'+n" class="pin"
                    :style="{ transform: `rotate(${(n - 1) * (anglePerItem / 2)}deg) translateY(-985px)` }"></div>
            </div>
        </div>
        <div class="character-sticker"><CharacterBackground :speed="rouletteSpeed" /></div>
        <div class="central-arrow" ref="arrowRef">
            <svg viewBox="0 0 100 300" xmlns="http://www.w3.org/2000/svg">
                <g><path d="M 50 200 L 15 30 L 85 30 Z" fill="#ff5252" /></g>
            </svg>
        </div>
    </div>

    <transition name="intro-fade">
        <div v-if="!isNicknameSet" class="intro-container">
            <div class="intro-panel wood-panel">
                <div class="logo-area">
                    <h1 class="game-title">DocQ</h1>
                    <p class="game-subtitle">PDF 퀴즈 배틀</p>
                </div>
                <div class="input-area">
                    <div class="input-wrapper">
                        <input type="text" v-model="nickname" placeholder="닉네임을 입력하세요" @keyup.enter="confirmNickname" autofocus />
                    </div>
                    <button @click="confirmNickname" class="cartoon-btn primary large-btn">
                        <span>START</span>
                        <span class="material-symbols-outlined">play_arrow</span>
                    </button>
                </div>
            </div>
        </div>
    </transition>

    <transition name="main-slide">
        <div v-if="isNicknameSet" class="main-container">
            
            <div class="active-content">
                <div class="carousel-stage">
                    <div 
                        v-for="(item, index) in menuItems" 
                        :key="item.id"
                        class="carousel-item"
                        :class="getItemPosition(index)"
                        @click="clickItem(index)"
                    >
                        <div class="panel-content" :class="{ 'main-mode': index === activeIndex, 'side-mode': index !== activeIndex }">
                            
                            <div v-if="index === activeIndex" class="full-content" @click.stop>
                                <div class="content-header">
                                    <div class="icon-circle"><span class="leaf-icon material-symbols-outlined">{{ item.icon }}</span></div>
                                    <span class="index-number">Menu {{ index + 1 }} / {{ totalItems }}</span>
                                </div>
                                <h1 class="wood-text">{{ item.label }}</h1>
                                <p class="description">{{ item.description }}</p>

                                <div class="action-area mt-6">
                                    <div v-if="item.id === 'profile'">
                                        <div class="welcome-msg mb-4"><span class="material-symbols-outlined check-icon">check_circle</span><span><span class="highlight">{{ nickname }}</span>님</span></div>
                                        <button @click.stop="handleAction('profile')" class="cartoon-btn secondary">
                                            <span class="material-symbols-outlined">Edit</span>수정하기
                                        </button>
                                    </div>
                                    <div v-else-if="item.id === 'create'">
                                        <button @click.stop="handleAction('create')" class="cartoon-btn primary large-btn animate-pulse-slow">
                                            <span class="material-symbols-outlined text-3xl">add_circle</span>방 만들기
                                        </button>
                                    </div>
                                    <div v-else-if="item.id === 'join'">
                                        <button @click.stop="handleAction('join')" class="cartoon-btn primary large-btn">
                                            <span class="material-symbols-outlined text-3xl">login</span>참여하기
                                        </button>
                                    </div>
                                    <div v-else class="info-msg"><span class="material-symbols-outlined text-4xl text-leaf-green mb-2">touch_app</span><p>Comming Soon !</p></div>
                                </div>
                            </div>

                            <div v-else class="mini-content">
                                <span class="material-symbols-outlined side-icon">{{ item.icon }}</span>
                                <span class="side-label">{{ item.label }}</span>
                            </div>

                        </div>
                    </div>
                </div>
            </div>

            <div class="bottom-nav-area">
                <div class="scroll-indicator wood-text">
                    <span class="material-symbols-outlined arrow-icon">keyboard_double_arrow_up</span>
                    SCROLL TO DISCOVER
                    <span class="material-symbols-outlined arrow-icon">keyboard_double_arrow_up</span>
                </div>
                <div class="nav-bar-container">
                    <div 
                        v-for="(item, index) in menuItems" 
                        :key="'nav-' + item.id"
                        class="nav-dot"
                        :class="{ active: index === activeIndex }"
                        @click="goToIndex(index)"
                    >
                        <span class="material-symbols-outlined nav-icon">{{ item.icon }}</span>
                        <span class="nav-tooltip">{{ item.label }}</span>
                    </div>
                </div>
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

/* 구름 */
.cloud { position: absolute; background: #fff; left: 110%; animation-name: moveClouds; animation-timing-function: linear; animation-iteration-count: infinite; }
.cloud::after, .cloud::before { content: ''; position: absolute; background: #fff; border-radius: 50%; }
.cloud-type-1 { width: 120px; height: 40px; border-radius: 20px; }
.cloud-type-1::after { width: 50px; height: 50px; top: -25px; left: 15px; }
.cloud-type-1::before { width: 40px; height: 40px; top: -15px; left: 55px; }
.cloud-type-2 { width: 150px; height: 35px; border-radius: 50px; }
.cloud-type-2::after { width: 45px; height: 30px; top: -15px; left: 25px; border-radius: 50% 50% 0 0; }
.cloud-type-2::before { width: 60px; height: 40px; top: -20px; left: 60px; border-radius: 50% 50% 0 0; }
.cloud-type-3 { width: 100px; height: 30px; border-radius: 15px; box-shadow: 30px -10px 0 5px #fff; }
.cloud-type-3::after { width: 60px; height: 60px; top: -30px; left: 10px; }
.cloud-type-3::before { width: 50px; height: 50px; top: -20px; left: 50px; }
@keyframes moveClouds { 0% { left: 110%; } 100% { left: -25%; } }

/* 인트로 컨테이너: 상단 7vh (5% Up) */
.intro-container { position: absolute; inset: 0; z-index: 20; display: flex; justify-content: center; align-items: flex-start; padding-top: 7vh; }
.intro-panel { 
    width: 90%; max-width: 340px; 
    background: #fff8e1; border: 5px solid var(--wood-border); border-radius: 24px; 
    padding: 25px; 
    box-shadow: 0 10px 0 rgba(93, 64, 55, 0.2), 0 20px 40px rgba(0,0,0,0.15); 
    text-align: center; display: flex; flex-direction: column; gap: 15px;
    animation: floating 3s ease-in-out infinite;
}
@keyframes floating {
    0%, 100% { transform: translateY(0px); box-shadow: 0 10px 0 rgba(93, 64, 55, 0.2), 0 20px 40px rgba(0,0,0,0.15); }
    50% { transform: translateY(-12px); box-shadow: 0 25px 0 rgba(93, 64, 55, 0.1), 0 40px 60px rgba(0,0,0,0.1); }
}

.logo-area { margin-bottom: 5px; }
.game-title { font-size: 3.5rem; margin: 0; color: var(--text-brown); text-shadow: 3px 3px 0 #fff; line-height: 1; }
.game-subtitle { font-size: 1.1rem; color: var(--wood-dark); font-family: 'Nanum Pen Script', cursive; margin-top: 5px; opacity: 0.8; }

.input-area { display: flex; flex-direction: column; gap: 12px; }
.input-wrapper { position: relative; border-bottom: 3px solid #d7ccc8; transition: all 0.3s; }
.input-wrapper:focus-within { border-bottom-color: var(--leaf-green); }
.input-area input { width: 100%; border: none; background: transparent; padding: 10px; font-size: 1.3rem; text-align: center; color: var(--text-brown); font-family: var(--font-family); outline: none; }
.input-area input::placeholder { color: #bcaaa4; font-size: 1.1rem; }

/* 버튼 스타일 (공통) */
.cartoon-btn {
    width: 100%; padding: 12px 20px; border: none; border-radius: 16px;
    font-family: 'Jua', sans-serif; font-size: 1.4rem; color: white;
    cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px;
    transition: all 0.1s ease; position: relative; top: 0;
}
.cartoon-btn.primary { background: #7cb342; box-shadow: 0 5px 0 #558b2f; }
.cartoon-btn.primary:hover { background: #8bc34a; }
.cartoon-btn.primary:active { top: 5px; box-shadow: 0 0 0 #558b2f; }

.cartoon-btn.secondary { background: #ff7043; box-shadow: 0 5px 0 #d84315; }
.cartoon-btn.secondary:hover { background: #ff8a65; }
.cartoon-btn.secondary:active { top: 5px; box-shadow: 0 0 0 #d84315; }

/* 메인 컨텐츠 */
.main-container { position: absolute; inset: 0; z-index: 10; }
.active-content { position: absolute; z-index: 30; top: 5%; left: 50%; transform: translateX(-50%); width: 90%; max-width: 420px; text-align: center; }
.carousel-stage { position: relative; width: 100%; height: 450px; perspective: 1000px; display: flex; justify-content: center; align-items: center; }
.carousel-item { position: absolute; top: 50%; left: 50%; transition: all 0.5s cubic-bezier(0.25, 0.8, 0.25, 1); transform-style: preserve-3d; }

.pos-center { width: 420px; z-index: 20; opacity: 1; transform: translate(-50%, -50%) scale(1) rotateY(0deg); pointer-events: auto; }
.pos-left { width: 420px; z-index: 10; opacity: 0.7; transform: translate(-120%, -50%) scale(0.65) rotateY(25deg); cursor: pointer; }
.pos-left:hover { opacity: 0.9; transform: translate(-120%, -50%) scale(0.7) rotateY(25deg); }
.pos-right { width: 420px; z-index: 10; opacity: 0.7; transform: translate(20%, -50%) scale(0.65) rotateY(-25deg); cursor: pointer; }
.pos-right:hover { opacity: 0.9; transform: translate(20%, -50%) scale(0.7) rotateY(-25deg); }
.pos-hidden { width: 420px; z-index: 0; opacity: 0; transform: translate(-50%, -50%) scale(0.4); pointer-events: none; }

.panel-content { width: 100%; height: 100%; transition: all 0.5s ease; background: rgba(255, 248, 225, 0.9); border: 6px solid var(--wood-border); border-radius: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.15); backdrop-filter: blur(5px); overflow: hidden; }
.panel-content.main-mode { padding: 24px; background: rgba(255, 248, 225, 0.95); }
.panel-content.main-mode::before, .panel-content.main-mode::after { content: ''; position: absolute; width: 12px; height: 12px; background: var(--wood-border); border-radius: 50%; top: 15px; }
.panel-content.main-mode::before { left: 15px; } .panel-content.main-mode::after { right: 15px; }
.panel-content.side-mode { display: flex; flex-direction: column; justify-content: center; align-items: center; background: rgba(255, 248, 225, 0.5); border-width: 4px; height: 300px; margin-top: 75px; }
.mini-content { text-align: center; display: flex; flex-direction: column; gap: 10px; }
.side-icon { font-size: 4rem; color: var(--wood-dark); opacity: 0.8; }
.side-label { font-size: 1.5rem; font-family: 'Nanum Pen Script', cursive; color: var(--wood-dark); }

.content-header { display: flex; align-items: center; justify-content: center; gap: 12px; margin-bottom: 12px; }
.icon-circle { width: 48px; height: 48px; background: var(--leaf-green); border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 3px solid var(--leaf-dark); color: white; box-shadow: inset 0 -4px 0 rgba(0,0,0,0.2); }
.index-number { font-size: 1.4rem; color: var(--wood-dark); font-family: 'Nanum Pen Script', cursive; }
h1.wood-text { font-size: 2.8rem; margin: 0; line-height: 1.1; color: var(--text-brown); text-shadow: 2px 2px 0px rgba(0,0,0,0.1); }
.description { margin-top: 8px; font-size: 1rem; color: var(--wood-dark); line-height: 1.3; }

/* 룰렛 */
.roulette-wrapper { position: absolute; z-index: 5; width: 2000px; height: 2000px; bottom: -1700px; left: 50%; transform: translateX(-50%); }
.wheel-shadow-static { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border-radius: 50%; background: rgba(0,0,0,0.15); z-index: -1; filter: blur(20px); transform: scale(0.95); }
.roulette-wheel { position: relative; width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; }
.wood-wheel-enhanced { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border-radius: 50%; border: 30px solid #8d6e63; background: repeating-conic-gradient(#f5f5f5 0deg 30deg, #d7ccc8 30deg 60deg); overflow: hidden; }
.wood-texture-overlay { position: absolute; inset: 0; border-radius: 50%; background: repeating-radial-gradient(circle, transparent 0, transparent 20px, rgba(93, 64, 55, 0.05) 20px, rgba(93, 64, 55, 0.05) 22px); mix-blend-mode: multiply; pointer-events: none; overflow: hidden; }
.pins-container { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 20; }
.pin { position: absolute; top: 50%; left: 50%; width: 22px; height: 22px; background-color: #d4d42e; border: 2px solid #d4d42e; border-radius: 50%; box-shadow: 0 4px 6px rgba(0,0,0,0.5); margin-top: -11px; margin-left: -11px; }
.character-sticker { position: absolute; top: -200px; left: 50%; transform: translateX(-50%); width: 600px; height: 300px; z-index: 25; }
.central-arrow { position: absolute; top: 0; left: 50%; width: 90px; height: 220px; transform-origin: 50% 25%; transform: translate(-50%, -63%); z-index: 25; filter: drop-shadow(2px 4px 0px rgba(0,0,0,0.2)); }

/* 하단 네비게이션 */
.bottom-nav-area { position: absolute; bottom: 30px; left: 50%; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; gap: 15px; z-index: 50; }
.scroll-indicator { position: static; margin-bottom: 5px; font-size: 1.1rem; color: white; text-shadow: 2px 2px 0 rgba(0,0,0,0.3); display: flex; align-items: center; gap: 8px; }
.nav-bar-container { display: flex; justify-content: center; gap: 15px; padding: 12px 25px; background: rgba(255, 255, 255, 0.25); border-radius: 50px; backdrop-filter: blur(5px); box-shadow: 0 4px 15px rgba(0,0,0,0.1); border: 2px solid rgba(255,255,255,0.4); }
.nav-dot { position: relative; width: 45px; height: 45px; display: flex; align-items: center; justify-content: center; background: #efebe9; border-radius: 50%; cursor: pointer; transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); border: 2px solid #a1887f; color: #5d4037; }
.nav-icon { font-size: 24px; }
.nav-dot.active { background: var(--leaf-green); color: white; transform: scale(1.15) translateY(-5px); border-color: #33691e; box-shadow: 0 5px 10px rgba(0,0,0,0.2); }
.nav-tooltip { position: absolute; top: -40px; left: 50%; transform: translateX(-50%); background: rgba(93, 64, 55, 0.9); color: white; font-size: 0.85rem; padding: 5px 10px; border-radius: 8px; opacity: 0; pointer-events: none; transition: all 0.2s; white-space: nowrap; }
.nav-tooltip::after { content: ''; position: absolute; bottom: -5px; left: 50%; margin-left: -5px; border-width: 5px; border-style: solid; border-color: rgba(93, 64, 55, 0.9) transparent transparent transparent; }
.nav-dot:hover .nav-tooltip { opacity: 1; top: -45px; }

/* 애니메이션 */
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
@media (max-width: 768px) { .pos-left, .pos-right { opacity: 0; pointer-events: none; } .carousel-item { width: 90vw; } }
</style>