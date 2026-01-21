<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';

// userStore 사용
import { useUserStore } from '@/stores/userStore'
const userStore = useUserStore()

// 모달들 임포트
import CreateRoomModal from '@/components/lobby/CreateRoomModal.vue'; 
import JoinRoomModal from '@/components/lobby/JoinRoomModal.vue'; 

const router = useRouter();
const nickname = ref('');
const isNicknameSet = ref(false);

// 모달 상태 관리
const showCreateModal = ref(false);
const showJoinModal = ref(false);

// 저장된 사용자 정보 복원
onMounted(() => {
  if (userStore.restoreUser()) {
    nickname.value = userStore.username
    isNicknameSet.value = true
  }
})

const confirmNickname = () => {
  if (nickname.value.trim().length < 2) {
    alert("닉네임을 2자 이상 입력해주세요.");
    return;
  }
  
  // 게스트로 로그인
  userStore.loginAsGuest(nickname.value.trim())
  isNicknameSet.value = true;
};

// 버튼 클릭 핸들러 (모달 열기)
const handleCreateRoom = () => { showCreateModal.value = true; };
const handleJoinRoom = () => { showJoinModal.value = true; };

</script>

<template>
  <div class="min-h-screen bg-[#F0F2F5] flex items-center justify-center p-8 font-sans">
    <div class="w-full max-w-md bg-white rounded-[32px] shadow-2xl p-10 border border-white flex flex-col items-center">
      
      <h1 class="text-4xl font-black text-[#2d3436] mb-10 tracking-tight">DocQ</h1>

      <div class="w-full space-y-4 mb-8">
        <div class="relative">
          <input 
            v-model="nickname"
            :disabled="isNicknameSet"
            type="text"
            placeholder="닉네임을 입력하세요"
            class="w-full h-16 px-6 rounded-2xl border-2 transition-all outline-none font-bold text-lg"
            :class="isNicknameSet 
              ? 'bg-[#f1f2f6] border-[#dfe6e9] text-[#b2bec3] cursor-not-allowed' 
              : 'bg-white border-[#6c5ce7] text-[#2d3436] focus:shadow-[0_0_0_4px_rgba(108,92,231,0.1)]'"
            @keyup.enter="!isNicknameSet && confirmNickname()"
          />
          <span v-if="isNicknameSet" class="absolute right-5 top-1/2 -translate-y-1/2 text-xl">🔒</span>
        </div>

        <button 
          v-if="!isNicknameSet"
          @click="confirmNickname"
          class="w-full h-14 rounded-2xl font-black text-white bg-[#6c5ce7] shadow-[0_4px_0_0_#5f3dc4] hover:bg-[#5f3dc4] active:translate-y-1 active:shadow-none transition-all"
        >
          확인
        </button>
      </div>

      <div 
        v-if="isNicknameSet" 
        class="w-full flex flex-col gap-4 animate-in"
      >
        <button 
          @click="handleCreateRoom"
          class="w-full h-16 rounded-2xl font-black text-xl text-white bg-[#00b894] shadow-[0_6px_0_0_#00896f] hover:bg-[#00a383] active:translate-y-1 active:shadow-none transition-all"
        >
          방 만들기 🏠
        </button>
        
        <button 
          @click="handleJoinRoom"
          class="w-full h-16 rounded-2xl font-black text-xl text-[#636e72] bg-white border-2 border-[#dfe6e9] hover:bg-[#f1f2f6] transition-all"
        >
          방 참가하기 🚪
        </button>

        <button @click="isNicknameSet = false" class="text-[#b2bec3] text-sm font-bold hover:underline mt-2">
          닉네임 수정하기
        </button>
      </div>

    </div>

    <CreateRoomModal v-if="showCreateModal" @close="showCreateModal = false" />
    <JoinRoomModal v-if="showJoinModal" @close="showJoinModal = false" />

  </div>
</template>

<style scoped>
.animate-in {
  animation: slideIn 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
@keyframes slideIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>