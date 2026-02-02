# 로그인 도입 및 userId → playerId 전환 계획

ERD와 현재 코드베이스를 기준으로, **게스트/로그인 통합**과 **playerId 기반 게임 플레이** 전환을 위한 단계별 계획입니다.

**이번 구현 범위:** Phase 1 ~ Phase 3까지만 진행. Phase 4(피드·저장 등 회원 전용 기능)는 추후 진행.  
**인증 방식:** 토큰(JWT) 미사용, **세션 기반 인증** (서버 세션에 로그인/게스트 정보 저장, 쿠키로 세션 ID 전달).

---

## 1. 현재 상태 요약

### 1.1 백엔드

| 구분 | 현재 구현 |
|------|-----------|
| **User 도메인** | `POST /users/login`(닉네임만 → UUID 발급), `PUT /users/{userId}/nickname` |
| **User 엔티티** | `id`, `nickname` (인메모리 Map). ERD의 `users`(login_id, password 등)와 **다름** |
| **게임/방 로직** | 전부 **userId** 사용: `Room.hostId`, `Room.players`, `PlayerState.userId`, GameDto/MiniGameDto, SessionRegistry |
| **방 생성 검증** | `userRepository.findById(request.getUserId())` 존재 여부로 호스트 검증 |

즉, 지금의 “user”는 **게스트 세션(닉네임 + UUID)** 이고, ERD의 “로그인 회원(users)”은 아직 없음.

### 1.2 프론트엔드

| 구분 | 현재 구현 |
|------|-----------|
| **userStore** | `userId` = `currentUser.id`, `loginWithApi(nickname)` → `/users/login` |
| **게임/방/웹소켓** | 방 생성·참여·준비·시작·퇴장·미니게임 등 **모두** `userStore.userId` 사용 |
| **API** | `userLogin`, `updateNickname` → `/users/login`, `/users/{userId}/nickname` |

### 1.3 목표 개념 정리

| 용어 | 의미 | 사용처 |
|------|------|--------|
| **playerId** | 게임 세션 참여자 식별자 (매번 UUID). 게스트/로그인 공통 | 방 참여, 턴, 점수, 아바타, 퀴즈 제출, 미니게임 등 **모든 게임 플레이** |
| **userId** | 로그인 회원의 RDS PK (`users.id`). 회원가입 시 한 번 부여 | 로그인 여부 판별, 피드 게시판 글쓰기, “내 퀴즈에 저장” 등 **회원 전용 기능** |

- **게스트**: `playerId`만 있음. `userId` 없음. `/guests/login`으로 **playerId**(UUID) 발급.
- **로그인 유저**: **로그인할 때마다** 새 **playerId**(UUID) 발급 + **userId**(RDS PK) 유지.  
  - DTO/스토어에는 `userId`와 `playerId` 둘 다 둠. 게스트는 `userId` 없이 `playerId`만 관리.
  - 게임 로직은 **항상 playerId**만 사용. “로그인 유저인지”는 **userId 존재 여부**로만 판단.
- **userId를 playerId로 쓰지 않는 이유**: userId는 RDS에 저장되는 회원 PK라, 게임 세션 식별자와 섞이면 의미가 섞이고 노출도 부담되므로, 로그인 시마다 **별도 UUID(playerId)** 를 발급해 세션·게임 참여자 식별용으로만 쓰는 쪽이 깔끔함.

---

## 2. API 설계 (최종 목표)

### 2.1 게스트 (기존 “user” URL을 guest로 이전)

| 메서드 | URI | 설명 |
|--------|-----|------|
| POST | `/guests/login` | 닉네임만 보내면 **playerId** 발급 (게스트 세션) |
| PUT | `/guests/{playerId}/nickname` | 해당 playerId의 닉네임 변경 |

### 2.2 인증 (ERD `users` 테이블 기반)

