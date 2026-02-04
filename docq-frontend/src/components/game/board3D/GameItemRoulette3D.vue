<script setup lang="ts">
import { shallowRef, watch, onMounted } from 'vue'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import * as THREE from 'three'

interface Props {
    rotation: number
    isSpinning: boolean
    modelPosition?: { x: number, y: number, z: number }
    modelScale?: number
    pivotOffset?: { x: number, y: number, z: number }
    arrowOffset?: { x: number, y: number, z: number }
    initialRotation?: { x: number, y: number, z: number }
    spinAxis?: 'x' | 'y' | 'z'
    showHelper?: boolean
    useRawModel?: boolean
}

const props = withDefaults(defineProps<Props>(), {
    modelPosition: () => ({ x: 0, y: 0, z: 0 }),
    modelScale: 1,
    pivotOffset: () => ({ x: 0, y: 0, z: 0 }),
    arrowOffset: () => ({ x: 0, y: 0, z: 4.5 }),
    initialRotation: () => ({ x: 0, y: 0, z: 0 }),
    spinAxis: 'y',
    showHelper: false,
    useRawModel: false
})

const baseModel = shallowRef<THREE.Group | null>(null)
const arrowModel = shallowRef<THREE.Group | null>(null)
const mainGroup = shallowRef<THREE.Group | null>(null)

onMounted(async () => {
    const loader = new GLTFLoader()
    const models = {
        base: '/assets/models/item_roullet.glb',
        arrow: '/assets/models/arrow.glb'
    }

    try {
        const [baseGltf, arrowGltf] = await Promise.all([
            loader.loadAsync(models.base),
            loader.loadAsync(models.arrow)
        ])

        const group = new THREE.Group()

        if (baseGltf.scene) {
            const wheelWrapper = new THREE.Group()
            // 원판이 너무 작은 경우를 대비한 보정
            const box = new THREE.Box3().setFromObject(baseGltf.scene)
            const size = box.getSize(new THREE.Vector3())
            if (size.length() < 0.2) {
                baseGltf.scene.scale.set(100, 100, 100)
            }
            wheelWrapper.add(baseGltf.scene)
            group.add(wheelWrapper)
            baseModel.value = wheelWrapper
        }

        if (arrowGltf.scene) {
            const arrowWrapper = new THREE.Group()
            // 화살표 크기 보정
            const box = new THREE.Box3().setFromObject(arrowGltf.scene)
            const size = box.getSize(new THREE.Vector3())
            if (size.length() < 0.2) {
                arrowGltf.scene.scale.set(100, 100, 100)
            } else {
                arrowGltf.scene.scale.set(0.8, 0.8, 0.8)
            }
            arrowWrapper.add(arrowGltf.scene)
            group.add(arrowWrapper)
            arrowModel.value = arrowWrapper
        }

        mainGroup.value = group
    } catch (error) {
        console.error('❌ Item Roulette 3D 로드 오류:', error)
    }
})

watch([() => props.rotation, mainGroup, arrowModel, () => props.spinAxis, () => props.initialRotation, () => props.arrowOffset, () => props.modelPosition, () => props.modelScale], () => {
    if (mainGroup.value) {
        mainGroup.value.position.set(props.modelPosition.x, props.modelPosition.y, props.modelPosition.z)
        mainGroup.value.scale.set(props.modelScale, props.modelScale, props.modelScale)

        mainGroup.value.rotation.set(
            THREE.MathUtils.degToRad(props.initialRotation.x),
            THREE.MathUtils.degToRad(props.initialRotation.y),
            THREE.MathUtils.degToRad(props.initialRotation.z)
        )
    }

    if (baseModel.value) {
        const rad = THREE.MathUtils.degToRad(props.rotation)
        if (props.spinAxis === 'x') baseModel.value.rotation.x = rad
        else if (props.spinAxis === 'y') baseModel.value.rotation.y = rad
        else baseModel.value.rotation.z = rad
    }

    if (arrowModel.value) {
        arrowModel.value.position.set(props.arrowOffset.x, props.arrowOffset.y, props.arrowOffset.z)
    }
}, { immediate: true, deep: true })
</script>

<template>
    <TresGroup v-if="mainGroup">
        <primitive :object="mainGroup" />
        <TresAmbientLight :intensity="1.5" />
        <TresDirectionalLight :position="[2, 5, 2]" :intensity="1.5" cast-shadow />
    </TresGroup>
</template>
