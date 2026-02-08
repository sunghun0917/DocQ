<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';

const emit = defineEmits(['close']);

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') emit('close');
};

onMounted(() => window.addEventListener('keydown', handleKeydown));
onUnmounted(() => window.removeEventListener('keydown', handleKeydown));

// 페이지 데이터 정의
const pages = [
  {
    id: 1,
    subtitle: "The Identity",
    title: "읽기만 하던 PDF,\n이제는 플레이하세요",
    desc: "PDF 파일을 업로드하면 AI가 핵심 내용을 분석해\n퀴즈로 바꿔주는 자동 퀴즈 생성 플랫폼",
    slogan: "\"지루한 PDF의 게임화(Gamification), 잠깐이면 충분합니다.\"",
    icon: "picture_as_pdf", // PDF 아이콘
    icon2: "sports_esports", // 게임 아이콘
    color: "#ffca28" // 노란색 (주목)
  },
  {
    id: 2,
    subtitle: "The Value",
    title: "룰렛을 돌려 전진하는\n보드게임 어드벤처",
    desc: "퀴즈를 맞히면 룰렛을 돌려 이동합니다.\n아이템과 함정, 다양한 타일 위에서 펼쳐지는 전략 승부!",
    subFeatures: ["룰렛 & 이동", "아이템 / 함정", "턴제 전략"],
    color: "#42a5f5" // 파란색 (전략/지능)
  },
  {
    id: 3,
    subtitle: "The Community",
    title: "함께 만들고 공유하는\n지식의 놀이터",
    desc: "나만의 퀴즈를 공유하고, 유익한 퀴즈는 저장하세요.\n좋아요와 댓글로 유저들과 소통하며 함께 성장합니다.",
    subFeatures: ["퀴즈 공유/저장", "댓글 & 좋아요", "지식 소통"],
    color: "#66bb6a" // 초록색 (소통/공유)
  }
];

const currentPage = ref(0);
const direction = ref<'next' | 'prev'>('next');

const nextPage = () => {
  if (currentPage.value < pages.length - 1) {
    direction.value = 'next';
    currentPage.value++;
  } else {
    emit('close'); // 마지막 페이지에서 다음 누르면 닫기
  }
};

const prevPage = () => {
  if (currentPage.value > 0) {
    direction.value = 'prev';
    currentPage.value--;
  }
};

const activePage = computed(() => pages[currentPage.value]!);
</script>

