<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits(['close', 'submit']);

const title = ref('');
const content = ref('');

const handleSubmit = () => {
  if (!title.value.trim() || !content.value.trim()) {
    alert('제목과 내용을 모두 입력해주세요!');
    return;
  }
  emit('submit', { title: title.value, content: content.value });
};
</script>

<template>
  <div class="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 backdrop-blur-sm" @click.self="emit('close')">
    <div class="bg-[#fff8e1] border-[6px] border-[#5d4037] rounded-[24px] p-6 w-[90%] max-w-[500px] shadow-2xl relative animate-pop-in">
      
      <!-- 헤더 -->
      <div class="flex justify-between items-center mb-6">
        <h3 class="text-2xl font-black text-[#5d4037] flex items-center gap-2">
          <span class="material-symbols-outlined text-3xl text-[#7cb342]">edit_square</span>
          글쓰기
        </h3>
        <button @click="emit('close')" class="material-symbols-outlined text-[#bcaaa4] hover:text-[#5d4037] text-3xl transition-colors">cancel</button>
      </div>

      <!-- 입력 폼 -->
      <div class="flex flex-col gap-4">
        <div>
          <label class="block font-bold text-[#8d6e63] mb-1 ml-1 text-sm">제목</label>
          <input 
            v-model="title" 
            type="text" 
            placeholder="제목을 입력하세요" 
            class="w-full bg-white border-4 border-[#d7ccc8] rounded-xl px-4 py-3 font-bold text-[#5d4037] outline-none focus:border-[#7cb342] transition-colors"
            autofocus
          />
        </div>

        <div>
           <label class="block font-bold text-[#8d6e63] mb-1 ml-1 text-sm">내용</label>
          <textarea 
            v-model="content" 
            placeholder="자유롭게 이야기를 나눠보세요! (욕설 및 비방 금지)" 
            class="w-full h-40 bg-white border-4 border-[#d7ccc8] rounded-xl px-4 py-3 font-medium text-[#5d4037] resize-none outline-none focus:border-[#7cb342] transition-colors custom-scrollbar"
          ></textarea>
        </div>
      </div>

      <!-- 하단 버튼 -->
      <div class="flex gap-3 mt-6">
        <button @click="emit('close')" class="flex-1 py-3 rounded-xl font-black text-[#5d4037] bg-[#d7ccc8] hover:bg-[#bcaaa4] transition-colors">취소</button>
        <button @click="handleSubmit" class="flex-1 py-3 rounded-xl font-black text-white bg-[#7cb342] shadow-[0_4px_0_#558b2f] hover:bg-[#689f38] active:translate-y-[4px] active:shadow-none transition-all">등록하기</button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
@keyframes popIn { from { transform: scale(0.9); opacity: 0; } to { transform: scale(1); opacity: 1; } }

.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #d7ccc8; border-radius: 3px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
</style>
