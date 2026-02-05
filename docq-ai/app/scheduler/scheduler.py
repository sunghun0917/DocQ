import json
import time
import threading
import logging
import os
import requests

from app.infra.redis import redis_client
from app.workers.mineru_worker import run_gpu_job

logger = logging.getLogger(__name__)

QUEUE_KEY = "mineru:queue"22222222
LOCK_TTL = 600  # GPU 작업 최대 시간
WORKERS = [18001, 18002, 18003]  # 논리적 GPU worker
BACKEND_CALLBACK_URL = os.getenv("BACKEND_CALLBACK_URL", "http://backend:8080/ai/callback")


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
    for port in WORKERS:
        lock_key = f"mineru:worker:{port}:lock"
        if redis_client.set(lock_key, "BUSY", nx=True, ex=LOCK_TTL):
            return port
    return None


def release_worker(port):
    redis_client.delete(f"mineru:worker:{port}:lock")


# ---------- Scheduler Loop ----------

def scheduler_loop():
    logger.info("[SCHEDULER] Started")

    while True:
        job = dequeue()
        if not job:
            time.sleep(1)
            continue

        pdf_path = job.get("pdf_path")
        
        # 파일 존재 확인
        if not pdf_path or not os.path.exists(pdf_path):
            logger.error(f"[SCHEDULER] PDF not found: {pdf_path}, job {job['jobId']} dropped")
            send_callback(job["jobId"], "failed", error="PDF file not found")
            continue

        port = acquire_worker()
        if not port:
            enqueue(job)
            time.sleep(1)
            continue

        try:
            logger.info(f"[SCHEDULER] Dispatch job {job['jobId']} to worker {port}")
            result = run_gpu_job(job)

            # Spring Boot에 결과 전송
            send_callback(job["jobId"], "completed", result=result)

        except Exception as e:
            logger.exception("[SCHEDULER] Job failed, dropped")
            send_callback(job["jobId"], "failed", error=str(e))
            

        finally:
            release_worker(port)

            # 파일 정리
            if pdf_path and os.path.exists(pdf_path):
                try:
                    os.remove(pdf_path)
                    logger.info(f"[SCHEDULER] Cleaned up {pdf_path}")
                except Exception:
                    logger.warning(f"[SCHEDULER] Failed to cleanup {pdf_path}")


def start_scheduler():
    threading.Thread(
        target=scheduler_loop,
        daemon=True
    ).start()
