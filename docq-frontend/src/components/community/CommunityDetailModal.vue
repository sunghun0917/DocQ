<script setup lang="ts">
import { defineProps, defineEmits, ref, onMounted } from 'vue';
import { useUserStore } from '@/stores/userStore';

const props = defineProps<{ quiz: any }>();
const emit = defineEmits(['close']);
const userStore = useUserStore();

const commentInput = ref('');
// API 명세: likedByMe, likeCount 사용
const isLiked = ref(props.quiz.likedByMe);
const likeCount = ref(props.quiz.likeCount);
// API 명세: storedByMe 사용 (명세에 없으면 false)
const isSaved = ref(props.quiz.storedByMe || false);

// 퀴즈 미리보기용 데이터 (GET /boards/{boardId}/quiz-preview 결과)
const previewQuestions = ref<any[]>([]);

// 댓글 더미 데이터 (구조 맞춤)
const comments = ref([
  { id: 1, user: '퀴즈왕', text: '3번 문제 헷갈리네요 ㅋㅋ', time: '10m' },
  { id: 2, user: '개발자', text: '담아갈게요.', time: '1h' },
]);

onMounted(() => {
  // [중요] 퀴즈 미리보기는 회원만 가능 (API 명세 권한)
  if (!userStore.isGuest) {
    // 실제론 axios.get(`/boards/${props.quiz.boardId}/quiz-preview`) 호출
    previewQuestions.value = [
      { question: "API 명세서 미리보기 질문 1" },
      { question: "API 명세서 미리보기 질문 2" },
      { question: "API 명세서 미리보기 질문 3" }
    ];
  }
});

const toggleLike = () => {
  if (userStore.isGuest) return alert("로그인이 필요한 기능입니다.");
  isLiked.value = !isLiked.value;
  likeCount.value += isLiked.value ? 1 : -1;
};

const handleSaveClick = () => {
  if (userStore.isGuest) {
    alert("로그인 후 이용해주세요!");
    return;
  }
  isSaved.value = !isSaved.value;
};

const handleCommentSubmit = () => {
  if (userStore.isGuest) return;
  if (!commentInput.value.trim()) return;
  
  comments.value.unshift({
    id: Date.now(),
    user: userStore.username || 'Me',
    text: commentInput.value,
    time: '방금 전'
  });
  commentInput.value = '';
};

const close = () => emit('close');
</script>