<template>
  <div class="fixed inset-0 z-[100] flex items-center justify-center font-jua">
    <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="$emit('close')"></div>

    <div class="relative w-[640px] h-[650px] bg-[#fff8e1] border-[6px] border-[#5d4037] rounded-[32px] shadow-2xl flex flex-col overflow-hidden">
      
      <div class="absolute top-4 left-4 w-3 h-3 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>
      <div class="absolute top-4 right-4 w-3 h-3 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>

      <header class="!pt-8 !pb-4 text-center shrink-0 relative">
        <span class="inline-block !px-4 !py-1 rounded-full text-white text-sm font-bold tracking-widest mb-2 shadow-sm"
              :style="{ backgroundColor: activePage.color }">
          {{ activePage.subtitle }}
        </span>
        <div class="h-2"></div>
        <h2 class="text-4xl text-[#5d4037] font-black leading-tight whitespace-pre-line drop-shadow-sm">
          {{ activePage.title }}
        </h2>
      </header>

      <main class="flex-1 relative flex items-center justify-center w-full px-12 overflow-hidden">
        
        <button v-if="currentPage > 0" @click="prevPage" 
          class="absolute left-4 z-20 !p-2 text-[#bcaaa4] hover:text-[#5d4037] hover:bg-[#5d4037]/10 rounded-full transition-all">
          <span class="material-symbols-outlined !text-6xl">chevron_left</span>
        </button>

        <button v-if="currentPage < pages.length - 1" @click="nextPage" 
          class="absolute right-4 z-20 !p-2 text-[#bcaaa4] hover:text-[#5d4037] hover:bg-[#5d4037]/10 rounded-full transition-all">
          <span class="material-symbols-outlined !text-6xl">chevron_right</span>
        </button>

        <transition :name="direction === 'next' ? 'slide-next' : 'slide-prev'" mode="out-in">
          
          <div :key="currentPage" class="w-full flex flex-col items-center gap-6">
            
            <div v-if="activePage.id === 1" class="flex items-center gap-6 mb-2">
              <div class="w-32 h-40 bg-white border-4 border-[#5d4037] rounded-xl flex flex-col items-center justify-center shadow-md rotate-[-6deg]">
                <span class="material-symbols-outlined !text-6xl text-red-500">picture_as_pdf</span>
                <span class="text-xs text-gray-400 mt-2 font-bold">Document</span>
              </div>
              
              <div class="flex flex-col items-center gap-1 text-[#5d4037] animate-pulse">
                <span class="material-symbols-outlined !text-4xl">arrow_forward</span>
                <span class="text-sm font-bold">AI Parsing</span>
              </div>

              <div class="w-40 h-32 bg-[#5d4037] border-4 border-[#8d6e63] rounded-xl flex flex-col items-center justify-center shadow-md rotate-[6deg]">
                <span class="material-symbols-outlined !text-6xl text-[#ffca28] animate-bounce">sports_esports</span>
                <span class="text-xs text-[#ffca28] mt-2 font-bold">Game Start!</span>
              </div>
            </div>

            <div v-else-if="activePage.id === 2" class="flex gap-4 mb-2">
               <div v-for="(feat, idx) in activePage.subFeatures" :key="idx"
                    class="w-36 h-36 bg-white border-4 border-[#42a5f5] rounded-full flex flex-col items-center justify-center shadow-[0_6px_0_#1e88e5] transform hover:-translate-y-2 transition-transform">
                  <span class="material-symbols-outlined !text-6xl text-[#1e88e5] mb-1">
                    {{ idx === 0 ? 'casino' : (idx === 1 ? 'videogame_asset' : 'timer_off') }}
                  </span>
                  <span class="text-sm font-bold text-[#5d4037]">{{ feat }}</span>
               </div>
            </div>

            <div v-else class="flex flex-col items-center mb-4 w-full max-w-md">
              
              <div class="w-64 bg-white border-4 border-[#66bb6a] rounded-2xl !p-4 shadow-lg !mb-6 rotate-1 hover:rotate-0 transition-transform cursor-default relative">
                 <div class="flex items-center gap-2 mb-3 border-b-2 border-gray-100 !pb-2">
                    <div class="w-8 h-8 rounded-full bg-[#f1f8e9] border-2 border-[#aed581] flex items-center justify-center text-xs">👤</div>
                    <div class="flex flex-col">
                       <div class="w-20 h-2 bg-[#e0e0e0] rounded-full"></div>
                       <div class="w-12 h-2 bg-[#f5f5f5] rounded-full mt-1"></div>
                    </div>
                 </div>
                 
                 <div class="flex flex-col gap-2 mb-4">
                    <div class="h-16 bg-[#fff8e1] rounded-lg border-2 border-[#ffecb3] flex items-center justify-center">
                       <span class="text-[#fbc02d] font-black !text-2xl">Q.</span>
                    </div>
                 </div>

                 <div class="flex justify-between items-center !px-1">
                    <div class="flex gap-3">
                       <span class="material-symbols-outlined !text-red-400 animate-[bounce_1.5s_infinite]">favorite</span>
                       <span class="material-symbols-outlined !text-blue-400">chat_bubble</span>
                    </div>
                    <span class="material-symbols-outlined !text-[#66bb6a] animate-[pulse_2s_infinite]">bookmark</span>
                 </div>

                 <div class="absolute top-1 right-3 !text-2xl animate-bounce">♥️</div>
              </div>

              <div class="flex gap-3">
                 <span v-for="tag in activePage.subFeatures" :key="tag" 
                       class="!px-4 !py-1.5 bg-[#66bb6a] text-white rounded-full text-base font-bold shadow-md transform hover:scale-105 transition-transform cursor-default">
                    #{{ tag }}
                 </span>
              </div>
            </div>

            <div class="text-center">
              <p class="text-2xl text-[#8d6e63] whitespace-pre-line leading-relaxed font-medium">
                {{ activePage.desc }}
              </p>
              <p v-if="activePage.slogan" class="text-xl text-[#5d4037] font-black mt-4 bg-[#fff3e0] inline-block !px-4 !py-2 rounded-lg border-2 border-[#ffe0b2]">
                {{ activePage.slogan }}
              </p>
            </div>

          </div>
        </transition>
      </main>

      <footer class="!pb-8 !pt-4 flex flex-col items-center gap-4 shrink-0">
        <div class="flex gap-2">
          <div v-for="(_, i) in pages" :key="i"
               class="w-3 h-3 rounded-full transition-all duration-300"
               :class="i === currentPage ? 'bg-[#5d4037] scale-125' : 'bg-[#d7ccc8]'">
          </div>
        </div>

        <button v-if="currentPage === pages.length - 1" @click="$emit('close')"
          class="cartoon-btn primary !px-12 !py-3 text-xl shadow-[0_4px_0_#558b2f] animate-bounce-slight">
          START
        </button>
        <button v-else @click="nextPage"
          class="cartoon-btn secondary !px-12 !py-3 text-xl shadow-[0_4px_0_#8d6e63]">
          NEXT
        </button>
      </footer>

    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-jua {
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

/* Transitions */
.slide-next-enter-active, .slide-next-leave-active,
.slide-prev-enter-active, .slide-prev-leave-active {
  transition: all 0.4s cubic-bezier(0.25, 1, 0.5, 1);
}

.slide-next-enter-from { opacity: 0; transform: translateX(60px); }
.slide-next-leave-to { opacity: 0; transform: translateX(-60px); }

.slide-prev-enter-from { opacity: 0; transform: translateX(-60px); }
.slide-prev-leave-to { opacity: 0; transform: translateX(60px); }

.animate-bounce-slight {
  animation: bounceSlight 2s infinite;
}
@keyframes bounceSlight {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
</style>