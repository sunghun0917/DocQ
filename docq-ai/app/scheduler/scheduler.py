import json
import time
import threading
import logging
import os
import requests
from concurrent.futures import ThreadPoolExecutor

from app.infra.redis import redis_client
from app.workers.mineru_worker import run_gpu_job

logger = logging.getLogger(__name__)

QUEUE_KEY = "mineru:queue"
LOCK_TTL = 600  # GPU 작업 최대 시간
BACKEND_CALLBACK_URL = os.getenv("BACKEND_CALLBACK_URL", "http://backend:8081/ai/callback")

# MinerU GPU 워커 설정 (포트 → API URL 매핑)
# 환경변수로 호스트 설정 가능
MINERU_HOST = os.getenv("MINERU_HOST", "localhost")
WORKERS = {
    18000: f"http://{MINERU_HOST}:18000/file_parse",
    18001: f"http://{MINERU_HOST}:18001/file_parse",
    # 포트 추가로 서버 늘리기 가능
}


def send_callback(job_id: str, status: str, result: dict = None, error: str = None):
    """Spring Boot에 결과 전송"""
    try:
        payload = {
            "jobId": job_id,
            "status": status,
        }
        if result:
            payload["total"] = result.get("total", 0)
            payload["quizzes"] = result.get("quizzes", [])
        if error:
            payload["error"] = error

        response = requests.post(BACKEND_CALLBACK_URL, json=payload, timeout=10)
        logger.info(f"[SCHEDULER] Callback sent for job {job_id}, response: {response.status_code}")
    except Exception as e:
        logger.error(f"[SCHEDULER] Callback failed for job {job_id}: {e}")


# ---------- Queue ----------

def enqueue(job: dict):
    redis_client.lpush(QUEUE_KEY, json.dumps(job))


def dequeue():
    job = redis_client.rpop(QUEUE_KEY)
    return json.loads(job) if job else None


# ---------- Lock ----------

def acquire_worker():
    """사용 가능한 워커를 찾아 (port, api_url) 튜플 반환"""
    for port, api_url in WORKERS.items():
        lock_key = f"mineru:worker:{port}:lock"
        if redis_client.set(lock_key, "BUSY", nx=True, ex=LOCK_TTL):
            return port, api_url
    return None, None


def release_worker(port):
    redis_client.delete(f"mineru:worker:{port}:lock")


# ---------- Thread Pool ----------

# 워커 수만큼 스레드풀 생성
executor = ThreadPoolExecutor(max_workers=len(WORKERS))


def process_job(job: dict, port: int, api_url: str):
    """별도 스레드에서 실행될 작업 처리 함수"""
    pdf_path = job.get("pdf_path")
    try:
        logger.info(f"[WORKER:{port}] Start job {job['jobId']}")
        result = run_gpu_job(job, api_url)
        send_callback(job["jobId"], "completed", result=result)
        logger.info(f"[WORKER:{port}] Completed job {job['jobId']}")
    except Exception as e:
        logger.exception(f"[WORKER:{port}] Job failed: {job['jobId']}")
        send_callback(job["jobId"], "failed", error=str(e))
    finally:
        release_worker(port)
        # 파일 정리
        if pdf_path and os.path.exists(pdf_path):
            try:
                os.remove(pdf_path)
                logger.info(f"[WORKER:{port}] Cleaned up {pdf_path}")
            except Exception:
                logger.warning(f"[WORKER:{port}] Failed to cleanup {pdf_path}")


# ---------- Scheduler Loop ----------

def scheduler_loop():
    logger.info("[SCHEDULER] Started (parallel mode)")

    while True:
        job = dequeue()
        if not job:
            time.sleep(0.5)  # 병렬 처리 시 더 빠른 폴링
            continue

        pdf_path = job.get("pdf_path")

        # 파일 존재 확인
        if not pdf_path or not os.path.exists(pdf_path):
            logger.error(f"[SCHEDULER] PDF not found: {pdf_path}, job {job['jobId']} dropped")
            send_callback(job["jobId"], "failed", error="PDF file not found")
            continue

        port, api_url = acquire_worker()
        if not port:
            # 모든 워커가 사용 중 → 큐에 다시 넣고 대기
            enqueue(job)
            time.sleep(0.5)
            continue

        # 별도 스레드에서 비동기 실행 (블로킹 없음)
        logger.info(f"[SCHEDULER] Dispatch job {job['jobId']} to worker {port}")
        executor.submit(process_job, job, port, api_url)


def start_scheduler():
    threading.Thread(
        target=scheduler_loop,
        daemon=True
    ).start()
