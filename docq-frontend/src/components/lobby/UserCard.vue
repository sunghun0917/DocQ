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
    class="relative rounded-[32px] p-8 flex flex-col items-center justify-center transition-all duration-300 w-full h-full"
    :class="[
      user.nickname 
        ? 'bg-white shadow-xl border-2 border-indigo-50 scale-100 opacity-100' 
        : 'bg-slate-100/50 border-2 border-dashed border-slate-300 scale-95 opacity-70',
      user.isHost ? 'ring-4 ring-amber-100 border-amber-200' : '' /* 방장은 테두리 한 번 더 강조 */
    ]"
  >
    
    <span 
      v-if="user.isHost" 
      class="material-symbols-outlined text-[#f59e0b] text-[44px] absolute top-4 right-4 drop-shadow-md rotate-12 z-20" 
      title="방장"
    >
      crown
    </span>

    <div 
      class="w-36 h-36 rounded-full flex items-center justify-center text-7xl mb-6 shadow-inner relative overflow-hidden transition-transform hover:scale-105"
      :class="user.nickname ? 'bg-indigo-50' : 'bg-slate-200'"
    >
      <span v-if="user.avatar" class="z-10 drop-shadow-lg select-none">{{ user.avatar }}</span>
      
      <div v-if="!user.isHost && user.isReady" class="absolute inset-0 bg-green-400/20 animate-pulse"></div>
    </div>

    <div v-if="user.nickname" class="text-2xl font-black text-slate-800 mb-4 truncate w-full text-center tracking-tight">
      {{ user.nickname }}
      <span v-if="user.isMe" class="text-xs font-bold text-indigo-500 bg-indigo-100 px-2 py-0.5 rounded-full align-middle ml-1">ME</span>
    </div>
    <div v-else class="text-xl font-bold text-slate-400 mb-4">
      Empty Slot
    </div>
    
    <div v-if="user.nickname" class="mt-auto">
      <span 
        class="px-6 py-2.5 rounded-full text-sm font-extrabold shadow-sm transition-colors block min-w-[100px] text-center"
        :class="{
          'bg-[#fffbeb] text-[#b45309] shadow-amber-100 ring-1 ring-amber-200': user.isHost,
          'bg-[#20c997] text-white shadow-green-200': !user.isHost && user.isReady,
          'bg-slate-100 text-slate-400 ring-1 ring-slate-200': !user.isHost && !user.isReady
        }"
      >
        <template v-if="user.isHost">HOST</template>
        <template v-else-if="user.isReady">READY</template>
        <template v-else>WAITING</template>
      </span>
    </div>
  </div>
</template>