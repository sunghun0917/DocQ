<!-- src/components/game/board3d/Board.vue -->
<template>
  <div class="board-container">
    <!-- 3D 캔버스 초기화: 그림자(shadows) 활성화, 투명 배경(alpha) -->
    <TresCanvas shadows alpha clear-color="#222222">
      <!--
        OrthographicCamera (직교 카메라):
        원근감이 없는 쿼터뷰 시점을 제공하여 보드게임판처럼 보이게 함
        zoom: 확대 배율, position: 카메라 위치
      -->
      <TresOrthographicCamera
        :position="[10, 20, 10]"
        :look-at="[0, 0, 0]"
        :zoom="0.2"
        :near="-100"
        :far="100"
      />

      <!-- 개발용 마우스 컨트롤 (줌/회전 가능) -->
      <OrbitControls :enableZoom="true" />

      <!-- 조명 설정: 전체적으로 밝게(Ambient) + 그림자용(Directional) -->
      <TresAmbientLight :intensity="1.2" />
      <TresDirectionalLight
        :position="[10, 15, 10]"
        :intensity="2"
        cast-shadow
      />

      <!--
        맵 타일 렌더링:
        v-for를 사용해 grid 배열의 모든 타일을 3D로 그림
        type 0: 빈칸, 1: 길, 2: 시작/끝
      -->
      <Tile3D
        v-for="tile in grid"
        :key="`${tile.x}-${tile.y}`"
        :x="tile.x"
        :y="tile.y"
        :type="tile.type"
      />

      <!--
        플레이어 캐릭터 렌더링:
        Suspense 컴포넌트는 비동기 모델 로딩(FBX)을 기다려줌
      -->
      <Suspense>
        <GameCharacter
          v-for="(p, idx) in players"
          :key="p.id"
          :x="playerCoords(idx).x"
          :y="playerCoords(idx).y"
          :isMoving="isMoving && currentTurn === idx"
          :lookTarget="playerNextTarget(idx)"
          :color="p.color"
        />
      </Suspense>
    </TresCanvas>

    <!-- UI: 현재 누구 턴인지 표시 -->
    <div v-if="gameStarted" class="turn-info">
      <h2>Current Turn: Player {{ currentTurn + 1 }}</h2>
    </div>

    <!-- UI: 게임 시작 전 인원수 선택 화면 -->
    <div v-if="!gameStarted" class="start-screen">
      <h1>Select Player Count</h1>
      <div class="buttons">
        <!-- 인원수 버튼 (2~4명) -->
        <button @click="startGame(2)">2 Players</button>
        <button @click="startGame(3)">3 Players</button>
        <button @click="startGame(4)">4 Players</button>
      </div>
    </div>

    <!-- UI: 룰렛 컴포넌트 -->
    <GameRoulette
      v-if="gameStarted"
      :rotation="rotation"
      :isMoving="isMoving"
      :isSpinning="isSpinning"
      :labelByIndex="labelByIndex"
      @spin="spinRoulette"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue' // watch 추가
import { TresCanvas } from '@tresjs/core'
import { OrbitControls } from '@tresjs/cientos'
import { useGameStore } from '@/stores/gameStore' // [NEW]
import { useRoomStore } from '@/stores/roomtStore' // [NEW]

// [컴포넌트 Import]
import Tile3D from '@/components/game/board3D/Tile3D.vue'
import GameCharacter from '@/components/game/board3D/GameCharacter.vue'
import GameRoulette from '@/components/game/board3D/GameRoulette.vue'

const gameStore = useGameStore()
const roomStore = useRoomStore()
const myUserId = computed(() => roomStore.myUser?.id || '')

// ... (interfaces)

/* ========== 게임 설정 상수 ========== */
// ... (grid, path logic)

// ...

/* ========== 플레이어 및 게임 상태 관리 ========== */
const players = ref<Player[]>([])     // 플레이어 목록
const currentTurn = ref(0)            // 현재 턴 (0 ~ players.length-1)
// const gameStarted = ref(false)     // (기존)
const gameStarted = computed(() => !gameStore.isGameEnded) // Store 연동

// ... (startGame logic remains for initialization but improved)

// ...

/* ========== 룰렛 및 이동 로직 ========== */
const isMoving = ref(false)     
const isSpinning = ref(false)   
const rotation = ref(0)         
const labelByIndex: number[] = [1, 2, 3, 4, 5, 6] 

