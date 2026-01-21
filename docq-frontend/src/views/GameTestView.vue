<template>
  <div class="game-page">
    <div class="ui-overlay">
      <h2>BOARD GAME TEST</h2>
    </div>
    <BoardTest />
  </div>
</template>

<script setup>
import BoardTest from '@/components/BoardTest.vue'
</script>

<style scoped>
.game-page {
  width: 100%;
  height: 100vh;
  background: #111;
  position: relative;
}
.ui-overlay {
  position: absolute;
  top: 1rem;
  left: 1rem;
  z-index: 10;
  color: #fff;
}
</style>