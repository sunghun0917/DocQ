import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // path: 동적 연결
  // @: 별칭, 보통 프로젝트의 src 폴더를 가리킴.
  { path: '/', component: () => import('@/views/MainView.vue') },
  { path: '/waiting/:roomId', component: () => import('@/views/WaitingView.vue') },
  { path: '/game', component: () => import('@/views/GameView.vue') },
]

const router = createRouter({
  // 히스토리 모드 설정
  history: createWebHistory(),
  // 위에서 정의한 routes를 사용해서 실제 라우터 객체 생성
  routes,
})

export default router