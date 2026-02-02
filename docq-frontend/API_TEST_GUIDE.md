# API 테스트 가이드

API가 정상적으로 작동하는지 테스트하는 방법입니다.

## 🚀 사전 준비

### 1. 백엔드 서버 실행
```bash
# 백엔드 디렉토리로 이동
cd ../docq_backend

# Spring Boot 서버 실행
./gradlew bootRun
# 또는
gradle bootRun
```

서버가 `http://localhost:8080`에서 실행되어야 합니다.

### 2. 프론트엔드 개발 서버 실행
```bash
# 프론트엔드 디렉토리
cd docq-frontend

# 개발 서버 실행
npm run dev
```

---

## 🧪 테스트 방법

### 방법 1: 브라우저 콘솔에서 테스트 (가장 간단!)

1. **브라우저에서 `http://localhost:5173` 열기**

2. **F12 키를 눌러 개발자 도구 열기**

3. **Console 탭으로 이동**

4. **다음 명령어 실행:**

#### 전체 플로우 테스트 (추천!)
```javascript
// main.ts에서 testAPI를 import하고 window에 추가해야 함
testAPI.testFullFlow()
```

#### 개별 API 테스트
```javascript
// 1. 사용자 로그인
testAPI.testUserLogin()

// 2. 방 생성 (userId 필요)
testAPI.testCreateRoom('발급받은-userId')

// 3. 방 조회
testAPI.testGetRoomByInviteCode('A1B2')

// 4. 방 참가
testAPI.testJoinPublicRoom('room-uuid')
```

---

### 방법 2: Postman 또는 Thunder Client 사용

#### 1. 사용자 로그인
```
POST http://localhost:8080/users/login
Content-Type: application/json

{
  "nickname": "테스트유저"
}
```

**예상 응답:**
```json
{
  "userId": "uuid-v4-string",
  "nickname": "테스트유저"
}
```

#### 2. 방 생성
```
POST http://localhost:8080/rooms
Content-Type: application/json

{
  "userId": "위에서-받은-userId",
  "capacity": 4,
  "title": "테스트 방",
  "isPrivate": false
}
```

**예상 응답:**
```json
{
  "roomId": "room-uuid",
  "inviteCode": "A1B2",
  "hostId": "userId",
  "isPrivate": false
}
```

#### 3. 방 조회
```
GET http://localhost:8080/rooms?inviteCode=A1B2
```

#### 4. 방 참가 검증
```
POST http://localhost:8080/rooms/join
Content-Type: application/json

{
  "roomId": "room-uuid"
}
```

---

### 방법 3: 테스트 페이지 만들기

간단한 테스트 페이지를 만들어서 버튼으로 테스트할 수 있습니다.

**`src/views/TestView.vue` 생성:**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { testAPI } from '@/test-api'

const result = ref<any>(null)
const loading = ref(false)

const runTest = async (testFn: () => Promise<any>) => {
    loading.value = true
    result.value = null
    try {
        result.value = await testFn()
    } catch (error) {
        result.value = { error: error }
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <div class="p-8">
        <h1 class="text-3xl font-bold mb-8">API 테스트</h1>
        
        <div class="space-y-4">
            <button 
                @click="runTest(() => testAPI.testFullFlow())"
                class="px-4 py-2 bg-blue-500 text-white rounded"
            >
                전체 플로우 테스트
            </button>
            
            <button 
                @click="runTest(() => testAPI.testUserLogin())"
                class="px-4 py-2 bg-green-500 text-white rounded"
            >
                사용자 로그인 테스트
            </button>
        </div>

        <div v-if="loading" class="mt-8">
            로딩 중...
        </div>

        <div v-if="result" class="mt-8 p-4 bg-gray-100 rounded">
            <pre>{{ JSON.stringify(result, null, 2) }}</pre>
        </div>
    </div>
</template>
```

**라우터에 추가 (`src/router/index.ts`):**
```typescript
{
    path: '/test',
    name: 'test',
    component: () => import('@/views/TestView.vue')
}
```

그리고 `http://localhost:5173/test`로 접속!

---

## ✅ 테스트 체크리스트

- [ ] 백엔드 서버 실행 확인 (`http://localhost:8080`)
- [ ] 프론트엔드 서버 실행 확인 (`http://localhost:5173`)
- [ ] 사용자 로그인 API 테스트
- [ ] 방 생성 API 테스트
- [ ] 방 조회 API 테스트
- [ ] 방 참가 검증 API 테스트
- [ ] 에러 케이스 테스트 (잘못된 입력 등)

---

## 🐛 문제 해결

### CORS 에러가 발생하면?
백엔드에서 CORS 설정이 필요합니다.

### 연결이 안 되면?
1. 백엔드 서버가 실행 중인지 확인
2. `.env` 파일의 `VITE_API_BASE_URL` 확인
3. 네트워크 탭에서 실제 요청 URL 확인

### 404 에러가 나면?
API 경로가 백엔드와 일치하는지 확인
