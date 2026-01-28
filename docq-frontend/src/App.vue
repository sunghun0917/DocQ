<script setup lang="ts">
import { RouterView } from 'vue-router'
import GlobalConfirmModal from '@/components/common/GlobalConfirmModal.vue'
</script>

<template>
  <RouterView />
  <GlobalConfirmModal />
</template>

<style>
/* 초기화 스타일 */
body {
  margin: 0;
  font-family: sans-serif;
}
</style>