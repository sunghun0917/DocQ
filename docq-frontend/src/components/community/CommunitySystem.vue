<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useUserStore } from '@/stores/userStore';
import CommunityWriteModal from './CommunityWriteModal.vue';
import CommunityDetailModal from './CommunityDetailModal.vue';
import { createBoard, getBoardDetail, getBoards, likeBoard, unlikeBoard, storeQuiz, unstoreQuiz, getErrorCode, getErrorMessage, type BoardDetailResponse, type BoardListItem } from '@/services/api';

const emit = defineEmits(['close']);
const userStore = useUserStore();

// --- 상태 ---
const searchQuery = ref('');
const isWriteModalOpen = ref(false);
const isDetailModalOpen = ref(false);
const selectedQuiz = ref<any>(null);
const currentSort = ref('newest');
const currentPage = ref(1);
const itemsPerPage = 8; // 서버 size와 맞춤
const isThrottled = ref(false);
const slideDirection = ref('next');

// --- 서버에서 가져오는 게시글 목록 ---
const boardItems = ref<(BoardListItem & { saves?: number })[]>([]);
const total = ref(0);
const isLoading = ref(false);

const fetchBoards = async () => {
  isLoading.value = true;
  try {
    const res = await getBoards(currentPage.value, itemsPerPage);
    total.value = res.total;
    // 서버에서 받은 storedCount를 saves로 매핑, questionCount는 서버 응답 그대로 사용
    boardItems.value = res.items.map((it) => ({
      ...it,
      saves: it.storedCount ?? 0, // 서버의 storedCount를 saves로 사용
      questionCount: it.questionCount ?? 0, // 서버의 questionCount 사용 (없으면 0)
    }));
  } catch (error) {
    console.error('게시판 목록 조회 실패:', error);
  } finally {
    isLoading.value = false;
  }
};

// --- 정렬 옵션 (게스트 분기 처리) ---
const sortOptions = computed(() => {
  const options = [
    { id: 'newest', label: '최신순', icon: 'schedule' },
    { id: 'most_liked', label: '인기순', icon: 'favorite' },
    { id: 'most_commented', label: '댓글순', icon: 'forum' },
  ];
  if (!userStore.isGuest) {
    options.push(
      { id: 'my_posts', label: '내가 쓴 글', icon: 'edit_note' },
      { id: 'my_saves', label: '내가 저장한 글', icon: 'bookmark' }
    );
  }
  return options;
});

// 게스트 전환 시 탭 초기화
watch(() => userStore.isGuest, (newVal) => {
  if (newVal && (currentSort.value === 'my_posts' || currentSort.value === 'my_saves')) {
    currentSort.value = 'newest';
  }
});

// --- 필터링 로직 (API 필드명 적용) ---
const filteredQuizzes = computed(() => {
  let result = boardItems.value.filter(q =>
    q.title.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    q.author.nickname.toLowerCase().includes(searchQuery.value.toLowerCase()) // author.nickname 접근
  );

  if (currentSort.value === 'newest') {
    result.sort((a, b) => b.boardId - a.boardId);
  } else if (currentSort.value === 'most_liked') {
    result.sort((a, b) => b.likeCount - a.likeCount);
  } else if (currentSort.value === 'most_commented') {
    result.sort((a, b) => b.commentCount - a.commentCount);
  } else if (currentSort.value === 'my_posts') {
    result = result.filter(q => q.author.nickname === userStore.username);
  } else if (currentSort.value === 'my_saves') {
    result = result.filter(q => q.storedByMe === true);
  }
  return result;
});

// 서버 페이지네이션: total/size로 페이지 수 계산
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / itemsPerPage)));
// 이미 한 페이지 단위로 받아오므로 그대로 표시 (검색/정렬은 현재 페이지 내에서만 적용)
const paginatedQuizzes = computed(() => filteredQuizzes.value);

