<script setup lang="ts">
defineProps<{
  user: {
    nickname: string;
    avatar: string;
    isReady: boolean;
    isHost?: boolean;
    isMe?: boolean;
  }
}>();
</script>

<template>
  <div 
    class="relative rounded-[32px] p-6 flex flex-col items-center justify-center transition-all duration-300 w-full h-full border-4"
    :class="[
      user.nickname 
        ? 'bg-white border-wood-border shadow-[0_6px_0_0_rgba(93,64,55,1)] animate-fade-in' 
        : 'bg-white/10 border-dashed border-wood-light/50 opacity-40 shadow-none'
    ]"
  >
    
    <!-- 방장 크라운 -->
    <span 
      v-if="user.isHost" 
      class="material-symbols-outlined text-amber-500 text-4xl absolute -top-2 -right-2 drop-shadow-[0_3px_0_rgba(217,119,6,1)] rotate-12 z-30 font-black" 
    >
      crown
    </span>

    <!-- 아바타 원형 -->
    <div 
      class="w-24 h-24 rounded-full flex items-center justify-center text-6xl mb-4 shadow-inner relative transition-transform hover:scale-105 border-4"
      :class="user.nickname ? 'bg-[#fff8e1] border-wood-border' : 'bg-transparent border-wood-light/20'"
    >
      <span v-if="user.avatar" class="z-10 drop-shadow-md select-none transform transition-all group-hover:scale-110">{{ user.avatar }}</span>
      <span v-else-if="user.nickname" class="z-10 drop-shadow-md select-none opacity-20">❓</span>

      <div v-if="!user.isHost && user.isReady" class="absolute inset-0 bg-leaf-green/20 animate-pulse"></div>
    </div>

    <!-- 닉네임 영역 -->
    <div v-if="user.nickname" class="text-2xl font-black text-wood-dark mb-3 truncate w-full text-center tracking-tight leading-none">
      {{ user.nickname }}
      <span v-if="user.isMe" class="text-[10px] font-black bg-leaf-green text-white px-2 py-0.5 rounded-full align-middle ml-1 shadow-sm">ME</span>
    </div>
    <div v-else class="text-xl font-black text-wood-dark opacity-10 mb-2 uppercase tracking-widest">
      Empty
    </div>
    
    <!-- 상태 배지 -->
    <div v-if="user.nickname" class="mt-auto w-full px-2">
      <div 
        class="py-1.5 rounded-xl text-base font-black shadow-[0_3px_0_0_rgba(0,0,0,0.1)] transition-all block text-center uppercase tracking-wider border-2"
        :class="{
          'bg-amber-50 text-amber-700 border-amber-200 shadow-amber-100': user.isHost,
          'bg-leaf-green text-white border-leaf-dark shadow-leaf-dark/20': !user.isHost && user.isReady,
          'bg-gray-50 text-gray-400 border-gray-100 shadow-none opacity-60': !user.isHost && !user.isReady
        }"
      >
        <template v-if="user.isHost">Host</template>
        <template v-else-if="user.isReady">Ready</template>
        <template v-else>Waiting</template>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}

.shadow-inner {
  box-shadow: inset 0 4px 8px rgba(0,0,0,0.05);
}

* {
  font-family: 'Jua', sans-serif;
}
</style>