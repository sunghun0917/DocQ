import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// 스타일 적용 순서가 중요
import '@/styles/reset.css'      // 1. 기본 초기화 (가장 먼저)
import '@/styles/variables.css'  // 2. Tailwind가 포함된 설정 (가장 나중에)

import Tres from '@tresjs/core'

const app = createApp(App)

app.use(Tres)
app.use(createPinia())
app.use(router)

app.mount('#app')

// 개발 환경에서만 API 테스트 함수를 전역으로 노출
if (import.meta.env.DEV) {
    import('./test-api').then((module) => {
        (window as any).testAPI = module.testAPI
        console.log('✅ API 테스트 함수가 준비되었습니다!')
        console.log('💡 사용법: testAPI.testFullFlow() 또는 testAPI.testUserLogin()')
    })
}