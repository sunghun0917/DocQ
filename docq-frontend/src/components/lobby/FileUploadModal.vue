<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits(['close']);
const uploadStatus = ref<'IDLE' | 'UPLOADING' | 'DONE'>('IDLE');
const selectedFile = ref<File | null>(null);

const onFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files[0]) {
    selectedFile.value = target.files[0];
  }
};

const uploadFile = () => {
  if (!selectedFile.value) return;
  
  uploadStatus.value = 'UPLOADING';
  
  // Day 1: 가짜 타이머로 업로드 흉내 (UX 테스트용)
  setTimeout(() => {
    uploadStatus.value = 'DONE';
    console.log("Day 2에 Axios 연동할 부분");
  }, 2000);
};
</script>

<template>
  <div class="fixed inset-0 bg-black/50 flex items-center justify-center">
    <div class="bg-white p-6 rounded-lg shadow-xl w-96">
      <h2 class="text-xl font-bold mb-4">문서 업로드</h2>
      
      <div v-if="uploadStatus === 'IDLE'" class="space-y-4">
        <input type="file" accept=".pdf" @change="onFileChange" class="block w-full text-sm text-slate-500
          file:mr-4 file:py-2 file:px-4
          file:rounded-full file:border-0
          file:text-sm file:font-semibold
          file:bg-violet-50 file:text-violet-700
          hover:file:bg-violet-100
        "/>
        <button 
          @click="uploadFile" 
          :disabled="!selectedFile"
          class="w-full bg-indigo-600 text-white py-2 rounded disabled:bg-gray-300"
        >
          분석 시작
        </button>
      </div>

      <div v-else-if="uploadStatus === 'UPLOADING'" class="text-center py-8">
        <div class="animate-spin h-8 w-8 border-4 border-indigo-500 rounded-full border-t-transparent mx-auto mb-2"></div>
        <p>AI가 문제를 생성하고 있습니다...</p>
      </div>

      <div v-else class="text-center">
        <p class="text-green-600 font-bold text-lg mb-4">✅ 준비 완료!</p>
        <button @click="emit('close')" class="w-full bg-gray-800 text-white py-2 rounded">닫기</button>
      </div>
    </div>
  </div>
</template>