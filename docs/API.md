### 사용자 접속

앱 최초 진입 시, 닉네임을 설정하고 고유 ID를 발급받습니다.

- **URI:** `POST /users/login`
- **Request Body:**JSON

```json
{
  "nickname": "개발왕" // 필수
}
```

- **Response (200 OK):**JSON
    
    ```sql
    {
      "userId": "uuid-v4-string", // 로컬 스토리지 저장 필수
      "nickname": "개발왕"
    }
    ```
    
- **Possible Exceptions:**
    - `C002`: 닉네임이 비어있거나 너무 김.
    - `U002`: 닉네임 중복

### 방관리 - 방생성 (HOST)

- **URI:** `POST /rooms`
- **Request Body:**JSON

```json
{
  "userId": "uuid-v4-string",
  "capacity": 4,
  "title": "개발왕님의 게임",    //  방 제목
  "isPrivate": true,         // true: 비공개, false: 공개
  "password": "1234"         // isPrivate이 true일 경우 필수
}
```

- **Response (201 Created):**JSON

```json
{
  "roomId": "room-uuid",
  "inviteCode": "A1B2",
  "hostId": "uuid-v4-string",
  "isPrivate": true
}
```

- **Possible Exceptions:**
    - `U001`: 유효하지 않은 hostId.
    - `C002`: `isPrivate`가 true인데 `password`가 비어있는 경우.

### 방관리 - 방 조회

- **URI:** `GET /rooms/{roomId}`
- `GET /rooms?inviteCode=XYZ123` (1차 mvp)
- **Response (200 OK):**JSON

```json
{
  "roomId": "room-uuid",
  "title": "개발왕님의 게임",
  "currentCount": 1,
  "capacity": 4,
  "status": "WAITING",
  "isPrivate": true,    // 잠금 표시 아이콘용
  "hasPassword": true   // true면 클라이언트에서 비밀번호 입력 모달 띄움
}
```

- **Possible Exceptions:**
    - `R001`: 존재하지 않는 방 ID.

### 방관리 - 입장 검증

**URI:** `POST /rooms/join`

**Request Body:** JSON

```sql
{
  "roomCode": "A1B2C3",  // 입장하려는 방 코드 (필수)
  "password": "1234"     // 비공개 방일 경우 필수
}
```

**Response (200 OK):** JSON

```sql
{
  "roomid": "room-uuid-v4", // 소켓 구독에 사용할 실제 Room ID
  "roomCode": "A1B2C3",
  "status": "WAITING"
}

```

### 문서

- **URI:** `POST /rooms/{roomId}/upload`
- **Content-Type:** `multipart/form-data`
- **Request Param:** `file` (Binary PDF)
- **Response (200 OK):**JSON
    - *Note: 퀴즈 데이터가 바로 오지 않습니다. 처리가 끝나면 소켓으로 알림이 갑니다.*
    
    ```sql
    {
      "message": "업로드 성공. 퀴즈 생성 중...",
      "status": "PROCESSING"
    }
    ```
    
- **Possible Exceptions:**
    - `R001`: 방 없음.
    - `D001`: 파일 업로드 실패 (I/O).
    - `D003`: PDF가 아닌 파일.

### 소켓 연결

## 🔌 WebSocket 커스텀 훅 (`src/hooks/useGameSocket.js`)

```jsx
import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';

const useGameSocket = (roomId) => {
  const client = useRef(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    client.current = new Client({
      
      brokerURL: 'ws://localhost:8080/ws-docq',
			
			// MVP용 헤더 설정
		  connectHeaders: {
		       userId: userId    // <-- 이건 필수! (누군지는 알아야 함)
		  },
  
      // 디버깅 로그
      debug: (str) => {
        console.log(str);
      },

      // 연결 끊김 시 재연결 대기 시간 (5초)
      reconnectDelay: 5000,
      
      // Heartbeat (연결 유지 신호)
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      // 연결 성공 시
      onConnect: () => {
        console.log('✅ Native WebSocket 연결 성공!');
        setConnected(true);
        subscribeToRoom();
      },

      // 에러 발생 시
      onStompError: (frame) => {
        console.error('❌ STOMP 에러:', frame.headers['message']);
      },
      
      // 소켓 끊김 시
      onWebSocketClose: () => {
         console.log('⚠️ 소켓 연결 끊김');
         setConnected(false);
      }
    });

    client.current.activate();

    return () => {
      if (client.current) client.current.deactivate();
    };
  }, [roomId]);

  // --- [기능 함수들] ---

  // 구독하기 (받기)
  const subscribeToRoom = () => {
    if (!client.current) return;

    // 게임 상태 구독
    client.current.subscribe(`/topic/game/${roomId}/state`, (message) => {
      const data = JSON.parse(message.body);
      console.log('상태 변경:', data);
    });
  };

  // 메시지 보내기 (주기)
  const sendMessage = (destination, body) => {
    if (client.current && client.current.connected) {
      client.current.publish({
        destination: destination,
        body: JSON.stringify(body),
      });
    } else {
      console.warn('소켓이 연결되지 않았습니다.');
    }
  };

  return { connected,sendMessage, client: client.current };
};

export default useGameSocket;
```