| 메서드 | URI | 설명 |
|--------|-----|------|
| POST | `/auth/signup` | 회원가입 (login_id, password, nickname) |
| POST | `/auth/login` | 로그인 (login_id, password) → **userId** + **playerId**(로그인마다 새 UUID) 반환. DTO에 둘 다 포함 |

---

## 3. 세션 기반 인증 – 세션에 저장할 정보

토큰을 쓰지 않고 **서버 세션(HttpSession)** 으로 인증할 경우, **로그인/게스트 정보는 세션에 저장**하는 것이 맞습니다. 클라이언트는 요청 시 쿠키(예: `JSESSIONID`)만 보내고, 서버가 세션에서 “누구인지”를 꺼내 씁니다.

### 3.1 게스트 세션 (POST /guests/login 성공 시)

| 세션 속성 | 설명 |
|-----------|------|
| `playerId` | 이번 게스트 세션에 부여한 UUID. 게임/방 API 검증용. |
| `nickname` | 선택. 닉네임 변경 시 세션도 갱신하면 됨. |

→ **게스트는 `userId` 없음.** 방 생성/참여 시 “이 playerId가 유효한가?”는 “이 playerId가 게스트 저장소에 있나?” 또는 “현재 세션의 playerId와 요청 body의 playerId가 같은가?”로 검증.

### 3.2 로그인 세션 (POST /auth/login 성공 시)

| 세션 속성 | 설명 |
|-----------|------|
| `userId` | RDS `users.id`. “로그인한 회원인지” 판별 및 추후 Phase 4(피드·저장)에서 사용. |
| `playerId` | 이번 로그인에 부여한 UUID. 게임/방 API에서는 이 값만 사용. |
| `nickname` | 선택. 응답/UI용. |

→ **playerId → userId 매핑은 별도 저장소 없이, “현재 세션에 userId가 있으면 이 세션은 로그인 유저”로 처리하면 됨.  
→ 방 생성/참여 시: 요청 body의 `playerId`가 **현재 세션의 playerId**와 일치하는지 검사하면, 위조 방지와 “이 playerId가 이 세션 소유” 검증이 동시에 가능.

### 3.3 정리

- **세션에 로그인 정보를 두는 이유:** 토큰을 쓰지 않으므로 “누가 요청했는지”는 세션에만 존재. 게스트/로그인 모두 세션에 `playerId`를 두고, 로그인만 추가로 `userId`를 두면 됨.
- **게임 API:** 요청 시 쿠키로 세션이 넘어오므로, 서버는 “세션의 playerId”와 “요청 body의 playerId”가 같은지 확인 후 처리. (또는 “이 playerId가 게스트 저장소 또는 어떤 로그인 세션에 있는지”만 검사하는 방식도 가능.)
- **Phase 4 이후:** 피드·저장 API는 “세션에 userId가 있는지”로 로그인 여부 판별 후, `userId`로 DB 접근.

---

## 4. 단계별 실행 계획

### Phase 1: 백엔드 – Guest API 분리 및 playerId 용어 통일

**목표:**  
- “user” URL을 “guest”로 옮기고,  
- 게임/방 도메인에서는 **변수·필드·DTO 필드명**을 `userId` → `playerId`로 통일 (동작은 “참여자 식별자”로 동일).

1. **Guest 도메인 추가**
   - `GuestController`: `POST /guests/login`, `PUT /guests/{playerId}/nickname`
   - 기존 `UserService.login` / `updateNickname` 로직을 **GuestService**로 이전하거나, UserService를 Guest 전용으로 두고 Controller만 `/guests`로 매핑
   - 응답 DTO 필드명: `userId` → `playerId` (GuestLoginResponse 등)

2. **게스트 저장소**
   - 현재 `User` 엔티티 + `UserRepository`를 “게스트 세션”용으로 사용 가능
   - 선택: 패키지/클래스명을 `guest`로 바꾸거나, 그대로 두고 **역할만 “playerId 발급/닉네임 저장”**으로 명확히 함

