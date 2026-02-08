<script setup lang="ts">
import { ref, computed } from 'vue';

const emit = defineEmits(['close']);

// 타입 정의 (image와 images 모두 선택 속성으로)
interface SlideItem {
  title: string;
  desc: string;
  image?: string;
  images?: string[];
  items?: { image: string; label: string }[]; // 아이템 가이드용
  icon: string;
  color: string;
}

const slides: SlideItem[] = [
  {
    title: "목표: 완주!",
    desc: "퀴즈를 풀고 가장 먼저\n완주하는 사람이 승리!",
    image: "/images/map.png",
    icon: "flag",
    color: "#ffca28"
  },
  {
    title: "내 턴: 퀴즈 & 룰렛",
    desc: "정답을 맞히면 룰렛을 돌려\n나온 숫자만큼 이동합니다.",
    image: "/images/my_turn.png",
    icon: "casino",
    color: "#7cb342"
  },
  {
    title: "스틸 찬스 (Ghost)",
    desc: "턴 주인이 오답이면 기회!\n고스트 중 가장 빨리 맞히면 대신 이동합니다.",
    images: ["/images/steal_turn.png", "/images/steal_turn2.png"],
    icon: "sentiment_very_dissatisfied",
    color: "#ef5350"
  },
  {
    title: "미니게임 & 효과",
    desc: "2배 타일, 미니게임, 상대 잡기로\n짜릿한 역전을 노려보세요!",
    images: ["/images/buff.png", "/images/minigame.png"],
    icon: "videogame_asset",
    color: "#42a5f5"
  },
  {
    title: "아이템 가이드",
    desc: "미니게임 승리 시 아이템 획득 !\n아이템으로 판을 뒤집으세요.",
    items: [
      { image: "/images/boom.png", label: "꽝" },
      { image: "/images/banana.png", label: "바나나 설치\n(밟으면 뒤로 3칸)" },
      { image: "/images/plus.png", label: "앞으로 3칸 이동" },
      { image: "/images/quiz.png", label: "상대 퀴즈 방해" },
      { image: "/images/switch.png", label: "자리 바꾸기" }
    ],
    icon: "bolt",
    color: "#ab47bc"
  }
];

const currentSlide = ref(0);
const direction = ref<'next' | 'prev'>('next'); // 애니메이션 방향 제어

const nextSlide = () => {
  direction.value = 'next';
  currentSlide.value = (currentSlide.value + 1) % slides.length;
};

const prevSlide = () => {
  direction.value = 'prev';
  currentSlide.value = (currentSlide.value - 1 + slides.length) % slides.length;
};

const activeSlide = computed(() => slides[currentSlide.value]!);

