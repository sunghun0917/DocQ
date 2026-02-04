<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted, nextTick } from 'vue';
import { useUserStore } from '@/stores/userStore';
import { getMyQuizzes, type MyQuizItem } from '@/services/api';

const emit = defineEmits(['close', 'submit']);
const userStore = useUserStore();

// 상태 변수
const quizId = ref<number | null>(null);
const title = ref('');
const content = ref('');
const myQuizzes = ref<MyQuizItem[]>([]);
const isLoadingQuizzes = ref(false);

// 드롭다운 UI 상태
const isDropdownOpen = ref(false);
const triggerRef = ref<HTMLElement | null>(null); // 버튼 위치 추적용
const dropdownStyle = ref({}); // 드롭다운 위치 스타일

// 선택된 퀴즈 정보
const selectedQuiz = computed(() => 
  myQuizzes.value.find(q => q.quizId === quizId.value)
);

// [핵심] 드롭다운 위치 계산 (버튼 바로 아래에 붙이기)
const updateDropdownPosition = () => {
  if (triggerRef.value) {
    const rect = triggerRef.value.getBoundingClientRect();
    dropdownStyle.value = {
      position: 'absolute',
      top: `${rect.bottom + window.scrollY + 8}px`, // 버튼 바닥 + 8px 간격
      left: `${rect.left + window.scrollX}px`,
      width: `${rect.width}px`, // 버튼 너비와 동일하게
      zIndex: 9999 // 모달보다 위에 뜨게 설정
    };
  }
};

// 드롭다운 토글
const toggleDropdown = async () => {
  if (isDropdownOpen.value) {
    isDropdownOpen.value = false;
  } else {
    await nextTick(); // DOM 렌더링 대기
    updateDropdownPosition(); // 위치 계산
    isDropdownOpen.value = true;
  }
};

// 스크롤 시 드롭다운 닫기 (위치 어긋남 방지)
const handleScroll = () => {
  if (isDropdownOpen.value) isDropdownOpen.value = false;
};

// 외부 클릭 시 닫기
const handleClickOutside = (event: MouseEvent) => {
  // 버튼 클릭은 toggleDropdown에서 처리하므로 패스
  if (triggerRef.value && triggerRef.value.contains(event.target as Node)) return;
  
  // 드롭다운 리스트 클릭 확인 (Teleport로 딴 데 가있어서 ID로 확인)
  const dropdownEl = document.getElementById('teleported-dropdown');
  if (dropdownEl && dropdownEl.contains(event.target as Node)) return;

  isDropdownOpen.value = false;
};

const fetchMyQuizzes = async () => {
  if (userStore.isGuest || !userStore.userId) return;
  isLoadingQuizzes.value = true;
  try {
    const res = await getMyQuizzes();
    myQuizzes.value = res.quizzes;
  } catch (error) {
    console.error('내 퀴즈 목록 조회 실패:', error);
  } finally {
    isLoadingQuizzes.value = false;
  }
};

const selectQuiz = (id: number) => {
  quizId.value = id;
  isDropdownOpen.value = false;
};

const handleSubmit = () => {
  if (!quizId.value) {
    alert('공유할 퀴즈를 선택해주세요!');
    return;
  }
  if (!title.value.trim() || !content.value.trim()) {
    alert('제목과 내용을 모두 입력해주세요!');
    return;
  }
  emit('submit', { quizId: quizId.value, title: title.value, content: content.value });
};

onMounted(() => {
  fetchMyQuizzes();
  document.addEventListener('click', handleClickOutside);
  window.addEventListener('resize', handleScroll); // 창 크기 조절 시 닫기
  window.addEventListener('scroll', handleScroll, true); // 스크롤 시 닫기
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
  window.removeEventListener('resize', handleScroll);
  window.removeEventListener('scroll', handleScroll, true);
});
</script>

