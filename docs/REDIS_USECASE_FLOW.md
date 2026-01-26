## Redis 도입 이후 유즈케이스 흐름

이 문서는 Redis 타임라인/락 기반 이벤트 처리 도입 이후,
프론트와 백엔드가 어떤 순서로 동작하는지 유즈케이스 관점으로 정리합니다.

---

## 공통 전제
- Redis ZSET(`game:events`)에 이벤트가 예약되고, 폴러가 due 이벤트를 처리한다.
- 백엔드는 최종 상태를 결정하고, 프론트는 표시/애니메이션을 담당한다.
- WebSocket은 상태/이벤트 브로드캐스트 경로이다.
- 이벤트 예약/락/재시도 로직: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventStore.java`
- 폴링 실행기: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventPoller.java`
- 이벤트 라우터: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventProcessor.java`

---

## 유즈케이스 1) 게임 시작

1. 프론트 → 서버
- `WS: /app/game/start` (방장 시작 요청)
- 수신 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/controller/GameController.java`

2. 서버 처리
- 방 상태를 `STARTING`으로 변경
- `START_QUIZ` 이벤트를 Redis에 예약 (인트로 딜레이 후)
- 처리 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`startGame`)

3. 서버 → 프론트
- `WS: /topic/game/{roomId}/state` 브로드캐스트

4. Redis 타임라인
- 폴러가 `START_QUIZ` 이벤트를 due 시점에 가져와 실행
- `startQuiz()` 호출
- 실행 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventProcessor.java`
- 호출 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`startQuiz`)

5. 서버 → 프론트
- `WS: /topic/game/{roomId}/quiz` (퀴즈 출제)
- `WS: /topic/game/{roomId}/state` (퀴즈 상태)

---

## 유즈케이스 2) 퀴즈 진행/채점

1. 프론트 → 서버
- `WS: /app/game/submit` (정답 제출)
- 수신 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/controller/GameController.java`

2. 서버 처리
- 제출 저장 (room.quizAnswers)
- 퀴즈 시작 시점에 예약된 `PROCESS_ROUND_RESULT` 이벤트가 Redis에서 실행됨
- 처리 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`submitAnswer`)

3. Redis 타임라인
- `PROCESS_ROUND_RESULT` 실행 → `processRoundResult()`
- 승자/이동값 계산, 라운드 결과 생성
- `APPLY_MOVE` 이벤트 예약
- `NEXT_TURN` 이벤트 예약
- 실행 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventProcessor.java`
- 처리 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`processRoundResult`)

4. 서버 → 프론트
- `WS: /topic/game/{roomId}/result` (라운드 결과)
- `WS: /topic/game/{roomId}/state` (판정 상태)

---

## 유즈케이스 3) 이동 처리

1. Redis 타임라인
- `APPLY_MOVE` 이벤트 실행 → `applyMove()`
- 위치 업데이트, 잡기 처리, 버프 소모
- 실행 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventProcessor.java`
- 처리 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`applyMove`)

2. 서버 → 프론트
- `WS: /topic/game/{roomId}/move` (이동 정보)
- `WS: /topic/game/{roomId}/state` (MOVE 상태)

3. 프론트 처리
- 이동 애니메이션/룰렛 표시
- UI 연출은 프론트에서 처리
- 처리 코드: `docq-frontend/src/components/game/board3D/GameBoard.vue`

---

## 유즈케이스 4) 다음 턴 준비

1. Redis 타임라인
- `NEXT_TURN` 이벤트 실행 → `prepareNextTurn()`
- 다음 턴 유저 지정, 라운드 증가 조건 확인
- 필요 시 미니게임 시작
- 실행 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventProcessor.java`
- 처리 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`prepareNextTurn`)

2. 서버 → 프론트
- `WS: /topic/game/{roomId}/state` (STARTING)

3. Redis 타임라인
- `START_QUIZ` 이벤트 예약/실행
- 다음 퀴즈 출제

---

## 유즈케이스 5) 미니게임

1. 서버 → 프론트
- `WS: /topic/game/{roomId}/minigame` (START)

2. 프론트 → 서버
- `WS: /app/game/minigame/submit` (결과 제출)
- 수신 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/minigame/controller/MiniGameController.java`

3. 서버 → 프론트
- `WS: /topic/game/{roomId}/minigame` (RESULT)
- 발행 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/minigame/service/MiniGameService.java`

4. 서버 처리
- `resumeAfterMiniGame()`에서 다음 라운드 준비
- `RESUME_AFTER_MINIGAME` 이벤트 예약 후 퀴즈 시작
- 처리 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`resumeAfterMiniGame`)

---

## 유즈케이스 6) 게임 종료

1. 프론트 → 서버
- `WS: /app/game/end` (방장 종료)
- 수신 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/controller/GameController.java`

2. 서버 처리
- 방 삭제 및 종료 브로드캐스트
- 처리 코드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java` (`endGame`, `closeRoom`)

3. 서버 → 프론트
- `WS: /topic/game/{roomId}/closed`

---

## Redis 역할 요약
- 이벤트 예약: `ZADD game:events`
- 이벤트 처리: 폴러의 `ZRANGEBYSCORE` + 락
- 중복 실행 방지: `SETNX` 락 키
- 실패 시 재시도: backoff 후 재예약
