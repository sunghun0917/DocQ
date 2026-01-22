<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';

const props = defineProps<{
  buffCode: string;
  winnerName?: string;
}>();

const emit = defineEmits<{
  (e: 'done'): void;
}>();

const isSpinning = ref(false);
const rotation = ref(0);
const isVisible = ref(true);
const showResult = ref(false);

const labels = [
  { code: 'DOUBLE_SCORE', label: '점수 2배' },
  { code: 'PLUS_THREE', label: '+3칸 이동' }
];

const targetIndex = computed(() => {
  const idx = labels.findIndex(item => item.code === props.buffCode);
  return idx >= 0 ? idx : 0;
});

const wheelStyle = computed(() => {
  const step = 360 / labels.length;
  const stops: string[] = [];
  for (let i = 0; i < labels.length; i++) {
    const color = i % 2 === 0 ? '#cbd5e1' : '#94a3b8';
    const from = i * step;
    const to = (i + 1) * step;
    stops.push(`${color} ${from}deg ${to}deg`);
  }
  return {
    transform: `rotate(${rotation.value}deg)`,
    transition: isSpinning.value ? 'transform 2.8s cubic-bezier(0.1, 0, 0.1, 1)' : 'none',
    background: `conic-gradient(${stops.join(',')})`
  };
});

const sectionStyle = (n: number) => {
  const sectionAngle = 360 / labels.length;
  const angle = (n * sectionAngle) + (sectionAngle / 2);
  return { transform: `rotate(${angle}deg)` };
};

const spin = () => {
  if (isSpinning.value) return;
  isSpinning.value = true;
  showResult.value = false;

  const sectionAngle = 360 / labels.length;
  const totalSpins = 360 * 4;
  const current = ((rotation.value % 360) + 360) % 360;
  const targetRotation = -((targetIndex.value * sectionAngle) + (sectionAngle / 2));
  let delta = targetRotation - current;
  if (delta < -180) delta += 360;
  if (delta > 180) delta -= 360;

  rotation.value += totalSpins + delta;

  setTimeout(() => {
    isSpinning.value = false;
    setTimeout(() => {
      showResult.value = true;
      isVisible.value = false;
      emit('done');
    }, 1200);
  }, 2800);
};

onMounted(() => {
  setTimeout(() => spin(), 400);
});
</script>

<template>
  <div v-if="isVisible" class="roulette-overlay">
    <div class="roulette-modal">
      <div class="modal-header">
        <h3>증강 룰렛</h3>
        <p v-if="winnerName" class="winner-text">{{ winnerName }}님 보상</p>
      </div>
      <div class="modal-body">
        <div class="roulette-container">
          <div class="roulette-pointer">▼</div>
          <div class="roulette-wheel" :style="wheelStyle">
            <div
              v-for="(item, idx) in labels"
              :key="item.code"
              class="wheel-section"
              :style="sectionStyle(idx)"
            >
              <span class="section-label">{{ item.label }}</span>
            </div>
          </div>
        </div>
        <div class="info-text">
          <p v-if="showResult">{{ labels[targetIndex]?.label }} 획득!</p>
          <small v-if="showResult">잠시 후 게임이 이어집니다</small>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.roulette-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2500;
  backdrop-filter: blur(4px);
}

.roulette-modal {
  background: white;
  width: 420px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 16px 35px rgba(0,0,0,0.5);
  text-align: center;
  animation: popIn 0.25s ease-out;
}

.modal-header {
  background: #f3f4f6;
  padding: 14px;
  border-bottom: 2px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.4rem;
  color: #1f2937;
}

.winner-text {
  margin: 6px 0 0;
  font-size: 0.95rem;
  color: #6b7280;
  font-weight: 700;
}

.modal-body {
  padding: 24px 20px 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.roulette-container {
  position: relative;
  width: 180px;
  height: 200px;
  display: flex;
  justify-content: center;
  padding-top: 20px;
}

.roulette-pointer {
  position: absolute;
  top: 0;
  color: #ef4444;
  font-size: 28px;
  z-index: 10;
  filter: drop-shadow(0 2px 2px rgba(0,0,0,0.3));
}

.roulette-wheel {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  border: 4px solid white;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 10px rgba(0,0,0,0.3);
}

.wheel-section {
  position: absolute;
  width: 100%;
  height: 100%;
  transform-origin: 50% 50%;
  pointer-events: none;
}

.section-label {
  position: absolute;
  top: 20%;
  left: 60%;
  color: #1e293b;
  font-weight: 800;
  font-size: 14px;
  transform: rotate(30deg);
}

.info-text p {
  font-size: 1.1rem;
  font-weight: 800;
  margin: 4px 0;
}

.info-text small {
  color: #6b7280;
  font-weight: 700;
}

@keyframes popIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
</style>
