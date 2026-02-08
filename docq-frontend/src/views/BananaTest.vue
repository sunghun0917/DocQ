<template>
    <div class="banana-test-container">
        <TresCanvas :shadows="true" clear-color="#333333">
            <TresPerspectiveCamera :position="[5, 5, 5]" :look-at="[0, 0, 0]" />

            <TresAmbientLight :intensity="1" />
            <TresDirectionalLight :position="[5, 10, 5]" :intensity="1.5" cast-shadow />

            <!-- Floor -->
            <TresMesh :rotation="[-Math.PI / 2, 0, 0]" :position="[0, 0, 0]" receive-shadow>
                <TresPlaneGeometry :args="[20, 20]" />
                <TresMeshStandardMaterial color="#888888" />
            </TresMesh>

            <!-- Grid Helper -->
            <primitive :object="gridHelper" />

            <!-- Character -->
            <GameCharacter model-url="/assets/character/character_chicken.glb" :position="charPos"
                :look-target="charLookTarget" :is-moving="isMoving" :is-banana-hit="isBananaHit" />
        </TresCanvas>

        <div class="ui-controls">
            <h1>🍌 Banana Jump Test</h1>
            <div class="btn-group">
                <button @click="triggerBananaJump">바나나 밟기 (1080도 회전 + 뒤로 3칸)</button>
                <button @click="resetPosition">위치 초기화</button>
            </div>
            <p>Status: {{ isMoving ? 'Moving (BananaHit: ' + isBananaHit + ')' : 'Idle' }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { TresCanvas } from '@tresjs/core'
import * as THREE from 'three'
import GameCharacter from '@/components/game/board3D/GameCharacter.vue'

const charPos = ref({ x: 0, y: 0, z: 0 })
const charLookTarget = ref({ x: 1, y: 0, z: 0 })
const isMoving = ref(false)
const isBananaHit = ref(false)

const gridHelper = new THREE.GridHelper(20, 20)

const triggerBananaJump = async () => {
    if (isMoving.value) return

    isBananaHit.value = true
    isMoving.value = true

    // 목표: 뒤로 3칸
    const targetX = charPos.value.x - 3

    // 1. 목표 지점 설정 (GameCharacter가 watch하여 애니메이션 시작)
    charPos.value = { ...charPos.value, x: targetX }

    // 2. 애니메이션 시간 대기 (GameCharacter BANANA_DURATION_S = 1.0s)
    // 약간의 여유를 두어 1050ms 대기
    await new Promise(r => setTimeout(r, 1050))

    isMoving.value = false
    isBananaHit.value = false
}

const resetPosition = () => {
    charPos.value = { x: 0, y: 0, z: 0 }
    isMoving.value = false
    isBananaHit.value = false
}
</script>

<style scoped>
.banana-test-container {
    width: 100vw;
    height: 100vh;
    position: relative;
    background: #111;
}

.ui-controls {
    position: absolute;
    top: 20px;
    right: 20px;
    background: rgba(255, 255, 255, 0.9);
    padding: 20px;
    border-radius: 12px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    font-family: sans-serif;
    z-index: 100;
}

h1 {
    font-size: 1.5rem;
    margin-bottom: 12px;
    color: #333;
}

.btn-group {
    display: flex;
    gap: 10px;
    margin-bottom: 12px;
}

button {
    padding: 10px 16px;
    background: #f59e0b;
    color: white;
    border: none;
    border-radius: 6px;
    cursor: pointer;
    font-weight: bold;
}

button:hover {
    background: #d97706;
}

button:last-child {
    background: #6b7280;
}

button:last-child:hover {
    background: #4b5563;
}
</style>
