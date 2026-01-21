<!-- src/views/GameView.vue -->
<template>
  <div class="game-page">
    <!-- UI 오버레이: 3D 캔버스 위에 텍스트나 점수판 등을 띄울 때 사용 -->
    <div class="ui-overlay">
      <!-- <h2>BOARD STAGE (3D)</h2> -->
    </div>

    <!-- 실제 3D 보드게임 컴포넌트 -->
    <Board />
  </div>
</template>

<script setup lang="ts">
import Board from '@/components/game/board3D/GameBoard.vue'
</script>

<style scoped>
/* 전체 화면을 채우는 컨테이너 */
.game-page {
  width: 100%;
  height: 100vh;
  background: #111; /* 배경색 (3D 캔버스 뒤) */
  position: relative;
  overflow: hidden; /* 스크롤 방지 */
}

/* UI 오버레이 스타일: 클릭 이벤트를 3D 쪽으로 통과시키려면 pointer-events: none 필요 */
.ui-overlay {
  position: absolute;
  top: 1rem;
  left: 1rem;
  z-index: 10;     /* 3D 캔버스보다 위에 표시 */
  color: #fff;
  pointer-events: none;
}
</style>