const visiblePages = computed(() => {
  const total = totalPages.value;
  const current = currentPage.value;
  if (total <= 5) return Array.from({ length: total }, (_, i) => i + 1);
  let start = current - 2, end = current + 2;
  if (start < 1) { start = 1; end = 5; }
  if (end > total) { end = total; start = total - 4; }
  return Array.from({ length: end - start + 1 }, (_, i) => start + i);
});

watch([searchQuery, currentSort], () => { currentPage.value = 1; });
watch(currentPage, () => { fetchBoards(); });

// --- 핸들러 ---
const toggleLike = (quiz: any) => {
  if (userStore.isGuest) return alert("로그인 후 이용 가능합니다.");
  const userId = userStore.userId;
  if (!userId) return alert("로그인 후 이용 가능합니다.");
  const boardId = quiz.boardId;
  const action = quiz.likedByMe ? unlikeBoard : likeBoard;
  action(boardId).then((res) => {
    quiz.likedByMe = res.liked;
    quiz.likeCount = res.likeCount;
  });
};

const toggleSave = (quiz: any) => {
  if (userStore.isGuest) {
    alert('로그인 후 이용 가능합니다.');
    return;
  }
  const userId = userStore.userId;
  if (!userId) {
    alert('로그인 후 이용 가능합니다.');
    return;
  }
  if (!quiz.quizId) {
    alert('저장할 수 없는 게시글입니다.');
    return;
  }

  // 토글: 저장되어 있으면 언저장, 없으면 저장
  const wasStored = !!quiz.storedByMe;
  const action = wasStored ? unstoreQuiz : storeQuiz;
  
  action(quiz.quizId)
    .then((res) => {
      // 서버 기준으로 내가 저장한 상태 반영
      quiz.storedByMe = res.stored;

      // 저장 개수 업데이트
      if (res.stored && !wasStored) {
        // 저장: +1
        quiz.saves = (quiz.saves ?? 0) + 1;
      } else if (!res.stored && wasStored) {
        // 언저장: -1 (최소 0)
        quiz.saves = Math.max(0, (quiz.saves ?? 0) - 1);
      }
    })
    .catch((error) => {
      console.error('퀴즈 저장/언저장 실패:', error);
      alert(wasStored ? '퀴즈 저장 취소에 실패했습니다.' : '퀴즈 저장에 실패했습니다.');
    });
};

const filterByAuthor = (authorName: string) => {
  searchQuery.value = authorName;
  currentPage.value = 1;
};

const openDetail = (quiz: any) => {
  getBoardDetail(quiz.boardId).then((detail: BoardDetailResponse) => {
    selectedQuiz.value = detail;
    isDetailModalOpen.value = true;
  });
};

// 상세 모달에서 좋아요/저장/댓글 변경 시 목록 동기화
const handleDetailUpdate = (updateData: { boardId: number; likedByMe?: boolean; likeCount?: number; storedByMe?: boolean; quizId?: number; commentCount?: number }) => {
  // 목록에서 해당 게시물 찾아서 업데이트
  const index = boardItems.value.findIndex(item => item.boardId === updateData.boardId);
  const item = index !== -1 ? boardItems.value[index] : undefined;
  if (item !== undefined) {
    const wasStored = item.storedByMe;
    
    if (updateData.likedByMe !== undefined) {
      item.likedByMe = updateData.likedByMe;
    }
    if (updateData.likeCount !== undefined) {
      item.likeCount = updateData.likeCount;
    }
    if (updateData.commentCount !== undefined) {
      item.commentCount = updateData.commentCount;
    }
    if (updateData.storedByMe !== undefined) {
      item.storedByMe = updateData.storedByMe;
      // 저장 개수 업데이트: 이전 상태와 비교하여 변경
      if (updateData.storedByMe && !wasStored) {
        // 저장: +1
        item.saves = (item.saves ?? 0) + 1;
      } else if (!updateData.storedByMe && wasStored) {
        // 언저장: -1 (최소 0)
        item.saves = Math.max(0, (item.saves ?? 0) - 1);
      }
    }
  }
  
  // 상세 모달의 데이터도 업데이트
  if (selectedQuiz.value && selectedQuiz.value.boardId === updateData.boardId) {
    if (updateData.likedByMe !== undefined) {
      selectedQuiz.value.likedByMe = updateData.likedByMe;
    }
    if (updateData.likeCount !== undefined) {
      selectedQuiz.value.likeCount = updateData.likeCount;
    }
    if (updateData.commentCount !== undefined) {
      selectedQuiz.value.commentCount = updateData.commentCount;
    }
    if (updateData.storedByMe !== undefined) {
      selectedQuiz.value.storedByMe = updateData.storedByMe;
    }
  }
};