import { onMounted, onUnmounted } from 'vue';

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    emit('close');
  }
};

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <div class="fixed inset-0 z-[100] flex items-center justify-center font-display">
    
    <div
      class="absolute inset-0 bg-black/40 backdrop-blur-sm"
      @click="$emit('close')"
    ></div>

    <div
      class="relative w-[650px] h-[660px] !p-4 bg-[#fff8e1]
             rounded-[32px] border-[6px] border-[#8d6e63]
             shadow-[0_12px_35px_rgba(0,0,0,0.25)]
             flex flex-col"
    >
      <div class="absolute top-5 left-5 w-4 h-4 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]" />
      <div class="absolute top-5 right-5 w-4 h-4 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]" />

      <header class="pt-10 pb-4 text-center shrink-0">
        <h2 class="text-4xl text-[#5d4037] tracking-wide drop-shadow-sm mt-4">
          게임 가이드
        </h2>
      </header>

      <main class="flex-1 flex flex-col items-center justify-center relative px-4 w-full">
        
        <button
          @click="prevSlide"
          class="absolute -left-28 top-1/2 -translate-y-1/2 z-120 p-2 bg-[#ffffff]/60 backdrop-blur-sm hover:bg-[#ffffff]/80 hover:scale-110 shadow-sm border border-white/40 rounded-full transition-all group"
        >
          <span class="material-symbols-outlined text-[#8d6e63] transition-transform" style="font-size: 80px;">
            chevron_left
          </span>
        </button>

        <button
          @click="nextSlide"
          class="absolute -right-28 top-1/2 -translate-y-1/2 z-120 p-2 bg-[#ffffff]/60 backdrop-blur-sm hover:bg-[#ffffff]/80 hover:scale-110 shadow-sm border border-white/40 rounded-full transition-all group"
        >
          <span class="material-symbols-outlined text-[#8d6e63] transition-transform" style="font-size: 80px;">
            chevron_right
          </span>
        </button>

        <transition :name="direction === 'next' ? 'slide-next' : 'slide-prev'" mode="out-in">
          <div :key="currentSlide" class="flex flex-col items-center gap-6 w-full max-w-[700px]">
            
            <!-- Case 1: Multiple Images (Steal Turn) -->
            <div v-if="activeSlide.images" class="relative w-[600px] h-[340px]">
                <div class="absolute top-0 left-8 w-[58%] h-[85%] z-10 rounded-2xl shadow-md border-4 border-[#8d6e63] bg-[#bcaaa4] overflow-hidden">
                    <img :src="activeSlide.images[0]" class="w-full h-full object-cover" />
                </div>
                <div class="absolute bottom-0 right-8 w-[58%] h-[85%] z-20 rounded-2xl shadow-xl border-4 border-[#8d6e63] bg-[#bcaaa4] overflow-hidden">
                    <img :src="activeSlide.images[1]" class="w-full h-full object-cover" />
                </div>
            </div>

            <!-- Case 2: Items Grid (Item Guide) -->
            <div v-else-if="activeSlide.items" class="relative w-[600px] h-[340px] bg-[#fff8e1] rounded-2xl border-[5px] border-[#8d6e63] px-8 py-4 flex flex-col justify-center shadow-lg overflow-hidden">
                <div class="flex flex-col justify-between h-full py-2 px-2">
                    <div v-for="(item, idx) in activeSlide.items" :key="idx" 
                         class="flex items-center gap-6">
                        <img :src="item.image" class="w-14 h-14 object-contain drop-shadow-sm shrink-0" />
                        <span class="text-3xl text-[#5d4037] font-bold leading-tight break-keep text-left">{{ item.label }}</span>
                    </div>
                </div>
            </div>

            <!-- Case 3: Single Image (Standard) -->
            <div
              v-else
              class="w-[600px] h-[340px] rounded-2xl overflow-hidden
                     border-[5px] border-[#8d6e63] bg-[#bcaaa4] shadow-lg relative"
            >
              <img
                v-if="activeSlide.image"
                :src="activeSlide.image"
                class="w-full h-full object-cover"
              />
            </div>

            <div class="text-center flex flex-col gap-3 mt-2">
              <div class="flex items-center justify-center gap-3">
                <div
                  class="w-10 h-10 rounded-full flex items-center justify-center
                         border-2 border-[#5d4037] shadow-sm"
                  :style="{ backgroundColor: activeSlide.color }"
                >
                  <span class="material-symbols-outlined text-white text-2xl">
                    {{ activeSlide.icon }}
                  </span>
                </div>
                <h3 class="text-3xl text-[#5d4037] font-black">
                  {{ activeSlide.title }}
                </h3>
              </div>

              <p class="text-2xl text-[#8d6e63] whitespace-pre-line leading-relaxed font-bold">
                {{ activeSlide.desc }}
              </p>
            </div>
          </div>
        </transition>
      </main>

      <div class="flex justify-center gap-3 py-6 shrink-0">
        <div
          v-for="(_, i) in slides"
          :key="i"
          class="w-3.5 h-3.5 rounded-full transition-all duration-300"
          :class="i === currentSlide ? 'bg-[#5d4037] scale-125' : 'bg-[#d7ccc8] hover:bg-[#bcaaa4]'"
        ></div>
      </div>
      <footer class="mt-auto px-10 !pb-8 !pt-4 border-[#d7ccc8]/50 shrink-0 flex flex-col items-center gap-4">
        <button v-if="currentSlide === slides.length - 1" @click="$emit('close')"
          class="cartoon-btn primary !px-12 !py-3 !text-xl shadow-[0_4px_0_#558b2f] animate-bounce-slight">
          START
        </button>
        <button v-else @click="nextSlide"
          class="cartoon-btn secondary !px-12 !py-3 !text-xl shadow-[0_4px_0_#8d6e63]">
          NEXT
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-display {
  font-family: 'Jua', sans-serif;
}

.cartoon-btn {
  border-radius: 12px;
  font-weight: 900;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.1s;
}
.cartoon-btn:active {
  transform: translateY(4px);
  box-shadow: none !important;
}
.cartoon-btn.primary { background: #7cb342; }
.cartoon-btn.primary:hover { background: #689f38; }

.cartoon-btn.secondary { background: #8d6e63; }
.cartoon-btn.secondary:hover { background: #795548; }

/* Next Animation (오른쪽 -> 왼쪽) */
.slide-next-enter-active,
.slide-next-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.5, 1);
}
.slide-next-enter-from {
  opacity: 0;
  transform: translateX(50px);
}
.slide-next-leave-to {
  opacity: 0;
  transform: translateX(-50px);
}

/* Prev Animation (왼쪽 -> 오른쪽) */
.slide-prev-enter-active,
.slide-prev-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.5, 1);
}
.slide-prev-enter-from {
  opacity: 0;
  transform: translateX(-50px);
}
.slide-prev-leave-to {
  opacity: 0;
  transform: translateX(50px);
}

.animate-bounce-slight {
  animation: bounceSlight 2s infinite;
}
@keyframes bounceSlight {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
</style>