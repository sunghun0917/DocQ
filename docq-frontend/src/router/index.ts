import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // path: 동적 연결
  // @: 별칭, 보통 프로젝트의 src 폴더를 가리킴.
  { path: '/', component: () => import('@/views/MainView.vue') },
  { path: '/waiting/:roomId', component: () => import('@/views/WaitingView.vue') },
  { path: '/game', component: () => import('@/views/GameView.vue') },
  // test
  { path: '/test', name: 'test', component: () => import('@/views/GameTestView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router