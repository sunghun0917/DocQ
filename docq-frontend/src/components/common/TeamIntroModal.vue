<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import AlertModal from '@/components/common/AlertModal.vue';
const emit = defineEmits(['close']);
const showAlert = ref(false);
const alertMessage = ref('');
const alertType = ref<'success' | 'error'>('success');
// 팀원 데이터 (기존 데이터 유지)
const teamMembers = [
  {
    name: "김현수",
    role: "Backend Architect",
    email: "wshyhs@gmail.com",
    photo: "/teamimages/hyunsu.jpg",
    keywords: ["Real-time Sync", "Event-Driven", "Redis Pub/Sub", "System Design"],
    icon: "psychology",
    color: "#ef5350"
  },
  {
    name: "안성훈",
    role: "DevOps/Cloud     Engineer",
    email: "a771001o5o@gmail.com",
    photo: "/teamimages/sunghoon.jpg",
    keywords: ["CI/CD Pipeline", "Infrastructure as Code", "Dockerized Architecture", "Zero-downtime"],
    icon: "dns",
    color: "#7e57c2"
  },
  {
    name: "정인호",
    role: "Frontend/3D Core",
    email: "jih19984@gmail.com",
    photo: "/teamimages/inho.jpeg",
    keywords: ["Three.js Rendering", "State Management", "WebGL Interaction", "Performance Opt"],
    icon: "view_in_ar",
    color: "#26a69a"
  },
  {
    name: "오창민",
    role: "UI/UX Engineer",
    email: "dhckdals1104@gmail.com",
    photo: "/teamimages/changmin.jpeg",
    keywords: ["UI Engineering", "3D Scene UI", "Responsive Design", "Interactive Motion"],
    icon: "palette",
    color: "#42a5f5"
  },
  {
    name: "홍현기",
    role: "AI/Security Engineer",
    email: "ghdgusrl3466@gmail.com",
    photo: "/teamimages/hyungi.jpg",
    keywords: ["LLM Integration", "RAG Pipeline", "OAuth 2.0 / JWT", "API Security"],
    icon: "security",
    color: "#ffca28"
  },
  {
    name: "임보경",
    role: "Frontend Developer",
    email: "qhrudlim@gmail.com",
    photo: "/teamimages/bokyoung.jpg",
    keywords: ["UI Component", "Game View", "Assets", "Client Support"],
    icon: "extension",
    color: "#66bb6a"
  }
];

const copyEmail = (email: string) => {
  if (!email || email === '[EMAIL_ADDRESS]') return;
  
  navigator.clipboard.writeText(email).then(() => {
    alertMessage.value = `${email}이(가) 복사되었습니다!`;
    alertType.value = 'success';
    showAlert.value = true;
  }).catch(() => {
    alertMessage.value = '복사에 실패했습니다.';
    alertType.value = 'error';
    showAlert.value = true;
  });
};

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    emit('close');
  }
};

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <div class="fixed inset-0 z-[100] flex items-center justify-center font-jua">
    <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="$emit('close')"></div>

    <div class="relative w-[95vw] max-w-[1600px] h-[540px] bg-[#fff8e1] border-[6px] border-[#5d4037] rounded-[32px] shadow-2xl flex flex-col overflow-hidden animate-pop-in">
      
      <header class="!pt-8 !pb-4 text-center shrink-0">
        <h2 class="text-4xl text-[#5d4037] font-black drop-shadow-sm">TEAM DOCQ</h2>
      </header>

      <div class="flex-1 !px-8 !pb-8 !pt-2 overflow-x-auto custom-scrollbar">
        <div class="flex h-full !gap-4 min-w-[1000px]"> 
          <div v-for="(member, index) in teamMembers" :key="index"
               @click="copyEmail(member.email)" 
               class="flex-1 min-w-0 bg-white border-4 border-[#d7ccc8] rounded-2xl !p-4 flex flex-col items-center shadow-md hover:shadow-xl hover:-translate-y-2 transition-all duration-300 relative overflow-hidden group cursor-pointer">
            
            <div class="absolute top-0 left-0 right-0 h-3" :style="{ backgroundColor: member.color }"></div>
            
            <div class="w-28 h-28 bg-[#f5f5f5] border-4 border-[#d7ccc8] rounded-2xl mb-3 flex items-center justify-center overflow-hidden shrink-0">
               <img v-if="member.photo" :src="member.photo" class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" />
               <span v-else class="material-symbols-outlined text-5xl text-[#d7ccc8]">person</span>
            </div>

            <span class="!px-3 !py-1 rounded-full text-white font-bold text-xs mb-4" :style="{ backgroundColor: member.color }">
              {{ member.role }}
            </span>
            <h3 class="text-2xl font-black text-[#5d4037] !mb-1">{{ member.name }}</h3>
            
            <div class="w-full flex justify-center !py-1 !mb-4">
               <div class="w-full flex items-center justify-center bg-[#f5f5f5] border border-[#d7ccc8] !py-1 rounded-lg">
                  <span class="text-xs text-[#5d4037] font-bold truncate">{{ member.email }}</span>
               </div>
            </div>

            <div class="flex flex-col !gap-1.5 w-full mt-auto">
              <span v-for="keyword in member.keywords" :key="keyword"
                    class="w-full !py-1 bg-[#eceff1] text-[#546e7a] border border-[#cfd8dc] rounded !text-[13px] font-bold text-center">
                #{{ keyword }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <AlertModal 
      :isOpen="showAlert"
      :message="alertMessage" 
      :type="alertType"
      @close="showAlert = false" 
    />
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Jua&display=swap');

.font-jua { font-family: 'Jua', sans-serif; }

.cartoon-btn {
  height: 50px;
  border-radius: 12px;
  font-weight: 900;
  font-size: 1.2rem;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.1s;
  background: #8d6e63;
  box-shadow: 0 4px 0 #5d4037;
}
.cartoon-btn:active { transform: translateY(4px); box-shadow: none !important; }
.cartoon-btn:hover { background: #795548; }

.custom-scrollbar::-webkit-scrollbar { height: 8px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #d7ccc8; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #bcaaa4; }

.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
@keyframes popIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
</style>