3. **Room / Game / MiniGame / WebSocket 전반**
   - `Room`: `hostId` → `hostPlayerId`, `players` 등 내부적으로 “playerId” 의미로 변수명 통일
   - `GameDto`, `MiniGameDto`, `PlayerState`, `AckStore`, `SessionRegistry` 등 **모든** `userId` 필드/변수 → `playerId`로 변경
   - **Room 생성/참여 검증**: “이 playerId가 유효한가?”만 검사 (기존: `userRepository.findById(userId)` → 동일 저장소를 “playerId 기준”으로 조회하도록 유지)

4. **기존 UserController/User API**
   - `/users/login`, `/users/{userId}/nickname` 제거 또는 deprecated 후, 클라이언트를 `/guests`로 이전 완료 후 삭제

5. **테스트**
   - `ApiVerificationTest` 등: `/users/login` → `/guests/login`, `userId` → `playerId` 로 요청/응답 수정

---

### Phase 2: 백엔드 – Auth 도메인 추가 (ERD users)

**목표:**  
회원가입/로그인을 ERD의 `users` 테이블로 구현하고, **로그인할 때마다 새 playerId(UUID)를 발급**해 DTO에 `userId`와 `playerId`를 함께 내려줌.

1. **DB 및 엔티티**
   - ERD 기준 `users` 테이블: `id`(BIGINT PK), `login_id`, `nickname`, `password`
   - JPA 엔티티 `User`(또는 `AuthUser`) 생성 (기존 게스트용 인메모리와 구분 필요)
   - 필요 시 기존 인메모리 User는 `Guest` 등 다른 이름으로 정리

2. **Auth API + 세션**
   - `AuthController`: `POST /auth/signup`, `POST /auth/login`
   - signup: 비밀번호 해시 저장
   - login: 인증 후 **playerId = UUID 새로 생성**, **세션에 저장**: `userId`, `playerId`, `nickname`. 응답 DTO에는 `userId`, `playerId`, `nickname` 반환. (토큰 없음 → 클라이언트는 응답 body로만 이 값들을 받고, 이후 요청 시 쿠키로 세션만 전달)

3. **Guest 로그인 + 세션**
   - `POST /guests/login` 성공 시: **세션에 저장** `playerId`, `nickname`. 응답 DTO는 `playerId`, `nickname`만 (userId 없음).

4. **방/게임 검증 (세션 기반)**
   - 방 생성/참여 등: 요청 body의 `playerId`가 **현재 세션의 playerId**와 일치하는지 검사. (쿠키로 넘어온 세션에서 playerId 조회)
   - 게스트: 세션에 playerId만 있음. 로그인: 세션에 userId + playerId 있음. 별도 playerId→userId 매핑 저장소 없이 세션만으로 처리 가능.

---

### Phase 3: 프론트엔드 – playerId / userId 분리 및 API 전환

**목표:**  
스토어와 API를 “게임은 playerId”, “로그인 여부는 userId”로 맞추고, Guest/Auth API에 연결.

1. **userStore (또는 authStore 분리)**
   - **playerId**: 게스트/로그인 공통. 게임·방·웹소켓 **모든 호출에 사용**. (로그인 유저도 서버가 로그인마다 준 playerId 사용)
   - **userId**: 로그인 시에만 존재 (nullable). 있으면 “로그인 유저”.
   - 게스트: `guestLogin(nickname)` → `POST /guests/login` → 응답 `playerId`만 저장, `userId = null`
   - 로그인: `authLogin(loginId, password)` → `POST /auth/login` → 응답 `userId` + `playerId` 둘 다 저장. 게임에는 항상 `playerId`만 사용.
   - 회원가입: `authSignup(...)` → `POST /auth/signup`

2. **API 클라이언트**
   - `guestLogin`: `POST /guests/login` → 응답 `playerId`, `nickname` (userId 없음)
   - `updateNickname`: `PUT /guests/{playerId}/nickname` (게스트용). 로그인 유저 닉네임 변경이 따로 있으면 별도 API.
   - `authLogin`: `POST /auth/login` → 응답 `userId`, `playerId`, `nickname` 등