### 게임 - 방 참여전

**Response Payload (Server → Client):** `GameStateDTO`

```sql
{
  "state": "WAITING", 
  
  "players": [
    {
      "userid": "user-uuid-1",
      "nickname": "방장",
      "isHost": true,
      "connected": true,
      "isReady": true
    }
    // ... 참여 요청 성공 시 내 정보가 여기에 추가되어 내려옴
  ]
}
```

**Response Payload (Server → Client):**

`GameDto.ErrorResponse`

```sql
{
  "code": "..", 
  
  "message": ".."
}
```

- **Destination:** `/app/game/join`
- **Payload:**JSON
    
    ```sql
    {
      "roomId": "room-uuid-abc",
      "nickname": "도전왕",
      "userId": "user-uuid-567"
    }
    ```
    
- **Exceptions (Error Topic):** `R001`(방없음), `R002`(방꽉참), `R003`(이미시작됨),

### 게임 - 방 참여후

- **Destination:** `/app/game/ready`
- **Payload:**JSON
    
    ```sql
    {
      "roomId": "room-uuid",
      "userId": "user-uuid",
      "isReady": "true"
    }
    ```
    
- **Exceptions:** `G001`(권한없음)

- **Destination:** `/app/game/start`
- **Payload:**JSON
    
    ```sql
    {
      "roomId": "room-uuid",
      "userId": "user-uuid" // 방장 검증용
    }
    ```
    
- **Exceptions:** `G001`(권한없음)

### 게임 - 공통

GameStateDTO (상태 동기화)

`/topic/game/{roomId}/state` 로 수신

```sql
{
  "state": "QUIZ_TIME", // WAITING, STARTING, QUIZ_TIME, JUDGMENT, ROULETTE, MOVE, FINISHED
  "round": 1,
  "currentTurnUserId": "user-uuid-1", // 현재 턴인 사람
  "players": [
    {
      "userId": "user-uuid-1",
      "nickname": "개발왕",
      "isHost": true,
      "score": 10,
      "position": 5, // 맵 인덱스 (0~30)
      "connected": true,
      "hasBuff" : "DOUBLE_SCORE" //null일 시에 증강 없음.
    },
    {
      "userId": "user-uuid-2",
      "nickname": "초보",
      "isHost": false,
      "score": 0, //맞힌 퀴즈 수
      "position": 0,
      "connected": true,
      "hasBuff" : null
    }
  ]
   ,"turnOrder": ["playerA", "playerB", "playerC"]
  ,"cardDrawResult": {
    "playerA": 98,
    "playerB": 50,
    "playerC": 12
  }
		 
		 
		 
}
```

## Game State Transition Rules

WAITING

- 모든 플레이어 입장 완료
- 모든 플레이어 isReady = true
→ STARTING

STARTING

- 카운트다운 종료
→ QUIZ_TIME

QUIZ_TIME

- 모든 플레이어 제출 완료 OR timeLimit 초과
→ JUDGMENT

JUDGMENT

- 정답 판정 및 winnerUserId 결정
→ ROULETTE

ROULETTE

- 룰렛 결과 확정
→ MOVE

MOVE

- 이동 애니메이션 종료
- 목표 지점 도달 여부 체크
    - 도달 시 → FINISHED
    - 미도달 시 → 다음 턴 QUIZ_TIME

FINISHED

- 결과 리포트 전송
- 재시작 선택 시 → WAITING

### 게임 - 진행

QuizMessageDTO (퀴즈 출제)

`/topic/game/{roomId}/quiz` 로 수신

```sql
{
  "quizId": 101,
  "question": "다음 중 Java의 기본형이 아닌 것은?",
  "options": ["int", "double", "boolean", "String"], // 4지선다
  "timeLimit": 5000 // 밀리초 (5초)
}
```

- **Destination:** `/app/game/submit`
- **Payload:**JSON
    
    ```sql
    {
      "roomId": "room-uuid",
      "userId": "user-uuid",
      "quizId": 101,
      "answer": "객체지향" // 혹은 인덱스(2)
    }
    ```
    
- **Exceptions:** `G001`(이미 제출함/턴 아님 등)

RoundResultDTO (정답 공개)

`/topic/game/{roomId}/result` 로 수신

