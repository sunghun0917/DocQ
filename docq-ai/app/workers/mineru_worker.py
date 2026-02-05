import logging
import asyncio
from app.pdf_pipeline import extract_text_with_fallback
from app.fast_quiz_generator import generate_dynamic_quizzes

logger = logging.getLogger(__name__)


def run_gpu_job(job: dict, api_url: str = None) -> dict:
    """
    실제 GPU 작업
    
    Args:
        job: 작업 정보 (jobId, pdf_path 등)
        api_url: MinerU API URL (None이면 기본값 사용)
    """
    job_id = job["jobId"]
    pdf_path = job["pdf_path"]

    logger.info(f"[WORKER] Start job {job_id} (api: {api_url})")

    text = extract_text_with_fallback(pdf_path, api_url)
    text_len = len(text.strip())

    if text_len <= 5000:
        target = 20
    elif text_len <= 10000:
        target = 25
    else:
        target = 30

    logger.info(f"[WORKER] Ready crerate quizzes")

    quizzes = asyncio.run(
        generate_dynamic_quizzes(text, target=target)
    )
    logger.info(f"[WORKER] Finished job {job_id}")

    return {
        "jobId": job_id,
        "total": len(quizzes),
        "quizzes": quizzes
    }
