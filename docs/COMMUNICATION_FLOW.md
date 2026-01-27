## 프론트-백엔드 통신 정리

이 문서는 현재 프로젝트의 프론트엔드와 백엔드 간 모든 통신(HTTP/WS)을 한 곳에 정리한 문서입니다.

---

## 1) WebSocket 설정

### 백엔드
- 설정 파일: `docq_backend/src/main/java/com/ssafy/docq_backend/global/config/WebSocketConfig.java`
- 연결 엔드포인트: `/ws-docq`
- 발행 프리픽스: `/app`
- 구독 프리픽스: `/topic`, `/queue`

### 프론트엔드
- 연결 파일: `docq-frontend/src/services/websocket.ts`
- 연결 URL: `VITE_WS_URL` 또는 `/ws-docq`
- 프로토콜: STOMP over SockJS

---

## 2) HTTP REST API

### 유저
- 백엔드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/user/controller/UserController.java`
- 프론트: `docq-frontend/src/services/api.ts`

| 메서드 | 엔드포인트 | 설명 | 요청/응답 |
|---|---|---|---|
| POST | `/users/login` | 닉네임 로그인 | `{nickname}` → `{userId, nickname}` |

### 방
- 백엔드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/room/controller/RoomController.java`
- 프론트: `docq-frontend/src/services/api.ts`

| 메서드 | 엔드포인트 | 설명 | 요청/응답 |
|---|---|---|---|
| POST | `/rooms` | 방 생성 | `{userId, capacity, title, isPrivate?, password?}` → `{roomId, inviteCode, hostId, isPrivate}` |
| GET | `/rooms/{roomId}` | 방 조회 | → `{roomId, title, isPrivate, status, currentCount, capacity}` |
| GET | `/rooms?inviteCode={code}` | 초대코드 조회 | → `{roomId, title, ...}` |
| POST | `/rooms/join` | 방 입장 검증 | `{roomId, password?}` → `{roomId, roomCode, status}` |

### 게임/퀴즈
- 백엔드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/controller/GameController.java`
- 프론트: `docq-frontend/src/services/api.ts`

| 메서드 | 엔드포인트 | 설명 | 요청/응답 |
|---|---|---|---|
| POST | `/game/{roomId}/quiz/generate` | PDF 업로드 퀴즈 생성 | `multipart/form-data (file)` → `{message}` |
| GET | `/game/{roomId}/quiz` | 퀴즈 목록 조회 | → `QuizResponse[]` |
| POST | `/game/{roomId}/quiz/default` | 기본 퀴즈 설정 | `?type=nonsense|common|mixed` → `{count}` |

---

## 3) WebSocket 메시지 매핑 (서버 수신)

### GameController
- 파일: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/controller/GameController.java`

| @MessageMapping | 설명 | Payload |
|---|---|---|
| `/app/game/join` | 게임 참가 | `{roomId, userId, nickname}` |
| `/app/game/leave` | 게임 나가기 | `{roomId, userId}` |
| `/app/game/ready` | 준비 상태 | `{roomId, userId, isReady}` |
| `/app/game/start` | 게임 시작 | `{roomId, userId}` |
| `/app/game/end` | 게임 종료 | `{roomId, userId}` |
| `/app/game/restart` | 게임 재시작 | `{roomId, userId}` |
| `/app/game/submit` | 정답 제출 | `{roomId, userId, quizId, answer}` |
| `/app/game/avatar` | 아바타 선택 | `{roomId, userId, avatarId}` |
| `/app/game/ack` | 애니메이션 ACK | `{roomId, userId, ackType, quizId?, round?}` |

#### ACK 상세 규칙
- 목적: 프론트 애니메이션 종료 시점을 서버가 감지해 다음 단계 이벤트를 즉시 진행.
- ACK 타입 및 기준 키:
  - `RESULT_DISPLAYED`: 결과 오버레이 종료 후, `quizId` 기반 키 사용.
  - `MOVE_COMPLETE`: 이동 애니메이션 종료 후, `quizId` 기반 키 사용.
  - `MINIGAME_COMPLETE`: 미니게임 결과 화면 종료 후, `round` 기반 키 사용.
  - `BUFF_ROULETTE_COMPLETE`: 버프 룰렛 종료 후, 미니게임 흐름과 통합 처리.
  - `ROULETTE_COMPLETE`: 현재는 미사용(향후 분리 연출용 예약).
- 상태 보호:
  - 미니게임 ACK는 방 상태가 `MINIGAME`일 때만 유효.
  - 잘못된 상태의 ACK는 무시됨.

### MiniGameController
- 파일: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/minigame/controller/MiniGameController.java`

| @MessageMapping | 설명 | Payload |
|---|---|---|
| `/app/game/minigame/submit` | 미니게임 결과 제출 | `{roomId, userId, gameType, resultValue}` |

---

## 4) WebSocket 메시지 발행 (서버 송신)

### GameService
- 파일: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java`

