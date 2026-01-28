<template>
  <div>
    <h1>Test Minigame View</h1>
    <MashGame :isStarted="true" />
    <br>
    <SequenceGame :isStarted="true" />
  </div>
</template>

<script setup lang="ts">
import MashGame from '@/components/minigame/MashGame.vue';
import SequenceGame from '@/components/minigame/SequenceGame.vue';
</script>