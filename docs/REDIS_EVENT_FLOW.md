## GameService 변경 사항

1) 스케줄러 제거 및 이벤트 등록 방식으로 전환
- `ScheduledExecutorService` 기반 예약 실행을 제거
- 게임 흐름 제어를 Redis 이벤트 타임라인에 등록하도록 변경

2) 퀴즈 시작/종료 흐름 변경
- `startGame()`에서 인트로 후 퀴즈 시작을 Redis 이벤트로 예약
- `startQuiz()`에서 퀴즈 종료를 Redis 이벤트로 예약

3) 라운드 결과 처리 분리
- `processRoundResult()`에서 이동 적용/다음 턴 준비를 즉시 처리하지 않고 이벤트로 예약
- 이동 적용은 `applyMove()`에서 처리
- 다음 턴 준비는 `prepareNextTurn()`에서 처리

4) 이벤트용 Payload 도입
- 이벤트에 필요한 데이터 구조를 명확히 정의하기 위해 Payload 클래스 추가
- Payload를 통해 Redis 저장/역직렬화 시 타입 안정성을 확보

---

## 추가된 기능(흐름 순서)

1) Redis 이벤트 타임라인(ZSET)
- 이벤트 예약 시 `game:events` ZSET에 score=실행 시각(ms), value=eventId 저장

2) 이벤트 상세 저장(Hash)
- `game:event:{eventId}`에 이벤트 메타/페이로드 저장
- TTL 설정으로 오래된 이벤트 자동 정리

3) 이벤트 폴링 실행기
- `GameEventPoller`가 일정 간격으로 due 이벤트 조회
- `ZRANGEBYSCORE`로 실행 대상 이벤트 확인

4) 분산 락(중복 실행 방지)
- `SETNX` 기반 락 키(`game:event:{eventId}:lock`)로 중복 처리 방지
- 처리 완료 시 락 해제

5) 이벤트 처리 라우터
- `GameEventProcessor`가 타입에 따라 `GameService` 메서드 호출
- START_QUIZ / PROCESS_ROUND_RESULT / APPLY_MOVE / NEXT_TURN / RESUME_AFTER_MINIGAME 처리

6) 이벤트 재시도
- 실행 실패 시 backoff로 재예약(reschedule)

---

## 관련 파일

- Redis 이벤트/락 저장소: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventStore.java`
- 이벤트 폴러: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventPoller.java`
- 이벤트 처리 라우터: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/GameEventProcessor.java`
- 이벤트 타입/페이로드: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/event/*.java`
- 게임 로직 변경: `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java`
- 설정: `docq_backend/src/main/resources/application.properties`
