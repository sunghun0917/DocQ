# 2026-01-28 작업 내역 (Update Log)

## 📌 개요
게임 보드 렌더링을 **GLB + JSON 기반의 월드 좌표 방식**으로 정리하고, 인트로/이동 카메라 연출과 배경(하늘/구름) 레이어를 구성했습니다.

---

## 🎨 Frontend (Vue + TresJS + Three.js)

### 1) 게임 보드 월드 좌표화 + GLB 보드 로드
- `docq-frontend/src/components/game/board3D/GameBoard.vue`
  - `mapData.json`을 읽어 **타일 번호(1~30) → 월드 좌표(x,y,z)** 로 변환하여 플레이어 배치
  - `game_map.glb`를 로드하여 보드 모델을 렌더링
  - 캔버스 배경이 검정으로 클리어되던 문제를 해결하기 위해 `TresCanvas`에 `:clear-alpha="0"` 적용(배경이 비치도록 투명 클리어)

### 2) 캐릭터 이동(점프) + 모델 선택(4종) + 시작 칸 분산 배치
- `docq-frontend/src/components/game/board3D/GameCharacter.vue`
  - 월드 좌표 props(`position`, `lookTarget`) 기반으로 이동
  - 한 칸 이동 시 **점프 곡선(sin)** 을 적용하여 자연스러운 점프 이동
  - `modelUrl`을 받아 캐릭터 GLB를 로드(모델별 캐시)
- `docq-frontend/src/components/game/board3D/GameBoard.vue`
  - 플레이어별 모델 URL 매핑(최대 4명)
  - 시작 칸(START)에 2~4명이 동시에 있을 때 **원형으로 자동 분산 배치(겹침 방지)**

### 3) 인트로 카메라 연출(시작 칸 확대 → 경로 프리뷰 → 원복) + ACK 텀
- `docq-frontend/src/components/game/board3D/GameBoard.vue`
  - 시작 칸 확대 후, 경로 프리뷰는 **3칸씩 샘플링**하여 따라가도록 연출
  - 인트로 종료 후 게임이 바로 시작되지 않도록 `CAMERA_END` ACK를 **2초 지연**

### 4) 이동 시 카메라 줌인 + 팔로우 + 줌아웃
- `docq-frontend/src/components/game/board3D/GameBoard.vue`
  - `currentMove` 수신 시점에 카메라 스냅샷 저장
  - 이동 시작 시 해당 플레이어로 줌인
  - 한 칸 이동(step)마다 타겟을 갱신해 **이동 캐릭터를 따라가도록(follow)** 처리
  - 이동 종료 시 스냅샷으로 정확히 줌아웃/원복 후 `MOVE_COMPLETE` ACK 전송

### 5) 게임 배경(메인 페이지 느낌) 구성
- `docq-frontend/src/views/GameView.vue`
  - 배경 컴포넌트로 `CloudBackground` 적용
- `docq-frontend/src/components/ui/CloudBackground.vue`
  - 파란 하늘 그라데이션 배경
- `docq-frontend/src/components/ui/CloudOverlay.vue`
  - 보드 위를 가로지르는 구름 오버레이(현재 14개 구성)

---

## 🛠 Backend (Spring Boot)

### 1) 시작 포지션을 1로 통일
- `docq_backend/src/main/java/com/ssafy/docq_backend/domain/game/service/GameService.java`
  - `START_POSITION = 1` 도입
  - 플레이어 초기 위치를 0 → 1로 변경
  - 이동 하한/잡기(CATCH) 처리에서 0 대신 START_POSITION 기준으로 보정
  - 특수 타일(mapEvents) 배치 범위를 START/TARGET 제외하도록 정리

---

## 🎨 UI/레이어 관련 참고
- 보드 캔버스가 검정 배경으로 덮이던 현상은 `TresCanvas`의 `clearAlpha` 기본값(불투명 클리어) 영향이었고, `:clear-alpha="0"`로 해결했습니다.

---

## ✅ 검증
- Frontend: `docq-frontend/`에서 `npm run type-check` 통과
- Backend: `docq_backend/`에서 `./gradlew test --no-daemon` 통과