// 휠/페이지네이션 등 기존 로직 유지
const handleGlobalWheel = (e: WheelEvent) => {
  if (isWriteModalOpen.value || isDetailModalOpen.value || isThrottled.value) return;
  if (e.deltaY > 0) { if (currentPage.value < totalPages.value) { slideDirection.value = 'next'; changePage(currentPage.value + 1); } }
  else { if (currentPage.value > 1) { slideDirection.value = 'prev'; changePage(currentPage.value - 1); } }
};

const changePage = (page: number) => {
  if (page > currentPage.value) slideDirection.value = 'next';
  else if (page < currentPage.value) slideDirection.value = 'prev';
  currentPage.value = page;
  isThrottled.value = true;
  setTimeout(() => isThrottled.value = false, 600);
};

onMounted(() => {
  fetchBoards();
  window.addEventListener('wheel', handleGlobalWheel);
});
onUnmounted(() => { window.removeEventListener('wheel', handleGlobalWheel); });

const handleCreateClick = () => {
  if (userStore.isGuest) return; 
  isWriteModalOpen.value = true;
};

const handlePostSubmit = (data: any) => {
  if (userStore.isGuest) return;
  const userId = userStore.userId;
  if (!userId) return;
  createBoard({ quizId: data.quizId, title: data.title, content: data.content }).then(() => {
    isWriteModalOpen.value = false;
    currentPage.value = 1;
    currentSort.value = 'newest';
    fetchBoards();
  }).catch((error) => {
    const code = getErrorCode(error);
    const message = getErrorMessage(error);
    if (code === 'B004' || message.includes('중복된 퀴즈')) {
      alert('중복된 퀴즈입니다.');
    } else {
      alert(message);
    }
  });
};
</script>

