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
    class="relative rounded-3xl p-6 flex flex-col items-center justify-center transition-all duration-300"
    :class="user.nickname 
      ? 'bg-white shadow-xl border border-indigo-50 scale-100 opacity-100' 
      : 'bg-slate-100/50 border-2 border-dashed border-slate-300 scale-95 opacity-70'"
  >
    <div v-if="user.isHost" class="absolute top-4 right-4 bg-yellow-400 text-white w-8 h-8 rounded-full flex items-center justify-center shadow-md z-10 text-sm" title="방장">
      👑
    </div>

    <div 
      class="w-32 h-32 rounded-full flex items-center justify-center text-6xl mb-4 shadow-inner relative overflow-hidden"
      :class="user.nickname ? 'bg-indigo-50' : 'bg-slate-200'"
    >
      <span v-if="user.avatar" class="z-10 drop-shadow-md">{{ user.avatar }}</span>
      <div v-if="!user.isHost && user.isReady" class="absolute inset-0 bg-green-400/20 animate-pulse"></div>
    </div>

    <div v-if="user.nickname" class="text-xl font-extrabold text-slate-800 mb-2">
      {{ user.nickname }}
    </div>
    <div v-else class="text-lg font-bold text-slate-400">
      Empty Slot
    </div>
    
    <div v-if="user.nickname" class="mt-auto">
      <span 
        class="px-4 py-1.5 rounded-full text-sm font-bold shadow-sm transition-colors block min-w-[80px] text-center"
        :class="{
          'bg-yellow-100 text-yellow-700 shadow-yellow-100': user.isHost,
          'bg-green-500 text-white shadow-green-200': !user.isHost && user.isReady,
          'bg-slate-200 text-slate-500': !user.isHost && !user.isReady
        }"
      >
        <template v-if="user.isHost">👑 방장</template>
        <template v-else-if="user.isReady">READY!</template>
        <template v-else>WAITING</template>
      </span>
    </div>
  </div>
</template>