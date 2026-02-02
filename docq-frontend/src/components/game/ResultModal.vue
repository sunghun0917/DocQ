<script setup lang="ts">

import { ref, computed, onMounted } from 'vue';

import confetti from 'canvas-confetti';

import AvatarViewer from '@/components/common/AvatarViewer.vue';



interface RankingResult {
  rank: number;
  nickname: string;
  score?: number;
  isMe: boolean;
  avatar?: string | number;
  userId?: string;   // 하위 호환
  playerId?: string; // 게임 참여자 ID (GameView에서 전달)
}

const props = defineProps<{
  isOpen: boolean;
  rankingResults: RankingResult[];
  isHost?: boolean;
}>();



const emit = defineEmits(['close', 'restart']);



const winner = computed(() => {
  if (!props.rankingResults || props.rankingResults.length === 0) return null;
  // GameView에서 이미 순위대로 정렬해서 줍니다. 첫 번째가 1등.
  return props.rankingResults[0];
});

const others = computed(() => {
  if (!props.rankingResults || props.rankingResults.length <= 1) return [];
  // 1등(인덱스 0)을 제외한 나머지 상위 3명 (인덱스 1, 2, 3)
  return props.rankingResults.slice(1, 4);
});



const isNumeric = (val: any): boolean => {

  if (val === null || val === undefined) return false;

  return !isNaN(Number(val));

};



const launchConfetti = () => {

  confetti({

    particleCount: 150,

    spread: 70,

    origin: { y: 0.6 },

    zIndex: 9999,

    colors: ['#7cb342', '#fbc02d', '#ef5350', '#5d4037']

  });

};



onMounted(() => {

  if (props.isOpen) {

    setTimeout(launchConfetti, 400);

  }

});

</script>



