# DocQ WebSocket & 게임 로비 구현 요약

tags: #Project #DocQ #WebSocket #Vue #SpringBoot #Refactoring

---

## 📅 작업 개요
- **목표**: Spring Boot 백엔드와 Vue 3 프론트엔드 간의 WebSocket(STOMP) 연동 및 게임 로비 시스템 구현
- **상태**: 구현 완료 및 테스트 성공 ✅
- **핵심 기능**:
    - 방 생성 및 대기실 입장 (REST API)
    - 실시간 유저 목록 동기화 (WebSocket 구독)
    - 게임 시작 시 전체 자동 라우팅 (WebSocket 발행/구독)

---

## 🛠️ 구현 상세

### 1. Backend (Spring Boot)
서버는 **8080 포트**에서 동작하며, STOMP 프로토콜을 사용합니다.

- **설정 파일**
    - `WebConfig.java`: CORS 설정 (프론트엔드 `localhost:5173` 허용)
    - `WebSocketConfig.java`:
        - 엔드포인트: `/ws-docq` (SockJS 지원)
        - 발행(Publish) Prefix: `/app`
        - 구독(Subscribe) Prefix: `/topic`, `/queue`

- **주요 로직**
    - `GameController`: `/app/game/start` 메시지를 받으면 방 상태를 `PLAYING`으로 변경하고 `/topic/game/{roomId}/state`로 브로드캐스트.

### 2. Frontend (Vue 3 + Pinia)
클라이언트는 **5173 포트**에서 동작합니다.

#### 📂 구조 및 역할
| 구분 | 파일명 | 역할 |
|---|---|---|
| **Service** | `api.ts` | REST API 통신 (로그인, 방생성) |
| | `websocket.ts` | SockJS + STOMP 클라이언트 래퍼 |
| **Store** | `websocketStore.ts` | 소켓 연결 관리, 게임 상태(`state`) 구독 및 로직 처리 |
| **View** | `WaitingView.vue` | 대기실 UI. `roomInfo.status`가 `PLAYING`이 되면 `/game`으로 자동 이동 |

#### 🔄 핵심 로직 (게임 시작)
1. **Host**가 '게임 시작' 버튼 클릭.
2. `wsStore.startGame()` 실행 -> `/app/game/start` 메시지 전송.
3. **Server**가 메시지 수신 후 방 상태 변경 -> `/topic/game/{roomId}/state`로 상태 전파.
4. **All Clients** (`WaitingView`)가 상태 변경 감지 -> `watchEffect`에 의해 `/game`으로 `router.push`.

---

## 🧪 테스트 방법

### 서버 실행
> [!NOTE] 터미널을 각각 열어서 실행해야 합니다.

**Backend**
```bash
cd docq_backend
./gradlew bootRun
```

**Frontend**
```bash
cd docq-frontend
npm run dev
```

**AI Server (Python)**
> [!TIP] Window 환경 기준 명령어입니다.
```bash
cd docq-ai

# 1. 초기 세팅 (가상환경 생성 및 패키지 설치)
# Git Bash의 경우: source venv/Scripts/activate && pip install -r requirements.txt
python -m venv venv && venv\Scripts\pip install -r requirements.txt

# 2. 서버 실행
# Git Bash의 경우 가상환경 활성화 후: uvicorn app.main:app --reload
venv\Scripts\uvicorn app.main:app --reload
```

### Docker 실행 (전체 통합)
> [!TIP] 로컬 개발 환경 대신 Docker로 3개 서버(FE, BE, AI)를 동시에 띄울 때 사용합니다. 코드 수정 사항을 반영하려면 `--build` 옵션을 꼭 붙여야 합니다.

```bash
cd deploy/compose
docker-compose up -d --build
```

### 시나리오 테스트 (브라우저 콘솔)
프론트엔드 코드에 `test-api.ts` 유틸리티가 포함되어 있어 콘솔에서 즉시 테스트 가능합니다.

```javascript
// 전체 플로우 자동 실행 (로그인 -> 방생성 -> 참가 -> 준비 -> 시작)
testAPI.testFullFlowWithWebSocket()
```

---

## 📝 남은 과제
- [ ] 게임 화면(`GameView`) 실제 구현
- [ ] 퀴즈 진행 로직 (문제 출제, 정답 제출 등) 구현

---
*Created by Antigravity*