| Topic | 설명 | Payload |
|---|---|---|
| `/topic/game/{roomId}/state` | 게임 상태 | `GameStateResponse` |
| `/topic/game/{roomId}/quiz` | 퀴즈 출제/생성 알림 | `QuizMessage` 또는 `{type:"QUIZ_GENERATED"}` |
| `/topic/game/{roomId}/result` | 라운드 결과 | `RoundResult` |
| `/topic/game/{roomId}/move` | 이동 정보 | `MoveInfo` |
| `/topic/game/{roomId}/avatar` | 아바타 업데이트 | `AvatarUpdateResponse` |
| `/topic/game/{roomId}/closed` | 방 종료 | `RoomClosedResponse` |
| `/topic/game/{roomId}/finish` | 게임 종료 | `FinishResponse` |
| `/queue/errors/{userId}` | 개인 에러 | `ErrorResponse` |

### MiniGameService
- 파일: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/minigame/service/MiniGameService.java`

| Topic | 설명 | Payload |
|---|---|---|
| `/topic/game/{roomId}/minigame` | 미니게임 이벤트 | `StartResponse` 또는 `ResultResponse` |

---

## 5) 프론트 WebSocket 구독/발행

### 구독 (Subscribe)
- 파일: `docq-frontend/src/stores/websocketStore.ts`

| 구독 경로 | 처리 로직 |
|---|---|
| `/topic/game/{roomId}/state` | `gameStore.setGameState()`, `roomStore.setUsers()` |
| `/topic/game/{roomId}/avatar` | `roomStore.updateUser()`, `userStore.updateAvatar()` |
| `/topic/game/{roomId}/move` | `gameStore.handleMove()` |
| `/topic/game/{roomId}/quiz` | `gameStore.onQuizReceived()` 또는 생성 알림 처리 |
| `/topic/game/{roomId}/result` | `gameStore.onRoundResultReceived()` |
| `/topic/game/{roomId}/closed` | `cleanup()`, `router.push('/')` |
| `/topic/game/{roomId}/minigame` | `gameStore.queueMiniGame()` / `gameStore.setMiniGameResult()` |
| `/queue/errors/{userId}` | 에러 표시 |

### 발행 (Publish)
- 파일: `docq-frontend/src/stores/websocketStore.ts`

| 발행 경로 | 메서드 | Payload |
|---|---|---|
| `/app/game/join` | `joinGame()` | `{roomId, userId, nickname}` |
| `/app/game/leave` | `leaveGame()` | `{roomId, userId}` |
| `/app/game/ready` | `setReady()` | `{roomId, userId, isReady}` |
| `/app/game/start` | `startGame()` | `{roomId, userId}` |
| `/app/game/end` | `endGame()` | `{roomId, userId}` |
| `/app/game/restart` | `restartGame()` | `{roomId, userId}` |
| `/app/game/submit` | `submitAnswer()` | `{roomId, userId, quizId, answer}` |
| `/app/game/avatar` | `setAvatar()` | `{roomId, userId, avatarId}` |
| `/app/game/minigame/submit` | `submitMiniGameResult()` | `{roomId, userId, gameType, resultValue}` |
| `/app/game/ack` | `sendAck()` | `{roomId, userId, ackType, quizId?, round?}` |

---

## 6) 대표 흐름

### 방 입장
1. `POST /users/login` → `userId` 발급  
2. `GET /rooms/{roomId}` → 방 정보 조회  
3. WebSocket 연결 `/ws-docq`  
4. `/app/game/join` 발행  
5. `/topic/game/{roomId}/state` 수신

### 게임 시작
1. `/app/game/ready` 발행 (모든 유저)
2. `/topic/game/{roomId}/state` 수신
3. 방장 `/app/game/start` 발행
4. `/topic/game/{roomId}/state` (STARTING) 수신
5. `/topic/game/{roomId}/quiz` 수신

### 퀴즈 진행
1. `/topic/game/{roomId}/quiz` 수신  
2. `/app/game/submit` 발행  
3. `/topic/game/{roomId}/result` 수신  
4. `/topic/game/{roomId}/move` 수신  
5. `/topic/game/{roomId}/state` 수신

### 퀴즈 타이머 동작
1. 서버가 `QuizMessage`에 `quizStartTime`과 `timeLimit(ms)`를 포함해서 전송  
2. 프론트는 수신 시점의 현재 시간과 `quizStartTime` 차이를 반영해 남은 시간을 계산  
3. 네트워크 지연이 있는 경우 5초가 4초로 시작하는 등 표시값이 변할 수 있음  
4. 타이머는 `QUIZ_TIME` 상태가 아닐 때 정지됨  

### 미니게임
1. `/topic/game/{roomId}/minigame` START 수신  
2. `/app/game/minigame/submit` 발행  
3. `/topic/game/{roomId}/minigame` RESULT 수신

