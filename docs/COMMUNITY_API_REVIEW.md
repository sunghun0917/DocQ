# 게시판/퀴즈/댓글 API 전체 점검 보고서

## 📋 구현된 기능 목록

### 1. 게시판 (Board) API
- ✅ `POST /boards` - 게시글 작성 (회원 전용)
- ✅ `GET /boards` - 게시글 목록 조회 (페이지네이션, 게스트/회원)
- ✅ `GET /boards/{boardId}` - 게시글 상세 조회 (게스트/회원)
- ✅ `DELETE /boards/{boardId}` - 게시글 삭제 (작성자 전용)
- ✅ `POST /boards/{boardId}/likes` - 좋아요 (회원 전용)
- ✅ `DELETE /boards/{boardId}/likes` - 좋아요 취소 (회원 전용)

### 2. 퀴즈 (Quiz) API
- ✅ `POST /quizzes/create` - 퀴즈 생성 (회원 전용)
- ✅ `GET /quizzes/my` - 내가 만든 퀴즈 목록 조회 (회원 전용)
- ✅ `POST /quizzes/{quizId}/store` - 퀴즈 저장 (회원 전용)
- ✅ `DELETE /quizzes/{quizId}/store` - 퀴즈 저장 취소 (회원 전용)

### 3. 댓글 (Comment) API
- ✅ `GET /boards/{boardId}/comments` - 댓글 목록 조회 (페이지네이션, 게스트/회원)
- ✅ `POST /boards/{boardId}/comments` - 댓글 작성 (회원 전용)
- ✅ `DELETE /boards/{boardId}/comments/{commentId}` - 댓글 삭제 (작성자 전용)

### 4. 게임 연동
- ✅ `POST /game/{roomId}/quiz/generate` - PDF 파싱 후 자동 퀴즈 저장 (회원인 경우)

---

## 🔐 현재 인증 방식: X-User-Id 헤더 (목업)

### 백엔드 - 헤더 사용 위치

#### BoardController
```java
@RequestHeader(value = "X-User-Id", required = false) Long userId
```
- `GET /boards` - 목록 조회 (선택적, likedByMe/storedByMe 계산용)
- `GET /boards/{boardId}` - 상세 조회 (선택적, likedByMe/storedByMe 계산용)
- `POST /boards` - 게시글 작성 (필수)
- `DELETE /boards/{boardId}` - 게시글 삭제 (필수)
- `POST /boards/{boardId}/likes` - 좋아요 (필수)
- `DELETE /boards/{boardId}/likes` - 좋아요 취소 (필수)

#### QuizController
```java
@RequestHeader(value = "X-User-Id", required = false) Long userId
```
- `POST /quizzes/create` - 퀴즈 생성 (필수)
- `GET /quizzes/my` - 내 퀴즈 목록 (필수)
- `POST /quizzes/{quizId}/store` - 퀴즈 저장 (필수)
- `DELETE /quizzes/{quizId}/store` - 퀴즈 저장 취소 (필수)

#### CommentController
```java
@RequestHeader(value = "X-User-Id", required = false) Long userId
```
- `POST /boards/{boardId}/comments` - 댓글 작성 (필수)
- `DELETE /boards/{boardId}/comments/{commentId}` - 댓글 삭제 (필수)
- `GET /boards/{boardId}/comments` - 댓글 목록 (헤더 없음, 게스트/회원 모두 가능)

#### GameController
```java
@RequestHeader(value = "X-User-Id", required = false) Long userId
```
- `POST /game/{roomId}/quiz/generate` - PDF 파싱 (선택적, 회원인 경우 자동 저장)

---

### 프론트엔드 - 헤더 전송 위치

#### api.ts - buildUserIdHeader 함수 사용
```typescript
const buildUserIdHeader = (userId?: string) => {
    if (!userId) return {}
    const n = Number(userId)
    if (Number.isNaN(n)) return {}
    return { 'X-User-Id': n }
}
```

**사용 위치:**
1. `getBoards(page, size, userId?)` - 선택적
2. `getBoardDetail(boardId, userId?)` - 선택적
3. `createBoard(data, userId)` - 필수
4. `likeBoard(boardId, userId)` - 필수
5. `unlikeBoard(boardId, userId)` - 필수
6. `storeQuiz(quizId, userId)` - 필수
7. `unstoreQuiz(quizId, userId)` - 필수
8. `getMyQuizzes(userId)` - 필수
9. `createComment(boardId, data, userId)` - 필수
10. `deleteComment(boardId, commentId, userId)` - 필수
11. `generateQuiz(roomId, file, userId?)` - 선택적 (WaitingView.vue)

---

## 🔄 Security 설정 후 변경 필요 사항

### 백엔드 변경 계획

**현재:**
```java
@RequestHeader(value = "X-User-Id", required = false) Long userId
```

**변경 후 (예상):**
```java
@AuthenticationPrincipal UserDetails userDetails
// 또는
@AuthenticationPrincipal Long userId  // Custom UserDetailsService에서 userId 반환 시
```

**변경 대상 파일:**
1. `BoardController.java` - 모든 메서드
2. `QuizController.java` - 모든 메서드
3. `CommentController.java` - create, delete 메서드
4. `GameController.java` - generateQuiz 메서드

**Service 레이어는 변경 불필요:**
- Service 메서드는 `Long userId` 파라미터를 받으므로, Controller에서만 principal에서 추출하여 전달하면 됨.

---

### 프론트엔드 변경 계획

**현재:**
- 모든 API 호출에서 `userId`를 헤더로 전송

