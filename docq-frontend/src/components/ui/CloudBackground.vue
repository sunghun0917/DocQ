<template>
  <div class="sky-background" aria-hidden="true" />
</template>

<script setup lang="ts">
</script>

<style scoped>
.sky-background {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: linear-gradient(to bottom, #4fc3f7 0%, #b3e5fc 100%);
}
</style>