```sql
{
  "quizId": 101,
  "correctAnswer": "String", // 혹은 인덱스(3)
  "isTurnUserCorrect": false, // 턴 주인 정답 여부
  "winnerUserId": "user-uuid-2", // 이번에 룰렛 돌릴 사람 (턴 주인 or 빨리 맞춘 고스트)
  "rouletteType": "BUFF" // MOVE(이동) or BUFF(증강)
}
```

- **Destination:** `/app/game/roulette`
- **Payload:**JSON
    
    ```sql
    {
      "roomId": "room-uuid",
      "userId": "user-uuid"
    }
    ```
    

MoveDTO (이동 정보)

`/topic/game/{roomId}/move` 로 수신

```sql
{
  "userId": "user-uuid-2",
  "startPosition": 1,
  "endPosition": 12,

  "moveProcess": [
    {
      "type": "ROULETTE",
      "value": 3
    },
    {
      "type": "BUFF",
      "buffType": "DOUBLE_MOVE",
      "before": 3,
      "after": 6
    },
    {
      "type": "TILE_EFFECT",
      "effectType": "DOUBLE_SCORE",
      "before": 6,
      "after": 12
    }
  ]
}
```

### 미니 게임

- **Topic:** `/topic/game/{roomId}/minigame`
- **Type:** `SUB` (받기)
- **Payload (MiniGameStartDTO):**JSON
    
    ```sql
    {
      "type": "START",
      "gameType": "SPACE_MASH", // SPACE_MASH(스페이스 연타), CLICK_MASH(클릭 연타), EXACT_TIME(5초 맞추기)
      "timeLimit": 5000,        // 제한시간 (ms)
      "description": "5초 동안 스페이스바를 최대한 많이 누르세요!"
    }
    ```
    

- **Destination:** `/app/game/minigame/submit`
- **Type:** `PUB` (보내기)
- **Payload (MiniGameSubmissionDTO):**JSON
    
    ```sql
    {
      "roomId": "room-uuid-123",
      "userId": "user-uuid-abc",
      "gameType": "SPACE_MASH", 
      "resultValue": 45  // 연타 횟수 or 멈춘 시간(ms)
    }
    ```
    
- **`resultValue` 설명:**
    - `SPACE_MASH` / `CLICK_MASH`: 누른 **횟수** (예: 45)
    - `EXACT_TIME`: 5초와의 **오차값(절대값)** 또는 **멈춘 시간** (예: 5012)

- **Topic:** `/topic/game/{roomId}/minigame`
- **Type:** `SUB` (받기)
- **Payload (MiniGameResultDTO):**JSON
    
    ```sql
    {
      "type": "RESULT",
      "gameType": "SPACE_MASH",
      "winnerUserId": "user-uuid-abc", // 1등 유저 (없으면 null - 동점 등 처리)
      "rewardBuff": "DOUBLE_SCORE",    // 1등이 얻은 증강 효과
      "rankings": [ // (선택사항) 결과 보여주기용 순위표
        {
          "userId": "user-uuid-abc",
          "nickname": "개발왕",
          "value": 45,
          "rank": 1
        },
        {
          "userId": "user-uuid-def",
          "nickname": "코딩초보",
          "value": 30,
          "rank": 2
        }
      ]
    }
    ```
    
    - **Destination:** `/app/game/minigame/roulette`
    - **Payload:**JSON
        
        ```sql
        {
          "roomId": "room-uuid",
          "userId": "user-uuid"
        }
        ```
        
    
    ### 시스템 에러
    
    | **Code** | **HTTP** | **Message (KR)** | **발생 상황** |
    | --- | --- | --- | --- |
    | **C001** | 500 | 서버 내부 에러 | NullPointer 등 서버 버그 |
    | **C002** | 400 | 잘못된 입력값 | 파라미터 누락, 타입 불일치 |
    | **R001** | 404 | 존재하지 않는 방 | 방 ID 오류 |
    | **R002** | 400 | 방 인원 초과 | 4명 꽉 참 |
    | **R003** | 400 | 이미 시작된 게임 | PLAYING 상태 방 입장 시도 |
    | **R004** | 404 | 게임 정보 없음 | 서버 재시작 등으로 게임 객체 소실 |
    | **R005** | 401 | **비밀번호가 일치하지 않음** | 비공개 방 입장 시 비밀번호 틀림 |
    | **R006** | 400 | **비밀번호를 입력해주세요.** | 비공개 방인데 password 필드 없이 요청함 |
    | **U001** | 409 | 닉네임 중복 | 방 안에 같은 닉네임 존재 |
    | **G001** | 400 | 턴 오류 | 내 턴이 아닌데 룰렛/제출 시도 |
    | **D001** | 500 | 파일 업로드 실패 | 서버 디스크/권한 에러 |
    | **D002** | 500 | AI 처리 실패 | FastAPI 통신 오류 |
    | **D003** | 400 | 파일 형식 오류 | PDF 아님 |