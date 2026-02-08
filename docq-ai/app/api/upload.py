from fastapi import APIRouter, UploadFile, File
from fastapi.responses import JSONResponse
import tempfile
import uuid
import fitz
import logging
import os

from app.scheduler.scheduler import enqueue

router = APIRouter()
logger = logging.getLogger(__name__)

MAX_PAGES = 50


@router.post("/ai/upload/pdf")
async def upload_pdf(file: UploadFile = File(...)):
    logger.info("UPLOAD PDF REQUEST RECEIVED")
    tmp_path = None

    try:
        # 1. 파일 저장
        with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name

        # 2. 가벼운 검증 (페이지 수)
        with fitz.open(tmp_path) as doc:
            page_count = doc.page_count

        if page_count > MAX_PAGES:

            os.remove(tmp_path)
            return JSONResponse(
                status_code=400,
                content={
                    "detail": "PDF has too many pages",
                    "page_count": page_count,
                    "max_pages": MAX_PAGES
                }
            )

        # 3. Job 생성
        job_id = str(uuid.uuid4())
        job = {
            "jobId": job_id,
            "pdf_path": tmp_path,
            "filename": file.filename
        }

        # 4. Redis Queue에 등록
        enqueue(job)

        # 5. 즉시 응답
        return {
            "jobId": job_id,
            "status": "QUEUED"
        }

    except Exception as e:
        logger.exception("UPLOAD FAILED")

        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)
            logger.info(f"TEMP FILE CLEANED UP: {tmp_path}")

        return JSONResponse(
            status_code=500,
            content={"detail": str(e)}
        )