<template>
  <div class="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 backdrop-blur-sm p-4" @click.self="emit('close')">
    <div class="bg-[#fff8e1] border-[6px] border-[#5d4037] rounded-[24px] !p-3 w-full max-w-[680px] h-[450px] shadow-2xl relative animate-pop-in flex flex-col max-h-[90vh]">
      
      <div class="flex justify-between items-center mb-5 shrink-0 px-2 mt-2">
        <h3 class="text-3xl font-black text-[#5d4037] flex items-center gap-3">
          <span class="material-symbols-outlined text-4xl text-[#7cb342]">edit_square</span>
          게시글 작성
        </h3>
      </div>
      
      <div class="h-2 w-full"></div>
      
      <div class="flex-1 overflow-y-auto custom-scrollbar pr-1 flex flex-col gap-3 px-1">
        
        <div>
          <input 
          v-model="title" 
          type="text" 
          class="w-full bg-white border-4 border-[#d7ccc8] rounded-2xl !px-4 py-4 font-bold text-[#5d4037] outline-none focus:border-[#7cb342] placeholder:text-[#d7ccc8] transition-colors"
          placeholder="제목을 입력하세요"
          />
        </div>
        
        <div class="relative z-20">
          <button 
            ref="triggerRef"
            @click.stop="toggleDropdown"
            :disabled="isLoadingQuizzes || myQuizzes.length === 0"
            class="w-full text-left bg-white border-4 rounded-2xl !px-4 py-4 transition-all flex items-center justify-between group relative"
            :class="isDropdownOpen ? 'border-[#7cb342] ring-2 ring-[#7cb342]/20' : 'border-[#d7ccc8] hover:border-[#a1887f]'"
          >
            <span v-if="isLoadingQuizzes" class="text-[#bcaaa4] font-bold animate-pulse">목록 불러오는 중...</span>
            <span v-else-if="myQuizzes.length === 0" class="text-[#bcaaa4] font-bold text-sm">만든 퀴즈가 없습니다 (PDF 업로드 필요)</span>
            
            <div v-else-if="selectedQuiz" class="flex items-center gap-3 !py-2 w-[85%]">
              <div class="w-7 h-7 rounded-lg bg-[#7cb342] flex items-center justify-center text-white shrink-0 shadow-sm">
                <span class="material-symbols-outlined text-[20px]">quiz</span>
              </div>
              <div class="flex flex-col overflow-hidden">
                <span class="font-black text-[#5d4037] truncate leading-tight">{{ selectedQuiz.title }}</span>
                <span class="text-xs font-bold text-[#7cb342]">문제 {{ selectedQuiz.questionCount }}개</span>
              </div>
            </div>
  
            <span v-else class="text-[#bcaaa4] !py-2.5 font-bold">눌러서 퀴즈를 선택하세요</span>
  
            <span class="material-symbols-outlined text-[#8d6e63] transition-transform duration-300 absolute right-5" 
              :class="{ 'rotate-180': isDropdownOpen }">expand_more</span>
          </button>
  
          <Teleport to="body">
            <div 
              id="teleported-dropdown"
              v-if="isDropdownOpen" 
              :style="dropdownStyle"
              class="bg-white border-4 border-[#5d4037] rounded-xl shadow-xl max-h-60 overflow-y-auto custom-scrollbar animate-dropdown"
            >
              <div v-for="quiz in myQuizzes" :key="quiz.quizId" 
                @click="selectQuiz(quiz.quizId)"
                class="!px-4 py-4 border-b-2 border-[#f5f5f5] last:border-0 hover:bg-[#fff8e1] cursor-pointer transition-colors flex items-center gap-3 group"
                :class="{ 'bg-[#f1f8e9]': quizId === quiz.quizId }"
              >
                <span class="material-symbols-outlined text-[#bcaaa4] group-hover:text-[#7cb342] transition-colors" 
                  :class="{ 'text-[#7cb342]': quizId === quiz.quizId }">
                  {{ quizId === quiz.quizId ? 'check_circle' : 'radio_button_unchecked' }}
                </span>
                <div class="flex flex-col overflow-hidden">
                  <span class="font-bold text-[#5d4037] truncate" :class="{ 'text-[#33691e]': quizId === quiz.quizId }">{{ quiz.title }}</span>
                  <span class="text-xs text-[#a1887f]">문제 {{ quiz.questionCount }}개</span>
                </div>
              </div>
            </div>
          </Teleport>
        </div>

        <div>
          <textarea 
            v-model="content" 
            class="w-full h-52 bg-white border-4 border-[#d7ccc8] rounded-2xl !px-4 py-4 font-bold text-[#5d4037] resize-none outline-none focus:border-[#7cb342] placeholder:text-[#d7ccc8] transition-colors custom-scrollbar"
            placeholder="내용을 입력하세요"
          ></textarea>
        </div>
      </div>

      <div class="flex gap-3 mt-4 pt-2 shrink-0 pb-1">
        <button @click="emit('close')" class="flex-1 py-6 rounded-xl font-black text-[#5d4037] bg-[#d7ccc8] hover:bg-[#bcaaa4] transition-colors text-lg">
          취소
        </button>
        <button @click="handleSubmit" 
          class="flex-[2] py-6 rounded-xl font-black text-white shadow-[0_4px_0_#558b2f] transition-all flex items-center justify-center gap-2 text-lg"
          :class="(quizId && title && content) ? 'bg-[#7cb342] hover:bg-[#689f38] active:translate-y-[4px] active:shadow-none cursor-pointer' : 'bg-[#a5d6a7] shadow-none cursor-not-allowed opacity-70'"
        >
          <span class="material-symbols-outlined text-2xl">send</span>
          등록하기
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
@keyframes popIn { from { transform: scale(0.95) translateY(10px); opacity: 0; } to { transform: scale(1) translateY(0); opacity: 1; } }

/* Teleport된 드롭다운 애니메이션 */
.animate-dropdown { animation: slideDown 0.2s ease-out; }
@keyframes slideDown { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }

.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #d7ccc8; border-radius: 10px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #a1887f; }
</style>