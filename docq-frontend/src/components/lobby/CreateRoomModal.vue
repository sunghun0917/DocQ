<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const emit = defineEmits(['close']);

// 방 정보 상태
const roomName = ref('');
const maxUsers = ref(4);
const isSecret = ref(false); // 비밀방 여부
const password = ref('');

// PDF 업로드 상태: 'idle'(대기) | 'parsing'(분석중) | 'done'(완료)
const uploadStatus = ref<'idle' | 'parsing' | 'done'>('idle');
const fileName = ref('');

// 파일 선택 핸들러 (숨겨진 input 트리거)
const fileInput = ref<HTMLInputElement | null>(null);
const triggerFileUpload = () => {
  if (uploadStatus.value === 'idle') {
    fileInput.value?.click();
  }
};

// 실제 파일이 선택되었을 때 (파싱 시뮬레이션)
const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    const file = target.files[0];
    if (file) {
      fileName.value = file.name;
      
      // 1. 분석 시작 (로딩 상태)
      uploadStatus.value = 'parsing';
      
      // 2. 3초 뒤 분석 완료 처리 (Mocking)
      setTimeout(() => {
        uploadStatus.value = 'done';
      }, 3000);
    }
  }
};

// 생성하기 버튼 활성화 조건: 방제목 입력됨 AND PDF 분석 완료됨
const isReadyToCreate = computed(() => {
  return roomName.value.trim().length > 0 && uploadStatus.value === 'done';
});

