<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import GlobalConfirmModal from '@/components/common/GlobalConfirmModal.vue'
import CustomCursor from '@/components/common/CustomCursor.vue'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()

onMounted(() => {
  userStore.restoreUser()
})
</script>

<template>
  <CustomCursor /> 
  <RouterView />
  <GlobalConfirmModal />
</template>

<style>
/* 1. 전역 초기화 */
body {
  margin: 0;
  font-family: sans-serif;
  cursor: none !important; /* 기본 커서 숨김 */
  /* overflow: hidden;  <-- 주의: 이걸 켜면 스크롤이 아예 안 됩니다. 필요하면 주석 해제하세요 */
}

/* 2. [핵심] 모든 요소 + 모든 상태(hover 등)에서 커서 강제 숨김 */
/* 별표(*)를 써서 모든 태그를 잡아내야 손바닥이 절대 안 뜹니다 */
*, *::before, *::after,
a:hover, button:hover, input:hover, select:hover, 
[role="button"]:hover, .cursor-pointer:hover {
  cursor: none !important;
}

/* 3. 입력창 등도 확실하게 */
input, textarea, select, [contenteditable] {
  cursor: none !important;
}
</style>