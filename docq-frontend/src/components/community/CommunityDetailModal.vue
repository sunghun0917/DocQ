<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useUserStore } from '@/stores/userStore';
import { likeBoard, unlikeBoard, storeQuiz, unstoreQuiz, getComments, createComment, deleteComment, type CommentItem } from '@/services/api';
import ConfirmModal from '@/components/common/ConfirmModal.vue';

const props = defineProps<{ quiz: any }>();
const emit = defineEmits(['close', 'update']);
const userStore = useUserStore();

const commentInput = ref('');
// API 명세: likedByMe, likeCount 사용
const isLiked = ref(props.quiz.likedByMe);
const likeCount = ref(props.quiz.likeCount);
// API 명세: storedByMe 사용 (명세에 없으면 false)
const isSaved = ref(props.quiz.storedByMe || false);

// 퀴즈 미리보기용 데이터 (GET /boards/{boardId}/quiz-preview 결과)
const previewQuestions = ref<any[]>([]);

// 댓글 데이터
const comments = ref<CommentItem[]>([]);
const commentPage = ref(1);
const commentTotal = ref(0);
const isLoadingComments = ref(false);

// 날짜 포맷팅 헬퍼
const formatDate = (dateString: string) => {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return '방금 전';
  if (diffMins < 60) return `${diffMins}분 전`;
  if (diffHours < 24) return `${diffHours}시간 전`;
  if (diffDays < 7) return `${diffDays}일 전`;
  return date.toLocaleDateString('ko-KR');
};

// 댓글 목록 가져오기
const fetchComments = async () => {
  if (!props.quiz?.boardId) return;
  isLoadingComments.value = true;
  try {
    const res = await getComments(props.quiz.boardId, commentPage.value, 20);
    comments.value = res.items;
    commentTotal.value = res.total;
  } catch (error) {
    console.error('댓글 조회 실패:', error);
  } finally {
    isLoadingComments.value = false;
  }
};

onMounted(() => {
  // 상세 응답에 quiz.quizList(questions)가 포함되어 있으므로 여기서 바로 사용
  if (!userStore.isGuest && props.quiz?.quiz?.quizList) {
    previewQuestions.value = props.quiz.quiz.quizList.map((q: string) => ({ question: q }));
  } else {
    previewQuestions.value = [];
  }
  // 댓글 목록 가져오기
  fetchComments();
});

const toggleLike = () => {
  if (userStore.isGuest) return alert("로그인이 필요한 기능입니다.");
  const userId = userStore.userId;
  if (!userId) return;
  const boardId = props.quiz.boardId;
  const action = isLiked.value ? unlikeBoard : likeBoard;
  action(boardId).then((res) => {
    isLiked.value = res.liked;
    likeCount.value = res.likeCount;
    // 부모 컴포넌트에 변경사항 전달 (목록 동기화)
    emit('update', {
      boardId,
      likedByMe: res.liked,
      likeCount: res.likeCount
    });
  }).catch((error) => {
    console.error('좋아요 토글 실패:', error);
    alert('좋아요 처리에 실패했습니다.');
  });
};

const handleSaveClick = () => {
  if (userStore.isGuest) {
    alert("로그인 후 이용해주세요!");
    return;
  }
  const userId = userStore.userId;
  if (!userId) {
    alert("로그인 후 이용해주세요!");
    return;
  }
  if (!props.quiz.quiz?.quizId) {
    alert('저장할 수 없는 게시글입니다.');
    return;
  }

  const quizId = props.quiz.quiz.quizId;
  const wasStored = isSaved.value;
  const action = wasStored ? unstoreQuiz : storeQuiz;

  action(quizId)
    .then((res) => {
      isSaved.value = res.stored;
      // 부모 컴포넌트에 변경사항 전달 (목록 동기화)
      emit('update', {
        boardId: props.quiz.boardId,
        storedByMe: res.stored,
        quizId
      });
    })
    .catch((error) => {
      console.error('퀴즈 저장/언저장 실패:', error);
      alert(wasStored ? '퀴즈 저장 취소에 실패했습니다.' : '퀴즈 저장에 실패했습니다.');
    });
};

watch(
  () => props.quiz,
  (next) => {
    // 모달 재사용 시 상태 갱신
    isLiked.value = next?.likedByMe ?? false;
    likeCount.value = next?.likeCount ?? 0;
    isSaved.value = next?.storedByMe ?? false;
    if (!userStore.isGuest && next?.quiz?.quizList) {
      previewQuestions.value = next.quiz.quizList.map((q: string) => ({ question: q }));
    } else {
      previewQuestions.value = [];
    }
    // 댓글 목록 다시 가져오기
    if (next?.boardId) {
      commentPage.value = 1;
      fetchComments();
    }
  }
);

const handleCommentSubmit = async () => {
  if (userStore.isGuest) return;
  if (!commentInput.value.trim()) return;
  const userId = userStore.userId;
  if (!userId || !props.quiz?.boardId) return;

  const content = commentInput.value.trim();
  commentInput.value = '';

  try {
    await createComment(props.quiz.boardId, { content });
    // 댓글 목록 새로고침
    commentPage.value = 1;
    await fetchComments();
    // 부모 컴포넌트에 댓글 수 업데이트 알림
    emit('update', {
      boardId: props.quiz.boardId,
      commentCount: commentTotal.value
    });
  } catch (error) {
    console.error('댓글 작성 실패:', error);
    alert('댓글 작성에 실패했습니다.');
    commentInput.value = content; // 실패 시 입력값 복원
  }
};