<template>
  <div class="community-layout pointer-events-auto">
    <header class="header-area">
      <div class="left-controls animate-fade-in-left">
        <div class="search-box">
          <input v-model="searchQuery" type="text" placeholder="제목이나 작성자로 검색..." />
          <span class="material-symbols-outlined search-icon">search</span>
        </div>
        <div class="sort-buttons">
          <button v-for="opt in sortOptions" :key="opt.id" @click="currentSort = opt.id" class="sort-btn font-noto"
            :class="{ active: currentSort === opt.id }">
            <span class="material-symbols-outlined icon">{{ opt.icon }}</span>
            {{ opt.label }}
          </button>
        </div>
      </div>

      <div class="right-controls animate-fade-in-right">
        <button v-if="!userStore.isGuest" @click="handleCreateClick" class="btn-create">
          <span class="material-symbols-outlined">add</span> Create
        </button>
        <button @click="emit('close')" class="btn-close">
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>
    </header>

    <main class="grid-area">
      <transition :name="slideDirection === 'next' ? 'slide-left' : 'slide-right'" mode="out-in">
        <div v-if="paginatedQuizzes.length > 0" :key="currentPage" class="quiz-grid">
          
          <div v-for="quiz in paginatedQuizzes" :key="quiz.boardId" @click="openDetail(quiz)" class="quiz-card group">
            
            <div class="card-header">
              <div class="author-info" @click.stop="filterByAuthor(quiz.author.nickname)">
                <div class="avatar">{{ quiz.author.nickname.substring(0, 1) }}</div>
                <span class="name">{{ quiz.author.nickname }}</span>
              </div>
              <span class="date">{{ quiz.createdAt.split('T')[0] }}</span>
            </div>

            <div class="card-body">
              <h3 class="title group-hover:text-[#7cb342]">{{ quiz.title }}</h3>
              <p class="desc">{{ quiz.content }}</p>
            </div>

            <div class="preview-box">
              <ul>
                <li>
                  <span class="q-num">Preview</span>
                  <span class="q-text">클릭하여 퀴즈 상세 내용을 확인하세요.</span>
                </li>
              </ul>
            </div>

            <div class="card-footer">
              <div class="stats">
                <button @click.stop="toggleLike(quiz)" class="stat-item like-btn"
                  :class="{ 'text-red-500': quiz.likedByMe }">
                  <span class="material-symbols-outlined icon">{{ quiz.likedByMe ? 'favorite' : 'favorite' }}</span>
                  <span>{{ quiz.likeCount }}</span>
                </button>
                <div class="stat-item hover:text-blue-400">
                  <span class="material-symbols-outlined icon">chat_bubble</span>
                  <span>{{ quiz.commentCount }}</span>
                </div>
                
                <button @click.stop="toggleSave(quiz)" class="stat-item hover:text-green-600 transition-colors"
                  :class="{ 'text-[#7cb342]': quiz.storedByMe }">
                  <span class="material-symbols-outlined icon">
                    {{ quiz.storedByMe ? 'bookmark' : 'bookmark_border' }}
                  </span>
                  <span>{{ quiz.saves }}</span>
                </button>
              </div>

              <div class="quiz-count">
                <span class="material-symbols-outlined icon">quiz</span>
                <span>{{ quiz.questionCount }}</span>
              </div>
            </div>

          </div>

          <div v-for="n in (itemsPerPage - paginatedQuizzes.length)" :key="'empty-' + n"></div>
        </div>

        <div v-else class="empty-state">
          <span class="material-symbols-outlined">search_off</span>
          <p>검색 결과가 없어요!</p>
        </div>
      </transition>
    </main>

    <footer class="pagination-area animate-fade-in-up" v-if="totalPages > 0">
      <button @click="changePage(currentPage - 1)" :disabled="currentPage === 1" class="page-arrow">
        <span class="material-symbols-outlined">chevron_left</span>
      </button>
      <div class="page-numbers">
        <button v-for="page in visiblePages" :key="page" @click="changePage(page)" class="page-num"
          :class="{ active: currentPage === page }">{{ page }}</button>
      </div>
      <button @click="changePage(currentPage + 1)" :disabled="currentPage === totalPages" class="page-arrow">
        <span class="material-symbols-outlined">chevron_right</span>
      </button>
    </footer>

    <CommunityWriteModal v-if="isWriteModalOpen" @close="isWriteModalOpen = false" @submit="handlePostSubmit" />
    <CommunityDetailModal v-if="isDetailModalOpen" :quiz="selectedQuiz" @close="isDetailModalOpen = false" @update="handleDetailUpdate" />

  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@500;700;900&display=swap');

/* --- [1] 레이아웃 (고정 해상도) --- */
.community-layout {
  width: 1180px;
  height: 650px;
  max-height: 650px;
  margin: 100px auto 0;  
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  z-index: 50;
  
  /* ▼ 기존 padding: 20px 10px; 에서 상하 패딩을 조금 줄여서 균형 맞춤 (선택사항) */
  padding: 10px;
}

.font-noto { font-family: 'Noto Sans KR', sans-serif; }

/* --- [2] 헤더 영역 --- */
.header-area { display: flex; justify-content: space-between; align-items: flex-end; height: 60px; }
.left-controls { display: flex; flex-direction: column; gap: 12px; }

.search-box { position: relative; width: 400px; }
.search-box input {
  width: 100%; height: 48px; padding: 0 48px; border-radius: 99px;
  background: rgba(255, 255, 255, 0.4); border: 2px solid rgba(255, 255, 255, 0.6);
  color: white; font-weight: bold; outline: none; backdrop-filter: blur(4px);
  transition: 0.2s;
}
.search-box input:focus { background: rgba(255, 255, 255, 0.6); border-color: white; }
.search-box input::placeholder { color: rgba(255, 255, 255, 0.8); }
.search-icon { position: absolute; left: 16px; top: 50%; transform: translateY(-50%); color: white; }