3. **타입 정의 (docq-api.ts 등)**
   - Guest 로그인 응답: `playerId`, `nickname` (userId 없음)
   - Auth 로그인 응답: `userId`, `playerId`, `nickname` — DTO에 **userId와 playerId 둘 다** 두고, 게스트는 userId 없이 playerId만 관리

4. **화면/컴포넌트**
   - 메인/로비: “닉네임만 입력” → 게스트 로그인(`guestLogin`) → `playerId` 발급
   - “로그인” 버튼/화면 → `authLogin` → 성공 시 응답의 `userId`와 `playerId` 둘 다 저장
   - 방 생성/참여, 게임 진행, 웹소켓 구독 등 **기존에 userStore.userId 쓰던 부분** → 전부 **playerId**로 교체 (이미 store에서 `userId`가 id라면, “게임용 id”를 playerId getter로 통일).

5. **웹소켓**
   - 연결/구독 시 헤더·구독 경로에 **playerId** 사용 (에러 큐 등 기존 `userId` 경로를 `playerId`로 통일).

6. **API 요청 시 쿠키(세션) 포함**
   - 세션 기반이므로, 프론트에서 Guest/Auth API 및 방·게임 API 호출 시 **credentials: 'include'**(또는 axios `withCredentials: true`)로 쿠키를 함께 보내야 서버가 세션을 인식함.

---

### Phase 4: 로그인 유저 전용 기능 (피드·저장) — 이번 범위 아님

**목표:**  
“userId가 있을 때만” 피드 게시판 글쓰기, 게시물 퀴즈 “내 퀴즈에 저장” 허용.

1. **백엔드**
   - boards, stored_quizzes 등 API에서 “이 요청의 주체가 로그인 유저인지”를 **userId**(JWT/세션에서 추출)로 검증.
   - 게임 API는 계속 **playerId**만 사용.

2. **프론트**
   - 피드 글쓰기/저장 버튼: `userId` 없으면 비활성화 또는 “로그인 필요” 안내.

---

## 5. 체크리스트 (요약) — Phase 3까지

- [ ] **Phase 1**  
  - [ ] Guest API: `/guests/login`, `/guests/{playerId}/nickname`  
  - [ ] 백엔드 전역: 게임/방/웹소켓에서 `userId` → `playerId` 변수·필드명 변경  
  - [ ] 기존 `/users` API 제거 또는 deprecated  
  - [ ] 테스트 코드 경로/필드명 수정  

- [ ] **Phase 2**  
  - [ ] ERD `users` 테이블 및 엔티티  
  - [ ] `/auth/signup`, `/auth/login`  
  - [ ] 로그인 시 **playerId(매번 새 UUID)** 발급, **세션에 userId, playerId, nickname 저장**  
  - [ ] 게스트 로그인 시 **세션에 playerId, nickname 저장**  
  - [ ] 방/게임 API에서 “요청 body의 playerId == 세션의 playerId” 검증  

- [ ] **Phase 3**  
  - [ ] 스토어에 `playerId` / `userId` 분리  
  - [ ] Guest/Auth API 연동 및 타입 정리  
  - [ ] 모든 게임/방/웹소켓 호출을 playerId 기준으로 통일  
  - [ ] API 호출 시 `withCredentials: true` 등으로 쿠키(세션) 포함  

- [ ] **Phase 4** (추후)  
  - [ ] 피드·저장 API는 userId로 검증  
  - [ ] 프론트에서 로그인 유저만 글쓰기/저장 가능하도록 처리  

이 순서대로 진행하면 “게임은 playerId, 로그인 여부는 userId”로 정리하면서 기존 게스트 플로우를 유지하고, ERD 기반 로그인을 추가할 수 있습니다.
