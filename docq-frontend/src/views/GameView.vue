<script setup lang="ts">
import { ref, computed } from 'vue';
import ResultModal from '@/components/game/ResultModal.vue'; // 방금 만든 모달 임포트

import { useRoomStore } from '@/stores/roomStore'
const roomStore = useRoomStore()

// 방 정보 가져오기
const roomInfo = computed(() => roomStore.roomInfo)
const users = computed(() => roomStore.users)
const myUser = computed(() => roomStore.myUser)
const myUserId = computed(() => myUser.value?.id || '')
const isReady = computed(() => myUser.value?.isReady || false)

// 준비 상태 토글
const toggleReady = () => {
  if (myUserId.value) {
    roomStore.updateUser(myUserId.value, { isReady: !isReady.value })
  }
}

// 게임 종료 상태 (테스트용: true로 두면 들어가자마자 모달 뜸)
const isGameEnded = ref(true); 
</script>

<template>
  <div class="h-screen bg-green-800 relative">
    <h1 class="text-white text-center pt-20 text-4xl">게임 진행 중...</h1>
    
    <button @click="isGameEnded = !isGameEnded" class="absolute bottom-10 left-10 bg-white p-4 rounded">
      결과창 토글 (테스트)
    </button>

    <ResultModal v-if="isGameEnded" />
  </div>
</template>