**변경 후:**
- Security 설정 후에는 **쿠키/세션 기반 인증**이므로 헤더 전송 불필요
- `buildUserIdHeader` 함수는 제거하거나 사용하지 않음
- API 함수 시그니처에서 `userId` 파라미터 제거 가능

**변경 대상:**
- `api.ts` - 모든 API 함수에서 `userId` 파라미터 및 헤더 제거
- `CommunitySystem.vue` - API 호출 시 `userId` 전달 제거
- `CommunityDetailModal.vue` - API 호출 시 `userId` 전달 제거
- `CommunityWriteModal.vue` - API 호출 시 `userId` 전달 제거
- `WaitingView.vue` - `generateQuiz` 호출 시 `userId` 전달 제거

---

## ✅ 기능 검증 체크리스트

### 게시판 기능
- [x] 게시글 작성 (퀴즈 선택 드롭다운, 중복 체크)
- [x] 게시글 목록 조회 (페이지네이션, 좋아요/저장 상태)
- [x] 게시글 상세 조회 (퀴즈 문제 목록, 좋아요/저장 상태)
- [x] 게시글 삭제 (작성자만)
- [x] 좋아요/좋아요 취소 (토글, 목록 동기화)
- [x] 질문 개수 표시

### 퀴즈 기능
- [x] 퀴즈 생성 (PDF 파싱 후 자동 저장, 파일명을 타이틀로 사용)
- [x] 내 퀴즈 목록 조회 (타이틀 표시)
- [x] 퀴즈 저장/저장 취소 (토글, 목록 동기화)
- [x] 퀴즈 타이틀 표시 (글쓰기 모달)

### 댓글 기능
- [x] 댓글 목록 조회 (페이지네이션)
- [x] 댓글 작성 (회원 전용)
- [x] 댓글 삭제 (작성자만)
- [x] 댓글 수 업데이트 (목록 동기화)

### 동기화 기능
- [x] 상세 모달에서 좋아요/저장 변경 시 목록 동기화
- [x] 상세 모달에서 댓글 작성/삭제 시 목록 동기화

---

## 🐛 알려진 이슈 및 개선 사항

### 1. 중복 퀴즈 체크
- ✅ 구현 완료: `BoardService.create`에서 `existsByQuiz_Id` 체크
- ✅ 에러 처리: `DUPLICATE_QUIZ (B004)` 에러 코드 추가
- ✅ 프론트 알림: "중복된 퀴즈입니다." 알림 표시

### 2. 타임존 처리
- ✅ `LocalDateTime.now(ZoneId.of("Asia/Seoul"))` 사용
- ✅ KST 기준으로 일관성 유지

### 3. 예외 처리
- ✅ 기존 DB 데이터 호환성 (quiz_list 파싱 실패 시 빈 리스트 반환)
- ✅ null 체크 및 적절한 에러 코드 반환

---

## 📝 Security 전환 시 체크리스트

### 백엔드
- [ ] `@RequestHeader("X-User-Id")` → `@AuthenticationPrincipal` 변경
- [ ] Security 설정에서 `/boards/**`, `/comments/**`, `/quizzes/**` 인증 요구 설정
- [ ] 게스트 접근 허용 엔드포인트는 `permitAll()` 유지
- [ ] 회원 전용 엔드포인트는 `authenticated()` 설정

### 프론트엔드
- [ ] `buildUserIdHeader` 함수 제거 또는 사용 중단
- [ ] 모든 API 함수에서 `userId` 파라미터 제거
- [ ] API 호출 시 헤더 전송 코드 제거
- [ ] 쿠키/세션 기반 인증으로 자동 전환 확인

---

## 📊 API 엔드포인트 요약

| 엔드포인트 | 메서드 | 권한 | 헤더 필요 여부 |
|-----------|--------|------|---------------|
| `/boards` | GET | 게스트/회원 | 선택적 (likedByMe/storedByMe용) |
| `/boards` | POST | 회원 | ✅ 필수 |
| `/boards/{boardId}` | GET | 게스트/회원 | 선택적 (likedByMe/storedByMe용) |
| `/boards/{boardId}` | DELETE | 작성자 | ✅ 필수 |
| `/boards/{boardId}/likes` | POST | 회원 | ✅ 필수 |
| `/boards/{boardId}/likes` | DELETE | 회원 | ✅ 필수 |
| `/boards/{boardId}/comments` | GET | 게스트/회원 | ❌ 불필요 |
| `/boards/{boardId}/comments` | POST | 회원 | ✅ 필수 |
| `/boards/{boardId}/comments/{commentId}` | DELETE | 작성자 | ✅ 필수 |
| `/quizzes/create` | POST | 회원 | ✅ 필수 |
| `/quizzes/my` | GET | 회원 | ✅ 필수 |
| `/quizzes/{quizId}/store` | POST | 회원 | ✅ 필수 |
| `/quizzes/{quizId}/store` | DELETE | 회원 | ✅ 필수 |
| `/game/{roomId}/quiz/generate` | POST | 게스트/회원 | 선택적 (자동 저장용) |

---

## 🎯 결론

모든 기능이 정상적으로 구현되었으며, Security 설정 후에는:
1. 백엔드에서 `@RequestHeader("X-User-Id")` → `@AuthenticationPrincipal`로 변경
2. 프론트엔드에서 헤더 전송 코드 제거
3. 쿠키/세션 기반 인증으로 자동 전환

현재 구조는 Security 전환에 대비하여 Service 레이어는 변경 없이 Controller만 수정하면 됩니다.
