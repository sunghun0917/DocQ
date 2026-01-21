<script setup lang="ts">
import { ref, computed } from 'vue';
import ResultModal from '@/components/game/ResultModal.vue';
import GameBoard from '@/components/game/board3D/GameBoard.vue';
import {useRoomStore} from "@/stores/roomStore.ts";
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
  <div class="game-page">
    <!-- 3D 보드게임 컴포넌트 (배경) -->
    <GameBoard />

    <!-- UI 오버레이: 3D 캔버스 위에 텍스트나 모달 등을 띄움 -->
    <div class="ui-overlay">
      <h1 class="text-white text-center pt-20 text-4xl font-bold drop-shadow-md">게임 진행 중...</h1>

      <button
        @click="isGameEnded = !isGameEnded"
        class="absolute bottom-10 left-10 bg-white text-black p-4 rounded shadow-lg hover:bg-gray-100 pointer-events-auto"
      >
        결과창 토글 (테스트)
      </button>

      <!-- 결과 모달 -->
      <ResultModal v-if="isGameEnded" class="pointer-events-auto" />
    </div>
  </div>
</template>

<style scoped>
/* 전체 화면을 채우는 컨테이너 */
.game-page {
  width: 100vw;
  height: 100vh;
  background: #222; /* 3D 캔버스 로딩 전 배경색 */
  position: relative;
  overflow: hidden;
}

/* UI 오버레이 스타일 */
.ui-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 10;     /* 3D 캔버스보다 위에 표시 */
  pointer-events: none; /* 클릭 이벤트를 3D 쪽으로 통과시킴 (버튼 등은 pointer-events-auto 필요) */
}

/* 유틸리티 클래스 (Tailwind가 적용 안 될 경우를 대비해 pointer-events 명시) */
.pointer-events-auto {
  pointer-events: auto;
}
</style>
