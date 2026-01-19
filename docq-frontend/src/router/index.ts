import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // 메인 페이지
  { path: '/', component: () => import('@/views/MainView.vue') },
  // 대기실 페이지 (방 ID를 동적으로 받음)
  { path: '/waiting/:roomId', component: () => import('@/views/WaitingView.vue') },
  // 게임 본 화면 (결과 모달은 이 안에서 호출됨)
  { path: '/game', component: () => import('@/views/GameView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router