.sort-buttons { display: flex; gap: 8px; }
.sort-btn {
  padding: 6px 16px; border-radius: 99px; font-size: 14px; font-weight: bold;
  border: 2px solid rgba(255, 255, 255, 0.4); background: rgba(255, 255, 255, 0.3);
  color: white; display: flex; align-items: center; gap: 6px; cursor: pointer;
  transition: 0.2s; backdrop-filter: blur(4px);
}
.sort-btn:hover { background: rgba(255, 255, 255, 0.5); }
.sort-btn.active { background: #7cb342; border-color: #558b2f; transform: scale(1.05); }
.sort-btn .icon { font-size: 18px; }

.right-controls { display: flex; gap: 12px; }
.btn-create {
  height: 48px; padding: 0 24px; border-radius: 99px; background: #7cb342;
  border-bottom: 4px solid #558b2f; color: white; font-weight: 900; font-size: 18px;
  display: flex; align-items: center; gap: 8px; cursor: pointer; transition: 0.1s;
}
.btn-create:active { border-bottom: 0; transform: translateY(4px); }
.btn-close {
  height: 48px; width: 48px; border-radius: 50%; background: #ef5350;
  border-bottom: 4px solid #c62828; color: white; display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: 0.1s;
}
.btn-close:active { border-bottom: 0; transform: translateY(4px); }
/* [수정 2] 그리드 영역: minmax를 써서 넘침 방지 */
.grid-area { flex: 1; overflow: hidden; position: relative; }
/* [수정 1] 그리드 컬럼 강제 고정 */
.quiz-grid {
  display: grid;
  /* repeat(4, 1fr)  --->  repeat(4, minmax(0, 1fr)) 로 변경 */
  grid-template-columns: repeat(4, minmax(0, 1fr)); 
  grid-template-rows: repeat(2, minmax(0, 1fr)); 
  gap: 20px; 
  height: 100%;
  padding: 10px; 
}

/* [수정 2] 카드 자체도 넘침 방지 */
.quiz-card {
  background: white;
  border-radius: 24px;
  padding: 10px; 
  box-shadow: 0 5px 10px -3px rgba(0, 0, 0, 0.1);
  border-top: 6px solid #7cb342;
  display: flex; 
  flex-direction: column;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  
  /* ▼ [핵심] 높이/너비 모두 내용물보다 작아질 수 있게 허용 */
  min-height: 0; 
  min-width: 0;  
}
.quiz-card:hover { transform: translateY(-6px); box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); }
/* 카드 내부 요소 */
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.author-info { display: flex; align-items: center; gap: 8px; }
.author-info .avatar {
  width: 32px; height: 32px; border-radius: 50%; background: #f1f8e9;
  border: 2px solid #aed581; color: #558b2f; display: flex; align-items: center; justify-content: center;
  font-weight: bold; font-size: 13px;
}
.author-info .name { font-size: 14px; font-weight: bold; color: #5d4037; }
.author-info:hover .name { color: #7cb342; text-decoration: underline; }
.card-header .date { font-size: 11px; font-weight: bold; color: #bdbdbd; }

.card-body { margin-bottom: 12px; }
.card-body .title { font-size: 18px; font-weight: 900; color: #3e2723; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; transition: color 0.2s; }
.card-body .desc { font-size: 12px; color: #757575; font-weight: 500; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; height: 34px;}

.preview-box {
  background: #f5f5f5; 
  border-radius: 12px; 
  padding: 10px; 
  border: 1px solid #eeeeee;
  flex: 1; 
  margin-bottom: 10px; 
  overflow: hidden; /* 박스 자체도 넘침 방지 */
}

.preview-box ul {
  display: flex;
  flex-direction: column;
  gap: 4px; /* 간격 미세 조정 */
}

.preview-box li { 
  display: flex; 
  gap: 6px; 
  font-size: 11px; 
  color: #616161; 
  margin-bottom: 4px; 
  align-items: center; /* 아이콘과 텍스트 수직 중앙 정렬 */
}

.preview-box .q-num { 
  font-weight: bold; 
  color: #bdbdbd; 
  flex-shrink: 0; /* 번호는 줄어들지 않게 고정 */
}

.preview-box .q-text { 
  flex: 1; 
  min-width: 0; /* 공간 없으면 줄어들기 */
  white-space: nowrap; 
  overflow: hidden; 
  text-overflow: ellipsis; 
}

.preview-box .more { 
  color: #bdbdbd; 
  padding-left: 20px; 
}
.card-footer { display: flex; justify-content: space-between; align-items: center; border-top: 2px solid #f5f5f5; padding-top: 10px; margin-top: auto; }
.stats { display: flex; gap: 12px; font-size: 12px; font-weight: bold; color: #9e9e9e; }
.stat-item { display: flex; align-items: center; gap: 4px; transition: color 0.2s; }
.stat-item .icon { font-size: 18px; }
.like-btn:hover { color: #ef5350; }
.like-btn:active .icon { transform: scale(1.2); }

.quiz-count { 
  display: flex; align-items: center; gap: 4px; background: #f1f8e9; color: #7cb342;
  font-size: 12px; font-weight: 900; padding: 4px 8px; border-radius: 8px;
}
.quiz-count .icon { font-size: 14px; }

.empty-state { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: rgba(255,255,255,0.8); }
.empty-state span { font-size: 80px; opacity: 0.5; margin-bottom: 10px; }
.empty-state p { font-size: 24px; font-weight: bold; }

/* --- [4] 페이지네이션 --- */
.pagination-area { display: flex; justify-content: center; gap: 16px; height: 40px; margin-top: 10px; }
.page-arrow {
  width: 40px; height: 40px; border-radius: 50%; background: rgba(255, 255, 255, 0.3);
  color: white; display: flex; align-items: center; justify-content: center; cursor: pointer;
  transition: 0.2s; backdrop-filter: blur(4px);
}
.page-arrow:hover:not(:disabled) { background: rgba(255, 255, 255, 0.5); }
.page-arrow:disabled { opacity: 0.3; cursor: not-allowed; }

.page-numbers { display: flex; gap: 8px; }
.page-num {
  width: 40px; height: 40px; border-radius: 50%; background: rgba(255, 255, 255, 0.3);
  color: white; font-weight: bold; font-size: 16px; cursor: pointer; transition: 0.2s;
}
.page-num:hover { background: rgba(255, 255, 255, 0.5); }
.page-num.active { background: #7cb342; border-bottom: 4px solid #558b2f; transform: translateY(-4px); }

/* --- [5] 애니메이션 --- */
.animate-fade-in-left { animation: fadeInLeft 0.5s ease-out; }
.animate-fade-in-right { animation: fadeInRight 0.5s ease-out; }
.animate-fade-in-up { animation: fadeInUp 0.5s ease-out; }

@keyframes fadeInLeft { from { opacity: 0; transform: translateX(-20px); } to { opacity: 1; transform: translateX(0); } }
@keyframes fadeInRight { from { opacity: 0; transform: translateX(20px); } to { opacity: 1; transform: translateX(0); } }
@keyframes fadeInUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }

.slide-left-enter-active, .slide-left-leave-active, .slide-right-enter-active, .slide-right-leave-active { 
  transition: all 0.5s cubic-bezier(0.25, 1, 0.5, 1); 
}
.slide-left-enter-from { opacity: 0; transform: translateX(100px); }
.slide-left-leave-to { opacity: 0; transform: translateX(-100px); }
.slide-right-enter-from { opacity: 0; transform: translateX(-100px); }
.slide-right-leave-to { opacity: 0; transform: translateX(100px); }
</style>