const handleDeleteComment = async (commentId: number) => {
  if (userStore.isGuest) return;
  const userId = userStore.userId;
  if (!userId || !props.quiz?.boardId) return;

  if (!confirm('댓글을 삭제하시겠습니까?')) return;

  try {
    await deleteComment(props.quiz.boardId, commentId);
    // 댓글 목록 새로고침
    await fetchComments();
    // 부모 컴포넌트에 댓글 수 업데이트 알림
    emit('update', {
      boardId: props.quiz.boardId,
      commentCount: commentTotal.value
    });
  } catch (error) {
    console.error('댓글 삭제 실패:', error);
    alert('댓글 삭제에 실패했습니다.');
  }
};

// --- 삭제 로직 ---
const isMyQuiz = computed(() => {
    return userStore.username && props.quiz.author.nickname === userStore.username;
});

const handleDeleteQuiz = () => {
    openConfirm('게시글 삭제', '정말 이 게시글을 삭제하시겠습니까?\n삭제 후에는 복구할 수 없습니다.', '삭제', 'delete_forever', 'danger', () => {
        // TODO: API Call -> DELETE /boards/{boardId}
        alert("게시글이 삭제되었습니다.");
        emit('close');
        // 상위 컴포넌트에서 목록 리로드 필요
    });
};

const close = () => emit('close');

// --- Confirm Modal State ---
const confirmState = ref({
  isOpen: false,
  title: '',
  message: '',
  confirmLabel: '확인',
  icon: 'help',
  variant: 'primary' as 'primary' | 'danger',
  pendingAction: null as (() => void) | null
});

const openConfirm = (title: string, message: string, confirmLabel: string, icon: string, variant: 'primary' | 'danger', action: () => void) => {
  confirmState.value = {
    isOpen: true,
    title,
    message,
    confirmLabel,
    icon,
    variant,
    pendingAction: action
  };
};

const handleConfirmAction = () => {
  if (confirmState.value.pendingAction) {
    confirmState.value.pendingAction();
  }
  confirmState.value.isOpen = false;
};
</script>

<template>
  <div class="fixed inset-0 z-[110] flex items-center justify-center bg-black/50 backdrop-blur-[2px] animate-fade-in" @click.self="close">
    
    <div class="w-[800px] h-[500px] bg-[#fffbf0] border-[4px] !py-4 !px-2 border-[#5d4037] rounded-[20px] shadow-2xl flex overflow-hidden animate-pop-up">
      
      <div class="w-[420px] border-r-2 border-[#d7ccc8] !p-2 flex flex-col bg-[#fffbf0] relative shrink-0">
        
        <div class="flex-1 overflow-y-auto p-4 custom-scrollbar">
           
           <div class="border-b border-[#d7ccc8]/50 pb-2 mb-3 relative">
              <div class="flex justify-between items-start mb-2">
                  <h2 class="text-2xl font-black text-[#3e2723] leading-tight break-all pr-8">
                     {{ quiz.title }}
                  </h2>
                  <button v-if="isMyQuiz" @click="handleDeleteQuiz" class="text-[#bdbdbd] hover:text-[#ef5350] transition-colors p-1 absolute top-0 right-0">
                      <span class="material-symbols-outlined">delete</span>
                  </button>
              </div>

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
               <span class="text-xl font-black">댓글 {{ commentTotal }}</span>
            </div>

            <div v-if="isLoadingComments" class="flex items-center justify-center py-8 text-gray-400">
              <span>댓글을 불러오는 중...</span>
            </div>
            <div v-else-if="comments.length === 0" class="flex items-center justify-center py-8 text-gray-400">
              <span>댓글이 없습니다.</span>
            </div>
            <div v-else v-for="comment in comments" :key="comment.commentId" class="flex gap-2 group">
               <div class="w-6 h-6 rounded-full bg-gray-200 shrink-0 border border-gray-300 flex items-center justify-center text-[9px] font-bold text-gray-500">
                 {{ comment.author.nickname.substring(0,1) }}
               </div>
               
               <div class="flex-1 min-w-0">
                  <div class="flex items-baseline gap-1.5 mb-0.5">
                      <span class="text-md font-black text-[#5d4037] break-all leading-snug">
                          {{ comment.author.nickname }}
                      </span>
                      <span class="text-xs text-gray-400">{{ formatDate(comment.createdAt) }}</span>
                      <button 
                        v-if="!userStore.isGuest && comment.author.userId === Number(userStore.userId)"
                        @click="handleDeleteComment(comment.commentId)"
                        class="ml-auto opacity-0 group-hover:opacity-100 transition-opacity text-xs text-red-500 hover:text-red-700">
                        삭제
                      </button>
                  </div>
                  <p class="text-md text-[#616161] font-medium leading-relaxed bg-white p-1.5 rounded-lg rounded-tl-none border border-gray-100 shadow-sm break-all whitespace-pre-wrap">
                    {{ comment.content }}
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

    <!-- Confirm Modal -->
    <ConfirmModal
      :isOpen="confirmState.isOpen"
      :title="confirmState.title"
      :message="confirmState.message"
      :confirmLabel="confirmState.confirmLabel"
      :icon="confirmState.icon"
      :variant="confirmState.variant"
      @close="confirmState.isOpen = false"
      @confirm="handleConfirmAction"
    />
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