<template>
  <div class="test-page">
    <div class="ui-overlay">
      <h2>Banana Map Rendering Test</h2>
      <div class="controls">
        <button @click="toggleBanana">Toggle Banana at Tile 5</button>
      </div>
    </div>
    <GameBoard />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import GameBoard from '@/components/game/board3D/GameBoard.vue'
import { useGameStore } from '@/stores/gameStore'

const gameStore = useGameStore()

const toggleBanana = () => {
  const currentEvents = gameStore.gameState.mapEvents || {}
  if (currentEvents['5'] === 'BANANA') {
    const newEvents = { ...currentEvents }
    delete newEvents['5']
    gameStore.setGameState({
      ...gameStore.gameState,
      mapEvents: newEvents
    })
  } else {
    gameStore.setGameState({
      ...gameStore.gameState,
      mapEvents: {
        ...currentEvents,
        '5': 'BANANA'
      }
    })
  }
}

onMounted(() => {
  // Initialize with some dummy data
  gameStore.setGameState({
    state: 'PLAYING',
    round: 1,
    currentTurnPlayerId: 'test-player-1',
    players: [
      {
        playerId: 'test-player-1',
        nickname: 'Tester',
        avatarId: 0,
        position: 1,
        isReady: true,
        isHost: true,
        connected: true,
        score: 0
      }
    ],
    mapEvents: {
      '5': 'BANANA',
      '10': 'BANANA',
      '8': 'TILE_X2' // For comparison (sphere)
    }
  })
})
</script>

<style scoped>
.test-page {
  width: 100vw;
  height: 100vh;
  position: relative;
  background: #333;
}
.ui-overlay {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 1000;
  background: rgba(0,0,0,0.7);
  color: white;
  padding: 20px;
  border-radius: 8px;
}
.controls {
  margin-top: 10px;
}
button {
  padding: 8px 12px;
  cursor: pointer;
}
</style>