const createRoom = () => {
  if (!isReadyToCreate.value) return;
  
  // 실제로는 여기서 file과 roomInfo를 서버로 전송
  const newRoomId = 'R' + Math.floor(Math.random() * 1000);
  router.push(`/waiting/${newRoomId}`);
  emit('close');
};
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-fade-in">
    
    <div class="w-full max-w-4xl bg-white rounded-[32px] shadow-2xl p-8 border-4 border-white/20 animate-slide-up relative flex flex-col">
      
      <button @click="$emit('close')" class="absolute top-6 right-6 text-gray-400 hover:text-gray-600 font-bold text-xl z-10">
        ✕
      </button>

      <h2 class="text-3xl font-black text-[#2d3436] mb-8 text-center">방 만들기 🛠️</h2>

      <div class="flex flex-col md:flex-row gap-8 mb-8">
        
        <div class="flex-1">
          <label class="block text-[#636e72] font-bold mb-2 ml-1">퀴즈 생성용 PDF</label>
          
          <div 
            @click="triggerFileUpload"
            class="w-full aspect-square rounded-[24px] border-4 border-dashed transition-all flex flex-col items-center justify-center relative overflow-hidden group cursor-pointer"
            :class="[
              uploadStatus === 'idle' ? 'border-[#dfe6e9] bg-[#f1f2f6] hover:bg-[#dfe6e9] hover:border-[#b2bec3]' : '',
              uploadStatus === 'parsing' ? 'border-[#6c5ce7] bg-[#f1f2f6]' : '',
              uploadStatus === 'done' ? 'border-[#00b894] bg-[#f0fff4]' : ''
            ]"
          >
            <input 
              ref="fileInput"
              type="file" 
              accept=".pdf"
              class="hidden"
              @change="handleFileChange" 
            />

            <div v-if="uploadStatus === 'idle'" class="text-center group-hover:scale-105 transition-transform">
              <div class="text-6xl mb-4 text-[#b2bec3]">📂</div>
              <p class="text-[#636e72] font-bold text-lg">PDF를 업로드해주세요</p>
              <p class="text-[#b2bec3] text-sm mt-2 font-medium">클릭하여 파일 선택</p>
            </div>

            <div v-if="uploadStatus === 'parsing'" class="text-center flex flex-col items-center">
              <div class="w-16 h-16 border-4 border-[#a29bfe] border-t-[#6c5ce7] rounded-full animate-spin mb-6"></div>
              <p class="text-[#6c5ce7] font-black text-lg animate-pulse">퀴즈 생성 중...</p>
              <p class="text-[#a29bfe] text-sm mt-1 font-bold">{{ fileName }}</p>
            </div>

            <div v-if="uploadStatus === 'done'" class="text-center animate-bounce-up">
              <div class="text-6xl mb-4 text-[#00b894]">✅</div>
              <p class="text-[#00b894] font-black text-lg">준비가 완료되었습니다!</p>
              <p class="text-[#00b894] text-sm mt-1 font-bold opacity-70">{{ fileName }}</p>
            </div>
          </div>
          <p class="text-xs text-[#b2bec3] mt-2 text-center font-medium">* PDF 크기 및 페이지 수를 고려해주세요.</p>
        </div>

        <div class="flex-1 flex flex-col gap-6">
          
          <div>
            <label class="block text-[#636e72] font-bold mb-2 ml-1">방 제목</label>
            <input 
              v-model="roomName"
              type="text" 
              placeholder="예: 싸피 공통 프로젝트 퀴즈!" 
              class="w-full h-14 px-5 rounded-2xl border-2 border-[#dfe6e9] focus:border-[#6c5ce7] outline-none font-bold text-lg transition-colors placeholder:font-normal"
            />
          </div>

          <div>
            <label class="block text-[#636e72] font-bold mb-2 ml-1">최대 인원</label>
            <div class="flex gap-3">
              <button 
                v-for="num in [2, 3, 4]" 
                :key="num"
                @click="maxUsers = num"
                class="flex-1 h-12 rounded-xl font-bold border-2 transition-all"
                :class="maxUsers === num 
                  ? 'bg-[#6c5ce7] text-white border-[#6c5ce7] shadow-md' 
                  : 'bg-white text-[#b2bec3] border-[#dfe6e9] hover:bg-[#f1f2f6]'"
              >
                {{ num }}명
              </button>
            </div>
          </div>

          <div class="bg-[#f8f9fa] p-5 rounded-2xl border-2 border-[#dfe6e9]">
            <div class="flex items-center justify-between mb-2">
              <span class="text-[#636e72] font-bold flex items-center gap-2">
                🔒 비밀방 설정
              </span>
              <button 
                @click="isSecret = !isSecret"
                class="w-12 h-7 rounded-full transition-colors relative"
                :class="isSecret ? 'bg-[#6c5ce7]' : 'bg-[#dfe6e9]'"
              >
                <div 
                  class="w-5 h-5 bg-white rounded-full absolute top-1 transition-all shadow-sm"
                  :class="isSecret ? 'left-6' : 'left-1'"
                ></div>
              </button>
            </div>

            <div v-if="isSecret" class="animate-fade-in mt-3">
              <input 
                v-model="password"
                type="password" 
                placeholder="비밀번호 4자리" 
                class="w-full h-12 px-4 rounded-xl border-2 border-[#dfe6e9] focus:border-[#6c5ce7] outline-none font-bold text-center tracking-widest"
                maxlength="4"
              />
            </div>
          </div>

        </div>
      </div>

      <div class="flex gap-3 mt-auto">
        <button 
          @click="$emit('close')"
          class="flex-1 h-16 rounded-2xl font-bold text-lg text-[#636e72] bg-[#f1f2f6] hover:bg-[#dfe6e9] transition-colors"
        >
          취소
        </button>
        
        <button 
          @click="createRoom"
          :disabled="!isReadyToCreate"
          class="flex-[2] h-16 rounded-2xl font-black text-xl transition-all flex items-center justify-center gap-2"
          :class="isReadyToCreate 
            ? 'text-white bg-[#00b894] shadow-[0_6px_0_0_#00896f] hover:bg-[#00a383] active:translate-y-1 active:shadow-none cursor-pointer' 
            : 'text-[#b2bec3] bg-[#dfe6e9] border-2 border-transparent cursor-not-allowed'"
        >
          <span v-if="uploadStatus === 'parsing'">⏳ 분석 중...</span>
          <span v-else>방 생성하기 🚀</span>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.animate-fade-in { animation: fadeIn 0.3s ease-out; }
.animate-slide-up { animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1); }
.animate-bounce-up { animation: bounceUp 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275); }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { transform: translateY(30px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
@keyframes bounceUp { 0% { transform: scale(0.8); opacity: 0; } 60% { transform: scale(1.05); } 100% { transform: scale(1); opacity: 1; } }
</style>