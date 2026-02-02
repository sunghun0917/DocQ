<script setup lang="ts">
import { ref, computed } from 'vue';

const emit = defineEmits(['close']);

const slides = [
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
    title: "아이템 & 미니게임",
    desc: "2배 타일, 버프, 미니게임 등\n매 판 다른 변수가 등장해요!",
    images: ["/images/buff.png", "/images/minigame.png"],
    icon: "bolt",
    color: "#42a5f5"
  }
];

const currentSlide = ref(0);

const nextSlide = () => {
  currentSlide.value = (currentSlide.value + 1) % slides.length;
};
const prevSlide = () => {
  currentSlide.value = (currentSlide.value - 1 + slides.length) % slides.length;
};

const activeSlide = computed(() => slides[currentSlide.value]!);
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center font-display">
    <!-- Backdrop -->
    <div
      class="absolute inset-0 bg-black/40 backdrop-blur-sm"
      @click="$emit('close')"
    ></div>

    <!-- Modal -->
    <div
      class="relative w-[1100px] h-[700px] bg-[#fff8e1]
             rounded-[32px] border-[6px] border-[#8d6e63]
             shadow-[0_12px_35px_rgba(0,0,0,0.25)]
             flex flex-col overflow-hidden"
    >
      <!-- Screws -->
      <div class="absolute top-5 left-5 w-4 h-4 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]" />
      <div class="absolute top-5 right-5 w-4 h-4 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]" />
      <div class="absolute bottom-5 left-5 w-4 h-4 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]" />
      <div class="absolute bottom-5 right-5 w-4 h-4 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]" />

      <!-- ================= HEADER ================= -->
      <header class="pt-10 pb-16 text-center">
        <h2 class="text-4xl text-[#5d4037] tracking-wide drop-shadow-sm" style="padding-top: 20px">
          게임 가이드
        </h2>
      </header>

      <!-- ================= CONTENT ================= -->
      <main class="flex-1 flex flex-col items-center justify-center relative px-16">
        <!-- Arrows -->
        <button
          @click="prevSlide"
          class="absolute left-[-20px] top-1/2 -translate-y-1/2 z-20 p-2 hover:bg-black/5 rounded-full transition group"
        >
          <span class="material-symbols-outlined text-[#8d6e63] group-hover:scale-110 transition-transform" style="font-size: 100px;">
            chevron_left
          </span>
        </button>

        <button
          @click="nextSlide"
          class="absolute right-[-20px] top-1/2 -translate-y-1/2 z-20 p-2 hover:bg-black/5 rounded-full transition group"
        >
          <span class="material-symbols-outlined text-[#8d6e63] group-hover:scale-110 transition-transform" style="font-size: 100px;">
            chevron_right
          </span>
        </button>

        <transition name="slide-fade" mode="out-in">
          <div :key="currentSlide" class="flex flex-col items-center gap-8">
            <!-- Image -->
            <!-- Image Area -->
            <!-- 1. Two images (Stacked Cards) -->
            <div v-if="activeSlide?.images" class="relative w-[640px] h-[360px]">
                <!-- Back Image (Top-Left) -->
                <div class="absolute top-0 left-4 w-[58%] h-[80%] z-10 rounded-2xl shadow-[2px_4px_12px_rgba(0,0,0,0.3)] overflow-hidden bg-[#bcaaa4]">
                    <img :src="activeSlide.images[0]" class="w-full h-full object-cover" />
                </div>
                <!-- Front Image (Bottom-Right) -->
                <div class="absolute bottom-0 right-4 w-[58%] h-[80%] z-20 rounded-2xl shadow-[4px_8px_16px_rgba(0,0,0,0.4)] overflow-hidden bg-[#bcaaa4]">
                    <img :src="activeSlide.images[1]" class="w-full h-full object-cover" />
                </div>
            </div>

            <!-- 2. Single Image -->
            <div
              v-else
              class="w-[640px] h-[360px] rounded-2xl overflow-hidden
                     border-[5px] border-[#8d6e63] bg-[#bcaaa4] shadow-lg relative"
            >
              <img
                v-if="activeSlide.image"
                :src="activeSlide.image"
                class="w-full h-full object-cover"
              />
            </div>

            <!-- Text -->
            <div class="text-center flex flex-col gap-3">
              <div class="flex items-center justify-center gap-3">
                <div
                  class="w-10 h-10 rounded-full flex items-center justify-center
                         border-2 border-[#5d4037]"
                  :style="{ backgroundColor: activeSlide.color }"
                >
                  <span class="material-symbols-outlined text-white">
                    {{ activeSlide.icon }}
                  </span>
                </div>
                <h3 class="text-3xl text-[#5d4037] font-bold">
                  {{ activeSlide.title }}
                </h3>
              </div>

              <p
                class="text-2xl text-[#8d6e63] whitespace-pre-line leading-relaxed"
              >
                {{ activeSlide.desc }}
              </p>
            </div>
          </div>
        </transition>
      </main>

      <!-- ================= INDICATOR ================= -->
      <div class="flex justify-center gap-3 py-6" style="padding-bottom: 20px">
        <div
          v-for="(_, i) in slides"
          :key="i"
          class="w-4 h-4 rounded-full transition-all"
          :class="i === currentSlide ? 'bg-[#5d4037] scale-125' : 'bg-[#d7ccc8]'"
        />
      </div>

      <!-- ================= FOOTER ================= -->
      <footer class="mt-auto px-10 pb-10 pt-6 border-t-2 border-[#d7ccc8]">
        <button
          @click="$emit('close')"
          class="cartoon-btn secondary w-full"
        >
          닫기
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
  height: 56px;
  border-radius: 16px;
  font-size: 1.4rem;
  color: white;
  background: #8d6e63;
  box-shadow: 0 5px 0 #5d4037;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cartoon-btn:active {
  transform: translateY(5px);
  box-shadow: none;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.25s ease;
}
.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(16px);
}
.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-16px);
}
</style>
