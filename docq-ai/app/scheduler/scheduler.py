import json
import time
import threading
import logging

from infra.redis import redis_client
from workers.mineru_worker import run_gpu_job

logger = logging.getLogger(__name__)

QUEUE_KEY = "mineru:queue"
LOCK_TTL = 600  # GPU 작업 최대 시간
WORKERS = [18001, 18002, 18003]  # 논리적 GPU worker


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

        port = acquire_worker()
        if not port:
            enqueue(job)
            time.sleep(1)
            continue

        try:
            logger.info(f"[SCHEDULER] Dispatch job {job['jobId']} to worker {port}")
            result = run_gpu_job(job)

            # 결과 저장 (임시: Redis)
            redis_client.set(
                f"mineru:job:{job['jobId']}:result",
                json.dumps(result),
                ex=3600
            )

        except Exception:
            logger.exception("[SCHEDULER] Job failed, requeue")
            enqueue(job)

        finally:
            release_worker(port)


def start_scheduler():
    threading.Thread(
        target=scheduler_loop,
        daemon=True
    ).start()
