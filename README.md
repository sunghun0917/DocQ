# 📄🎲 DocQ (Document + Quiz)

DocQ는 **PDF 문서를 게임으로 바꾸는 멀티플레이 퀴즈 웹 서비스**입니다.
단체 활동, 스터디, 워크숍 등에서 하나의 문서를 함께 읽고,
경쟁과 상호작용 속에서 내용을 자연스럽게 이해하고 기억하도록 돕습니다.

---

## 🔍 서비스 개요

DocQ는 사용자가 업로드한 PDF 문서를 AI가 분석하여
의미 있는 콘텐츠를 추출하고, 이를 기반으로 퀴즈를 자동 생성합니다.
최대 4명의 사용자가 하나의 게임 룸에 참여하여
턴제 방식의 보드 게임을 플레이하며 문서 내용을 경험하게 됩니다.

문서를 ‘읽는 것’에서 끝나는 것이 아니라,
**함께 반응하고, 웃고, 경쟁하는 과정 자체가 콘텐츠가 되는 경험**을 제공합니다.

---

## 🎮 주요 기능

* **PDF 의미 기반 파싱**
  단순 텍스트 분해가 아닌 문서의 핵심 개념과 흐름을 중심으로 콘텐츠를 추출합니다.

* **AI 퀴즈 자동 생성**
  문서 내용을 기반으로 객관식 및 서술형 퀴즈를 생성합니다.

* **실시간 멀티플레이 게임**
  하나의 게임 룸에서 최대 4명이 동시에 참여하여 경쟁합니다.

* **턴제 보드 게임 시스템**
  문제를 맞힌 플레이어가 주사위를 굴려 보드를 이동하며 게임을 진행합니다.

* **AI 서술형 답변 평가**
  정답 키워드 매칭이 아닌 의미 유사도를 기준으로 답변을 평가합니다.

* **미니게임 요소**
  방향키 입력 기반 미니게임을 통해 몰입도와 현장 분위기를 높입니다.

---

## ✨ DocQ의 특징

* 문서를 **학습 자료가 아닌 게임 콘텐츠**로 전환합니다.
* 경쟁과 실패 경험이 자연스럽게 기억으로 이어지는 구조를 가집니다.
* 별도의 콘텐츠 제작 없이 PDF 하나로 즉시 플레이가 가능합니다.
* 스터디, 교육, 팀 빌딩, 레크리에이션 등 다양한 상황에 적용할 수 있습니다.

---

## 🚀 이런 상황에 적합합니다

* 스터디나 수업 후 내용을 함께 정리하고 싶을 때
* 워크숍이나 세미나에서 분위기를 환기하고 싶을 때
* 팀원들과 가볍게 경쟁하며 콘텐츠를 공유하고 싶을 때

---

DocQ는
**문서를 읽는 시간을, 함께 즐기는 경험으로 바꿉니다.**

---
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
