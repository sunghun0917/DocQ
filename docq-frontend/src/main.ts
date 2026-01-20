import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// 스타일 적용 순서가 중요
import '@/styles/reset.css'      // 1. 기본 초기화 (가장 먼저)
import '@/styles/variables.css'  // 2. Tailwind가 포함된 설정 (가장 나중에)

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')