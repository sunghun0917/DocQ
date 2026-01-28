<template>
  <div class="cloud-overlay" aria-hidden="true">
    <div
      v-for="cloud in clouds"
      :key="cloud.id"
      class="cloud"
      :class="cloud.typeClass"
      :style="{
        top: cloud.top,
        opacity: cloud.opacity,
        transform: `scale(${cloud.scale})`,
        animationDuration: `${cloud.durationS}s`,
        animationDelay: `${cloud.delayS}s`,
      }"
    />
  </div>
</template>

<script setup lang="ts">
const clouds = [
  { id: 'c1', typeClass: 'cloud-type-1', top: '6%', scale: 1.45, opacity: 0.35, durationS: 55, delayS: -10 },
  { id: 'c2', typeClass: 'cloud-type-2', top: '12%', scale: 1.2, opacity: 0.32, durationS: 62, delayS: -20 },
  { id: 'c3', typeClass: 'cloud-type-3', top: '18%', scale: 1.6, opacity: 0.30, durationS: 75, delayS: -45 },
  { id: 'c4', typeClass: 'cloud-type-1', top: '24%', scale: 1.05, opacity: 0.32, durationS: 60, delayS: -5 },
  { id: 'c5', typeClass: 'cloud-type-2', top: '30%', scale: 1.75, opacity: 0.28, durationS: 88, delayS: -60 },
  { id: 'c6', typeClass: 'cloud-type-3', top: '36%', scale: 1.35, opacity: 0.30, durationS: 72, delayS: -30 },
  { id: 'c7', typeClass: 'cloud-type-1', top: '42%', scale: 1.55, opacity: 0.30, durationS: 80, delayS: -50 },
  { id: 'c8', typeClass: 'cloud-type-2', top: '48%', scale: 1.15, opacity: 0.32, durationS: 58, delayS: -18 },
  { id: 'c9', typeClass: 'cloud-type-3', top: '54%', scale: 1.85, opacity: 0.28, durationS: 92, delayS: -70 },
  { id: 'c10', typeClass: 'cloud-type-1', top: '60%', scale: 1.25, opacity: 0.30, durationS: 70, delayS: -40 },
  { id: 'c11', typeClass: 'cloud-type-2', top: '68%', scale: 1.45, opacity: 0.28, durationS: 78, delayS: -55 },
  { id: 'c12', typeClass: 'cloud-type-3', top: '76%', scale: 1.3, opacity: 0.30, durationS: 66, delayS: -25 },
  { id: 'c13', typeClass: 'cloud-type-1', top: '84%', scale: 1.65, opacity: 0.28, durationS: 85, delayS: -65 },
  { id: 'c14', typeClass: 'cloud-type-2', top: '92%', scale: 1.1, opacity: 0.25, durationS: 74, delayS: -35 },
] as const
</script>

<style scoped>
.cloud-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 50;
  overflow: hidden;
}

.cloud {
  position: absolute;
  left: 110%;
  background: rgba(255, 255, 255, 0.8);
  filter: blur(0.35px);
  animation-name: moveClouds;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

.cloud::after,
.cloud::before {
  content: '';
  position: absolute;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 50%;
}

/* a bit bigger than MainView */
.cloud-type-1 {
  width: 200px;
  height: 65px;
  border-radius: 999px;
}

.cloud-type-1::after {
  width: 90px;
  height: 90px;
  top: -45px;
  left: 28px;
}

.cloud-type-1::before {
  width: 76px;
  height: 76px;
  top: -32px;
  left: 92px;
}

.cloud-type-2 {
  width: 260px;
  height: 60px;
  border-radius: 999px;
}

.cloud-type-2::after {
  width: 84px;
  height: 54px;
  top: -26px;
  left: 40px;
  border-radius: 50% 50% 0 0;
}

.cloud-type-2::before {
  width: 108px;
  height: 70px;
  top: -35px;
  left: 112px;
  border-radius: 50% 50% 0 0;
}

.cloud-type-3 {
  width: 170px;
  height: 50px;
  border-radius: 28px;
  box-shadow: 55px -18px 0 10px rgba(255, 255, 255, 0.55);
}

.cloud-type-3::after {
  width: 100px;
  height: 100px;
  top: -50px;
  left: 16px;
}

.cloud-type-3::before {
  width: 86px;
  height: 86px;
  top: -38px;
  left: 86px;
}

@keyframes moveClouds {
  0% {
    left: 110%;
  }
  100% {
    left: -30%;
  }
}
</style>