<template>
  <div class="fixed inset-0 z-[110] flex items-center justify-center bg-black/50 backdrop-blur-[2px] animate-fade-in" @click.self="close">
    
    <div class="w-[800px] h-[500px] bg-[#fffbf0] border-[4px] !py-4 !px-2 border-[#5d4037] rounded-[20px] shadow-2xl flex overflow-hidden animate-pop-up">
      
      <div class="w-[420px] border-r-2 border-[#d7ccc8] !p-2 flex flex-col bg-[#fffbf0] relative shrink-0">
        
        <div class="flex-1 overflow-y-auto p-4 custom-scrollbar">
           
           <div class="border-b border-[#d7ccc8]/50 pb-2 mb-3">
              <h2 class="text-2xl font-black text-[#3e2723] leading-tight mb-2 break-all">
                 {{ quiz.title }}
              </h2>
              <div class="flex justify-between items-center">
                 <div class="flex items-center gap-1.5">
                    <div class="w-7 h-7 rounded-full bg-[#7cb342] border border-[#558b2f] flex items-center justify-center text-white font-black text-lg shadow-sm">
                       {{ quiz.author.nickname.substring(0,1) }}
                    </div>
                    <span class="font-bold text-[#5d4037] text-md">{{ quiz.author.nickname }}</span>
                 </div>
                 <span class="text-md font-bold text-[#bcaaa4]">{{ quiz.createdAt.split('T')[0] }}</span>
              </div>
           </div>

           <div class="mb-4">
              <p class="text-[#616161] font-medium leading-relaxed text-md whitespace-pre-wrap break-all">
                 {{ quiz.content }}
              </p>
           </div>
        </div>

        <div class="p-4 border-t border-[#d7ccc8] bg-white shrink-0 z-10 shadow-[0_-5px_10px_rgba(0,0,0,0.02)]">
           <div class="flex flex-col gap-2.5 mb-3">
              <h4 class="text-md font-black text-[#8d6e63] flex items-center gap-1.5">
                 <span class="material-symbols-outlined text-base">quiz</span>
                 퀴즈 미리보기 (회원전용)
              </h4>
              
              <div class="flex flex-col gap-2 h-[100px] pr-1 overflow-y-auto custom-scrollbar">
                 <div v-if="userStore.isGuest" class="h-full flex items-center justify-center text-gray-400 text-xs">
                    로그인 후 미리보기를 확인하세요.
                 </div>
                 <div v-else v-for="(q, idx) in previewQuestions" :key="idx" 
                      class="bg-[#f9f9f9] p-2 rounded-lg border border-[#eeeeee] flex gap-2.5 items-center">
                    <span class="w-5 h-5 rounded-md bg-[#f1f8e9] text-[#558b2f] flex items-center justify-center font-black shrink-0 text-xs">
                      {{ idx + 1 }}
                    </span>
                    <p class="flex-1 min-w-0 text-[#5d4037] font-bold text-md leading-snug break-all">
                        {{ q.question }}
                    </p>
                 </div>
              </div>
           </div>

           <button @click="handleSaveClick" 
                   class="w-[400px] h-10 rounded-xl border-b-4 font-black text-base flex items-center justify-center gap-1.5 transition-all active:border-b-0 active:translate-y-1"
                   :class="[
                     userStore.isGuest 
                       ? 'bg-[#d7ccc8] border-[#a1887f] text-white cursor-not-allowed' /* 게스트: 회색 */
                       : (isSaved ? 'bg-[#7cb342] border-[#558b2f] text-white' : 'bg-[#fb8c00] border-[#ef6c00] text-white hover:brightness-110') /* 유저: 초록/주황 */
                   ]">
              <span class="material-symbols-outlined text-lg">
                {{ userStore.isGuest ? 'lock' : (isSaved ? 'check_circle' : 'bookmark_add') }}
              </span>
              {{ userStore.isGuest ? '로그인하고 퀴즈를 저장하세요' : (isSaved ? '저장 완료!' : '이 퀴즈 저장하기') }}
           </button>
        </div>
      </div>

      <div class="flex-1 flex flex-col !p-2">
         
         <div class="flex-1 overflow-y-auto p-3 custom-scrollbar flex flex-col gap-3 ">
            <div class="flex items-center gap-1 text-[#8d6e63] bg-[#fffbf0] border-b border-gray-200 pb-2 mb-0 sticky top-0 z-10">
               <span class="material-symbols-outlined text-sm ">chat</span>
               <span class="text-xl font-black">댓글 {{ props.quiz.commentCount }}</span>
            </div>

            <div v-for="comment in comments" :key="comment.id" class="flex gap-2">
               <div class="w-6 h-6 rounded-full bg-gray-200 shrink-0 border border-gray-300 flex items-center justify-center text-[9px] font-bold text-gray-500">
                 {{ comment.user.substring(0,1) }}
               </div>
               
               <div class="flex-1 min-w-0">
                  <div class="flex items-baseline gap-1.5 mb-0.5">
                      <span class="text-md font-black text-[#5d4037] break-all leading-snug">
                          {{ comment.user }}
                      </span>
                  </div>
                  <p class="text-md text-[#616161] font-medium leading-relaxed bg-white p-1.5 rounded-lg rounded-tl-none border border-gray-100 shadow-sm break-all whitespace-pre-wrap">
                    {{ comment.text }}
                  </p>
               </div>
            </div>
         </div>

         <div class="p-2 border-t border-[#f5f5f5] flex flex-col gap-2 shrink-0">
            <div class="flex items-center gap-1">
               <button @click="toggleLike" class="group flex items-center gap-1">
                  <span class="material-symbols-outlined text-lg transition-transform group-active:scale-125" 
                        :class="isLiked ? 'text-red-500 fill-current' : 'text-[#bdbdbd] hover:text-red-300'">
                     favorite
                  </span>
               </button>
               <span class="text-lg font-black text-[#5d4037]">좋아요 {{ likeCount }}개</span>
            </div>

            <div class="flex gap-1.5 items-center">
               <input v-model="commentInput" 
                      type="text" 
                      :disabled="userStore.isGuest"
                      :placeholder="userStore.isGuest ? '로그인하고 댓글을 달아보세요' : '댓글을 남겨주세요...'" 
                      class="flex-1 h-10 bg-[#f5f5f5] rounded-lg px-3 text-[15px] font-bold text-[#5d4037] outline-none border border-transparent transition-colors placeholder:text-gray-400" 
                      :class="userStore.isGuest ? 'cursor-not-allowed opacity-70' : 'focus:border-[#7cb342]'"
               />
               
               <button @click="handleCommentSubmit" 
                       :disabled="userStore.isGuest"
                       class="h-10 px-3 !p-2 rounded-lg font-black text-[12px] transition-all shrink-0"
                       :class="userStore.isGuest 
                         ? 'bg-[#d7ccc8] text-white cursor-not-allowed' 
                         : 'bg-[#5d4037] text-white hover:bg-[#3e2723] active:scale-95'">
                  확인
               </button>
            </div>
         </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
.animate-pop-up { animation: popUp 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes popUp { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }

/* 스크롤바 커스텀 */
.custom-scrollbar::-webkit-scrollbar { width: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #d7ccc8; border-radius: 2px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }

.material-symbols-outlined { font-variation-settings: 'FILL' 1, 'wght' 700, 'GRAD' 0, 'opsz' 20; }
</style>