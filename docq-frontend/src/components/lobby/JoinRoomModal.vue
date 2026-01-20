<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const emit = defineEmits(['close']);

const roomCode = ref('');

const joinRoom = () => {
  if (!roomCode.value.trim()) {
    alert("입장 코드를 입력해주세요!");
    return;
  }
  // 입력된 코드로 대기실 이동
  router.push(`/waiting/${roomCode.value}`);
};
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-fade-in">
    
    <div class="w-full max-w-lg bg-white rounded-[32px] shadow-2xl p-8 border-4 border-white/20 animate-slide-up relative">
      
      <button @click="$emit('close')" class="absolute top-6 right-6 text-gray-400 hover:text-gray-600 font-bold text-xl">
        ✕
      </button>

      <h2 class="text-3xl font-black text-[#2d3436] mb-2 text-center">방 참가하기 🚪</h2>
      <p class="text-[#636e72] font-medium text-center mb-8">공유받은 입장 코드를 입력하세요.</p>

      <div class="mb-8">
        <label class="block text-[#636e72] font-bold mb-2 ml-1">입장 코드</label>
        <input 
          v-model="roomCode"
          type="text" 
          placeholder="예: A1B2" 
          class="w-full h-16 px-6 rounded-2xl border-2 border-[#dfe6e9] focus:border-[#6c5ce7] outline-none font-black text-2xl text-center tracking-widest uppercase transition-colors placeholder:font-normal placeholder:tracking-normal placeholder:text-lg"
          @keyup.enter="joinRoom"
        />
      </div>

      <div class="flex gap-3">
        <button 
          @click="$emit('close')"
          class="flex-1 h-14 rounded-2xl font-bold text-lg text-[#636e72] bg-[#f1f2f6] hover:bg-[#dfe6e9] transition-colors"
        >
          취소
        </button>
        <button 
          @click="joinRoom"
          class="flex-[2] h-14 rounded-2xl font-black text-lg text-white bg-[#6c5ce7] shadow-[0_4px_0_0_#5f3dc4] hover:bg-[#5f3dc4] active:translate-y-1 active:shadow-none transition-all"
        >
          입장하기
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
.animate-slide-up { animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1); }

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>
<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const emit = defineEmits(['close']);

const roomCode = ref('');
const password = ref('');
const isPrivateRoom = ref(false); // 비밀방 여부 (실제로는 서버에서 확인)

// 방참가 or 방취소 로직
const joinRoom = () => {
  if (!roomCode.value.trim()) {
    alert("입장 코드를 입력해주세요!");
    return;
  }
  
  // 비밀방인데 비밀번호 미입력
  if (isPrivateRoom.value && !password.value.trim()) {
    alert("비밀번호를 입력해주세요!");
    return;
  }
  
  // 실제 구현 시: 서버에 방 코드와 비밀번호 전송하여 검증
  // const response = await joinRoomAPI({ roomCode: roomCode.value, password: password.value });
  // if (response.success) { ... }
  
  // 입력된 코드로 대기실 이동
  router.push(`/waiting/${roomCode.value}`);
  emit('close');
};

// 방 코드 입력 시 비밀방 여부 확인 (실제로는 서버 API 호출)
const checkRoomType = () => {
  // 실제 구현 시: 서버에 방 코드 전송하여 비밀방 여부 확인
  // const response = await checkRoomAPI(roomCode.value);
  // isPrivateRoom.value = response.isPrivate;
  
  // 임시: 코드가 4자리면 비밀방으로 가정
  if (roomCode.value.length === 4) {
    // 실제로는 서버 응답으로 판단
    isPrivateRoom.value = false; // 기본값
  }
};
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-fade-in">
    
    <div class="w-full max-w-lg bg-white rounded-[32px] shadow-2xl p-8 border-4 border-white/20 animate-slide-up relative">
      
      <button @click="$emit('close')" class="absolute top-6 right-6 text-gray-400 hover:text-gray-600 font-bold text-xl">
        ✕
      </button>

      <h2 class="text-3xl font-black text-[#2d3436] mb-2 text-center">방 참가하기 🚪</h2>
      <p class="text-[#636e72] font-medium text-center mb-8">공유받은 입장 코드를 입력하세요.</p>

      <div class="space-y-6 mb-8">
        <!-- 입장 코드 입력 -->
        <div>
          <label class="block text-[#636e72] font-bold mb-2 ml-1">입장 코드</label>
          <input 
            v-model="roomCode"
            type="text" 
            placeholder="예: A1B2" 
            class="w-full h-16 px-6 rounded-2xl border-2 border-[#dfe6e9] focus:border-[#6c5ce7] outline-none font-black text-2xl text-center tracking-widest uppercase transition-colors placeholder:font-normal placeholder:tracking-normal placeholder:text-lg"
            @keyup.enter="joinRoom"
            @blur="checkRoomType"
          />
        </div>

        <!-- 비밀번호 입력 (비밀방인 경우만 표시) -->
        <div v-if="isPrivateRoom" class="animate-slide-down">
          <label class="block text-[#636e72] font-bold mb-2 ml-1 flex items-center gap-2">
            🔒 비밀번호
          </label>
          <input 
            v-model="password"
            type="password" 
            placeholder="비밀번호 4자리" 
            maxlength="4"
            class="w-full h-14 px-6 rounded-2xl border-2 border-[#dfe6e9] focus:border-[#6c5ce7] outline-none font-black text-xl text-center tracking-widest transition-colors"
            @keyup.enter="joinRoom"
          />
        </div>
      </div>

      <div class="flex gap-3">
        <button 
          @click="$emit('close')"
          class="flex-1 h-14 rounded-2xl font-bold text-lg text-[#636e72] bg-[#f1f2f6] hover:bg-[#dfe6e9] transition-colors"
        >
          취소
        </button>
        <button 
          @click="joinRoom"
          class="flex-[2] h-14 rounded-2xl font-black text-lg text-white bg-[#6c5ce7] shadow-[0_4px_0_0_#5f3dc4] hover:bg-[#5f3dc4] active:translate-y-1 active:shadow-none transition-all"
        >
          입장하기
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
.animate-slide-up { animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.animate-slide-down { animation: slideDown 0.3s cubic-bezier(0.16, 1, 0.3, 1); }

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@keyframes slideDown {
  from { 
    opacity: 0; 
    transform: translateY(-10px);
    max-height: 0;
  }
  to { 
    opacity: 1; 
    transform: translateY(0);
    max-height: 100px;
  }
}
</style>