import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // 메인 페이지
  { path: '/', component: () => import('@/views/MainView.vue') },
  // 대기실 페이지 (방 ID를 동적으로 받음)
  { path: '/waiting/:roomId', component: () => import('@/views/WaitingView.vue') },
  // 게임 본 화면 (결과 모달은 이 안에서 호출됨)
  { path: '/game', component: () => import('@/views/GameView.vue') },

  // 테스트용 미니게임 화면
  { path: '/test', component: () => import('@/views/TestMinigameView.vue') },
]

const router = createRouter({
  // 히스토리 모드 설정
  history: createWebHistory(),
  // 위에서 정의한 routes를 사용해서 실제 라우터 객체 생성
  routes,
})

export default router