<template>

  <!-- Google Font Load -->
  <link href="https://fonts.googleapis.com/css2?family=Jua&display=swap" rel="stylesheet">

  <Teleport to="body">

    <div v-if="isOpen" class="fixed inset-0 z-[3000] flex items-center justify-center px-4 font-display">

     

      <div class="absolute inset-0 bg-black/40 backdrop-blur-sm animate-fade-in" @click="emit('close')"></div>



      <div class="animate-pop-in w-full max-w-[520px] flex justify-center max-h-[90vh]">

       

        <div class="relative bg-[#fff8e1] w-full rounded-[32px] box-border px-6 py-8

                    flex flex-col border-[6px] border-[#8d6e63] shadow-[0_12px_35px_rgba(0,0,0,0.25)] overflow-y-auto custom-scrollbar" style="padding-left: 16px !important; padding-right: 16px !important;">

         

          <div class="absolute top-4 left-4 w-3 h-3 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>

          <div class="absolute top-4 right-4 w-3 h-3 rounded-full bg-[#bcaaa4] border-2 border-[#8d6e63]"></div>



          <h1 class="text-4xl font-display text-[#5d4037] mb-8 mt-2 text-center drop-shadow-sm tracking-tighter">게임 결과</h1>



          <div class="relative flex flex-col items-center justify-end bg-white/60 rounded-3xl p-6 border-4 border-[#fbc02d] mb-4 shadow-sm w-full h-[260px] pb-6 z-20 shrink-0">

           

            <div class="absolute -top-4 -left-3 bg-[#fbc02d] text-white w-12 h-12 flex items-center justify-center rounded-full border-4 border-white font-black text-xl shadow-md z-30">

              #1

            </div>

            <div v-if="winner?.isMe" class="absolute -top-4 -right-3 bg-[#7cb342] text-white text-[24px] px-2 py-1 rounded-full border-4 border-white font-bold shadow-md z-40">
              ME
            </div>

            <div class="absolute top-0 left-1/2 -translate-x-1/2 w-[300px] h-[300px] z-30 pointer-events-none">

              <AvatarViewer

                v-if="winner && isNumeric(winner.avatar)"

                :avatarIndex="Number(winner.avatar)"

                :scale="2.8"

                :autoRotate="false"

                :cameraY="0.8"

                :cameraZ="3.8"

                action="win"

              />

              <div v-else class="w-full h-full flex items-center justify-center text-8xl">{{ winner?.avatar || '🐥' }}</div>

            </div>

            <h2 class="text-3xl font-black text-[#5d4037] truncate max-w-full mb-1 z-10">{{ winner?.nickname }}</h2>


          </div>

          <div class="w-full h-3"></div>



          <div class="grid grid-cols-3 gap-3 w-full mb-8 z-20 shrink-0">

            <div

              v-for="player in others"

              :key="player.rank"

              class="relative flex flex-col items-center justify-end bg-white rounded-2xl border-4 transition-all mt-12 h-40 p-2"

              :class="player.isMe ? 'border-[#7cb342] bg-[#f1f8e9] shadow-lg scale-105 z-20' : 'border-[#d7ccc8] shadow-sm'"

            >

              <div class="absolute -top-[150px] left-1/2 -translate-x-1/2 w-[240px] h-[240px] z-30 pointer-events-none">

                <AvatarViewer

                  v-if="isNumeric(player.avatar)"

                  :avatarIndex="Number(player.avatar)"

                  :scale="1.5"

                  :autoRotate="false"

                  :cameraY="0.4"

                  :cameraZ="5.5"

                  :modelX="0"

                  action="lose"

                />

                <div v-else class="w-full h-full flex items-center justify-center text-6xl">{{ player.avatar || '👤' }}</div>

              </div>



              <div class="absolute -top-3 -left-2 bg-[#8d6e63] text-white w-8 h-8 flex items-center justify-center rounded-full border-4 border-white font-black text-sm shadow-sm z-30">

                #{{ player.rank }}

              </div>



              <div v-if="player.isMe" class="absolute -top-3 -right-2 bg-[#7cb342] text-white text-[20px] px-2 py-1 rounded-full border-2 border-white font-bold shadow-sm z-30">

                ME

              </div>



              <div class="font-bold text-[#5d4037] text-base truncate w-full text-center mb-1 z-10">

                {{ player.nickname }}

              </div>


            </div>

          </div>

          <div class="w-full h-3"></div>



          <div class="flex gap-3 w-full mt-auto z-10 shrink-0 pointer-events-auto">

            <button @click="emit('close')" class="cartoon-btn danger whitespace-nowrap pointer-events-auto">나가기</button>

            <button v-if="isHost" @click="emit('restart')" class="cartoon-btn primary flex-[1.5] whitespace-nowrap pointer-events-auto">

               한 판 더!

            </button>

          </div>

          <div class="w-full h-5"></div>

        </div>

      </div>

    </div>

  </Teleport>

</template>



<style scoped>

@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-display { font-family: 'Jua', sans-serif; }



.cartoon-btn {

  flex: 1; height: 60px; border-radius: 18px; font-size: 1.3rem; color: white;

  font-family: 'Jua', sans-serif; transition: all 0.1s ease;

  display: flex; align-items: center; justify-content: center;

  position: relative; top: 0; border: none; cursor: pointer;

  box-shadow: 0 4px 0 rgba(0,0,0,0.2);

}

.cartoon-btn.secondary { background-color: #9ca3af; box-shadow: 0 4px 0 #6b7280; }

.cartoon-btn.secondary:active { top: 4px; box-shadow: 0 0 0 #6b7280; }

.cartoon-btn.danger { background-color: #ef5350; box-shadow: 0 4px 0 #b71c1c; }

.cartoon-btn.danger:active { top: 4px; box-shadow: 0 0 0 #b71c1c; }



.cartoon-btn.primary { background-color: #7cb342; box-shadow: 0 4px 0 #558b2f; }

.cartoon-btn.primary:active { top: 4px; box-shadow: 0 0 0 #558b2f; }



.animate-fade-in { animation: fadeIn 0.2s ease-out forwards; }

.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards; }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

@keyframes popIn {

  from { opacity: 0; transform: scale(0.8) translateY(20px) rotate(-2deg); }

  to { opacity: 1; transform: scale(1) translateY(0) rotate(0deg); }

}

</style>