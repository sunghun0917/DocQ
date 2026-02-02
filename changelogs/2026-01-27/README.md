# 2026-01-27 작업 내역 (Update Log)

## 📌 개요
방장과 참가자 간의 **퀴즈 생성 상태 동기화**를 완벽하게 구현하는 데 중점을 두었습니다.
방장이 문서를 고르거나 '다시 만들기'를 할 때, 모든 참가자가 현재 상황을 실시간으로 알 수 있도록 개선했습니다.

## 🛠 Backend (Spring Boot)

### 1. `GameController.java`
- **`resetQuiz` 엔드포인트 추가** (`@MessageMapping("/game/quiz/reset")`)
  - 방장이 '퀴즈 다시 만들기' 버튼을 눌렀을 때 호출되는 WebSocket 핸들러입니다.

### 2. `GameService.java`
- **`generateQuizFromPdf` 수정**
  - 퀴즈 생성 시작 알림(`QUIZ_GENERATING`) 전송 시, 선택한 PDF의 **파일명(`fileName`)**을 함께 보내도록 개선했습니다.
- **`setDefaultQuizzes` 수정**
  - 기본 퀴즈 생성 시작 시에도 알림을 보내도록 추가하고, **퀴즈 타입(`quizType`)** 정보를 포함시켰습니다.
- **`resetQuiz` 메서드 추가**
  - `QUIZ_RESET` 타입의 메시지를 방 전체에 브로드캐스트하여 퀴즈 생성 상태를 초기화합니다.

## 🎨 Frontend (Vue.js + TypeScript)

### 1. 타입 및 스토어 (`docq-api.ts`, `websocketStore.ts`)
- **타입 확장**
  - `QuizMessage` 및 `QuizGeneratedNotice` 인터페이스에 `fileName`, `quizType` 필드를 추가했습니다.
  - WebSocket 메시지 타입에 `'QUIZ_RESET'`을 추가했습니다.
- **스토어 로직 업데이트**
  - WebSocket 메시지 수신 시, `reset`, `generating` 상태를 구분하여 데이터를 저장하도록 수정했습니다.
  - `resetQuiz` 액션을 추가하여 백엔드에 리셋 요청을 보낼 수 있게 했습니다.

### 2. UI/UX (`WaitingView.vue`)
- **상태 동기화 (`watch` 로직)**
  - **`QUIZ_RESET` 수신 시**:
    - **방장**: 생성된 퀴즈 목록을 비우고 업로드 화면(초기 화면)으로 전환합니다.
    - **참가자**: "방장님이 문서를 고르고 있어요"라는 대기 화면으로 전환합니다.
  - **`QUIZ_GENERATING` 수신 시**:
    - 파일명이 있으면 `[운영체제.pdf]`, 퀴즈 타입이 있으면 `[넌센스 퀴즈]`와 같이 구체적인 정보를 표시합니다.
- **사용성 개선**
  - '다시 만들기' 클릭 시 자동으로 파일 탐색기가 열리던 불편함을 제거했습니다.
  - 방장은 리셋 후 바로 초기 업로드 화면을 볼 수 있어 갇히는 현상을 해결했습니다.

## 📝 특이 사항
- 백엔드와 프론트엔드가 주고받는 메시지 프로토콜에 `QUIZ_RESET`이 추가되어 상태 관리가 명확해졌습니다.
