# DocQ Project Execution Guide

이 파일은 DocQ 프로젝트의 백엔드와 프론트엔드 서버를 실행하기 위한 가이드입니다.

---

## 💻 Frontend (Vue.js + Vite)

프론트엔드 서버를 실행하려면 `docq-frontend` 디렉토리로 이동해야 합니다.

### 환경 설정 및 실행
```bash
# 디렉토리 이동
cd docq-frontend

# 의존성 설치
npm install

# 개발 서버 실행
npm run dev

# 프로덕션 빌드
npm run build
```

- **Local URL**: [http://localhost:5173](http://localhost:5173)

---

## ☕ Backend (Spring Boot + Gradle)

백엔드 서버를 실행하려면 `docq_backend` 디렉토리로 이동해야 합니다.

### 환경 설정 및 실행
```bash
# 디렉토리 이동
cd docq_backend

# 서버 실행 (Windows - CMD/PowerShell)
.\gradlew bootRun

# 서버 실행 (Bash)
./gradlew bootRun
```

- **Local URL**: [http://localhost:8081](http://localhost:8081)
- **Java Version**: 17

---

## 🤖 DocQ AI (Python + FastAPI)

AI 서버를 실행하려면 `docq-ai` 디렉토리로 이동해야 합니다.

### 환경 설정 및 실행
```bash
# 디렉토리 이동
cd docq-ai

# 가상환경 생성 (최초 1회)
python -m venv venv

# 가상환경 활성화 (Windows)
source venv/Scripts/activate
# 또는
.\venv\Scripts\activate

# 가상환경 활성화 (Mac/Linux)
source venv/bin/activate

# 의존성 설치
pip install -r requirements.txt

# 서버 실행
uvicorn app.main:app --reload
```
- **Local URL**: [http://localhost:8000](http://localhost:8000)

---

## 🗄️ Redis (Docker)

Redis는 Docker 컨테이너를 통해 실행하는 것을 권장합니다.

### 실행 명령어
```bash
# Redis 컨테이너 실행 (포트 6379)
docker run -d --name redis -p 6379:6379 redis
```

---

## 🛠️ 주요 수정 사항 (2026-01-22)
- **Build**: TypeScript 타입 에러 및 경로(Asset) 오류 수정
- **WebSocket**: CORS 엔드포인트 패턴 허용 및 포트 불일치(8080->8081) 해결
- **Git Sync**: Frontend 작업물 최신 develop 반영