// 룰렛 표시 여부: Store에서 winnerId가 설정되었을 때만
const showRoulette = computed(() => !!gameStore.rouletteTargetId)
const canSpin = computed(() => showRoulette.value && gameStore.rouletteTargetId === myUserId.value)

/**
 * 룰렛 돌리기 함수
 */
const spinRoulette = () => {
  if (isMoving.value || isSpinning.value) return 
  // 권한 체크
  if (!canSpin.value) return 

  isSpinning.value = true

  // 1~6 랜덤값 생성 (나중에 서버 동기화 필요)
  const result = Math.floor(Math.random() * 6) + 1
  const targetIndex = labelByIndex.indexOf(result)

  // 회전 애니메이션
  const sectionAngle = 360 / 6
  const totalSpins = 360 * 5
  const currentDegrees = ((rotation.value % 360) + 360) % 360
  const extraToZero = (360 - currentDegrees) % 360
  const targetRotation = -(targetIndex * sectionAngle)

  rotation.value += totalSpins + extraToZero + targetRotation

  // 회전 종료 후 이동
  setTimeout(() => {
    isSpinning.value = false
    moveStep(result)
  }, 3200)
}

/**
 * 말 이동 로직 (한 칸씩 점프) -> 종료 후 Store에 알림
 * @param steps 이동할 칸 수
 */
const moveStep = async (steps: number) => {
  isMoving.value = true

  // 이동할 플레이어 찾기 (rouletteTargetId 기반)
  const targetId = gameStore.rouletteTargetId
  const activePlayer = players.value.find(p => p.id === Number(targetId) || p.id === targetId) // ID 타입 주의 (string vs number)
  // 임시: players.value의 id가 지금 number(idx)로 되어있어서 매핑 필요할 수 있음. 
  // initializeGame에서 roomStore.user.id를 매핑하지 않고 idx(0,1..)를 쓰고 있다면 문제 발생.
  // 이 부분은 initializeGame 로직을 확인해야 함. (현재 id: i 로 되어있음)
  
  // [HOTFIX] 현재 GameBoard init 로직상 id는 0,1,2 인덱스임.
  // roomStore.users[id] === targetId 인 플레이어를 찾아야 함.
  
  // 실제 타겟 플레이어 객체 찾기
  let targetPlayer: Player | undefined
  roomStore.users.forEach((u, idx) => {
      if (u.id === targetId) {
          targetPlayer = players.value[idx]
      }
  })

  if (!targetPlayer) {
    console.error("No active player found for ID:", targetId)
    isMoving.value = false
    gameStore.handleRouletteComplete() // 에러 나더라도 넘어가야 함
    return
  }

  // 한 칸씩 차례대로 이동
  for (let i = 0; i < steps; i++) {
    if (targetPlayer.currentIndex >= pathCoords.value.length - 1) break
    await jumpStep(targetPlayer)
  }

  isMoving.value = false

  /* [규칙] 잡기(Knockback) 로직 */
  if (targetPlayer.currentIndex > 0 && targetPlayer.currentIndex < pathCoords.value.length - 1) {
    players.value.forEach((p) => {
      if (p.id !== targetPlayer?.id && p.currentIndex === targetPlayer.currentIndex) {
        console.log(`Player ${p.id} caught!`)
        p.currentIndex = 0 
      }
    })
  }

  // 이동 완료 -> Store에 알림 (다음 퀴즈로)
  gameStore.handleRouletteComplete()
}

// 한 칸 이동 애니메이션 (Promise로 비동기 대기)
const jumpStep = (player: Player) =>
  new Promise<void>((resolve) => {
    if (player.currentIndex >= pathCoords.value.length - 1) {
      resolve()
      return
    }

    player.currentIndex++

    // 애니메이션 시간만큼 대기 (Character.vue의 이동 시간과 합을 맞춰야 함)
    setTimeout(() => {
      setTimeout(resolve, 100)
    }, 600)
  })
</script>

<style scoped>
/* 화면 전체를 가득 채우는 컨테이너 */
.board-container {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
}

/* 시작 화면(인원 선택) 오버레이 */
.start-screen {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  background: rgba(0,0,0,0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  z-index: 100;
}

/* 현재 턴 표시 UI */
.turn-info {
  position: absolute;
  top: 10px;
  left: 10px;
  color: white;
  z-index: 10;
}

.buttons {
  display: flex;
  gap: 20px;
  margin-top: 20px;
}

/* 버튼 스타일 */
button {
  padding: 15px 30px;
  font-size: 1.2rem;
  background: white;
  color: black;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: bold;
}

button:hover {
  background: #ddd;
}
</style>
