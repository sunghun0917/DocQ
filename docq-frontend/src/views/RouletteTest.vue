<script setup lang="ts">
import { ref } from 'vue'
import { TresCanvas } from '@tresjs/core'
import { OrbitControls } from '@tresjs/cientos'
import GameRoulette3D from '@/components/game/board3D/GameRoulette3D.vue'
import gsap from 'gsap'

const rotation = ref(0)
const isSpinning = ref(false)

// [테스트 랩 모든 컨트롤러 데이터 - RouletteOverlay.vue와 동일한 '골든 세팅']
const initialRotation = ref({ x: 140, y: 0, z: 0 })
const arrowOffset = ref({ x: 0, y: 0, z: 3.2 })
const modelScale = ref(1.5)                         // 1.5로 조정
const modelPosition = ref({ x: 0, y: -0.5, z: 0 })  // 위치 보정값 추가
const cameraPosition = ref({ x: 0, y: 6, z: -10 })  // 카메라 위치 [0, 6, -10]
const lookAt = ref({ x: 0, y: 0, z: 0 })
const pivotOffset = ref({ x: 0, y: 0, z: 0 })
const landingOffset = ref(60)

const labels = ref([-1, 1, 2, 3, 4, 5, 6, 7])
const targetValue = ref(1)

const spinToTarget = () => {
    if (isSpinning.value) return
    isSpinning.value = true
    const targetIndex = labels.value.indexOf(targetValue.value)
    const sectionAngle = 360 / labels.value.length
    const totalSpins = 360 * 3
    const currentDegrees = ((rotation.value % 360) + 360) % 360
    const targetFinalAngle = -(targetIndex * sectionAngle) + landingOffset.value
    const extra = (360 - currentDegrees) % 360
    const targetRotationValue = rotation.value + totalSpins + extra + targetFinalAngle

    gsap.to(rotation, {
        value: targetRotationValue,
        duration: 3,
        ease: "power4.out",
        onComplete: () => { isSpinning.value = false }
    })
}
const reset = () => { rotation.value = 0 }
</script>

<template>
    <div class="test-page">
        <div class="ui-layer">
            <h2>3D 룰렛 통합 테스트 랩</h2>

            <div class="control-group highlight">
                <h3>1. 회전 & 영점 보정</h3>
                <div class="input-item">목표: <select v-model.number="targetValue">
                        <option v-for="n in labels" :value="n">{{ n }}</option>
                    </select></div>
                <div class="input-item">보정(°): <input type="number" v-model.number="landingOffset"></div>
                <button @click="spinToTarget" :disabled="isSpinning">테스트 회전</button>
            </div>

            <div class="control-group">
                <h3>2. 카메라 설정 (Position / LookAt)</h3>
                <div class="control-section">
                    <h4>위치(Position)</h4>
                    <div class="input-item">X: <input type="number" v-model.number="cameraPosition.x"></div>
                    <div class="input-item">Y: <input type="number" v-model.number="cameraPosition.y"></div>
                    <div class="input-item">Z: <input type="number" v-model.number="cameraPosition.z"></div>
                </div>
                <div class="control-section" style="margin-top:10px;">
                    <h4>시선(Look-At)</h4>
                    <div class="input-item">X: <input type="number" v-model.number="lookAt.x"></div>
                    <div class="input-item">Y: <input type="number" v-model.number="lookAt.y"></div>
                    <div class="input-item">Z: <input type="number" v-model.number="lookAt.z"></div>
                </div>
            </div>

            <div class="control-group">
                <h3>3. 화면상 모델 설정 (Scale & Pos)</h3>
                <div class="input-item"><b>크기 (Scale):</b> <input type="number" step="0.1" v-model.number="modelScale">
                </div>
                <div class="input-section" style="margin-top:5px;">
                    <h4>위치(Position)</h4>
                    <div class="input-item">X: <input type="number" step="0.1" v-model.number="modelPosition.x"></div>
                    <div class="input-item">Y: <input type="number" step="0.1" v-model.number="modelPosition.y"></div>
                    <div class="input-item">Z: <input type="number" step="0.1" v-model.number="modelPosition.z"></div>
                </div>
            </div>

            <div class="control-group">
                <h3>4. 화살표 위치 (Arrow Offset)</h3>
                <div class="input-item">X: <input type="number" step="0.1" v-model.number="arrowOffset.x"></div>
                <div class="input-item">Y: <input type="number" step="0.1" v-model.number="arrowOffset.y"></div>
                <div class="input-item">Z: <input type="number" step="0.1" v-model.number="arrowOffset.z"></div>
            </div>

            <div class="control-group">
                <h3>5. 판 기울기 (Tilt)</h3>
                <div class="input-item">X (Tilt): <input type="number" v-model.number="initialRotation.x"></div>
            </div>
        </div>

        <div class="canvas-container">
            <TresCanvas shadows alpha>
                <TresPerspectiveCamera :position="[cameraPosition.x, cameraPosition.y, cameraPosition.z]"
                    :look-at="[lookAt.x, lookAt.y, lookAt.z]" />
                <OrbitControls />
                <Suspense>
                    <GameRoulette3D :rotation="rotation" :isSpinning="isSpinning" :model-scale="modelScale"
                        :model-position="modelPosition" :arrow-offset="arrowOffset" :initial-rotation="initialRotation"
                        spin-axis="y" use-raw-model />
                </Suspense>
                <TresGridHelper :args="[10, 10]" />
            </TresCanvas>
        </div>
    </div>
</template>

<style scoped>
.test-page {
    width: 100vw;
    height: 100vh;
    background-color: #1a1a1a;
    display: flex;
    color: white;
    font-family: sans-serif;
}

.ui-layer {
    width: 350px;
    background: rgba(0, 0, 0, 0.9);
    padding: 20px;
    overflow-y: auto;
    border-right: 1px solid #444;
}

.control-group {
    background: rgba(255, 255, 255, 0.05);
    padding: 12px;
    border-radius: 8px;
    margin-bottom: 15px;
    border: 1px solid #333;
}

.highlight {
    border-color: #4f46e5;
    background: rgba(79, 70, 229, 0.1);
}

h2 {
    margin-bottom: 15px;
    color: #818cf8;
    font-size: 1.2rem;
    text-align: center;
}

h3 {
    font-size: 0.9rem;
    margin-bottom: 8px;
    color: #a5b4fc;
}

h4 {
    font-size: 0.75rem;
    margin-bottom: 5px;
    color: #94a3b8;
}

.input-item {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
    font-family: monospace;
    font-size: 0.8rem;
}

input[type="number"],
select {
    flex: 1;
    background: #222;
    border: 1px solid #444;
    color: white;
    padding: 4px;
    border-radius: 4px;
}

.canvas-container {
    flex: 1;
    height: 100%;
    position: relative;
}

button {
    width: 100%;
    padding: 8px;
    margin-top: 8px;
    cursor: pointer;
    background: #4f46e5;
    color: white;
    border: none;
    border-radius: 4px;
    font-weight: bold;
}

button:disabled {
    background: #333;
    cursor: not-allowed;
}
</style>
