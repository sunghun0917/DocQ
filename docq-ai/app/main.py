from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from fastapi import UploadFile, File
import tempfile
from app.pdf_pipeline import extract_text_with_fallback
from app.fast_quiz_generator import generate_dynamic_quizzes
from fastapi.middleware.cors import CORSMiddleware
import traceback
import logging
import re
import fitz

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)
MAX_PAGES = 50

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logger.info("SERVER RELOADED - READY")

@app.post("/ai/upload/pdf")
async def upload_pdf(
    file: UploadFile = File(...)
):
    logger.info("ENDPOINT HIT: /ai/upload/pdf")
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name

        with fitz.open(tmp_path) as doc:
            page_count = doc.page_count
        if page_count > MAX_PAGES:
            return JSONResponse(
                status_code=400,
                content={
                    "detail": "PDF페이지가 너무 많습니다. 50페이지 이하로 업로드해주세요.",
                    "page_count": page_count,
                    "max_pages": MAX_PAGES,
                }
            )

        text = extract_text_with_fallback(tmp_path)
        logger.info("Final Text preview (first 500 chars): %s", text[:500])

        text_len = len(text.strip())
        if text_len < 2000:
            return JSONResponse(
                status_code=400,
                content={
                    "detail": "PDF내용이 너무 짧습니다. 더 많은 내용을 포함한 PDF를 업로드해주세요.",
                    "text_preview": text[:2000],
                    "text_length": text_len,
                    "min_length": 2000,
                }
            )

        # Determine target quiz count based on text length
        if text_len <= 5000:
            target_count = 20
        elif text_len <= 10000:
            target_count = 25
        else:
            target_count = 30

        quiz_pool = await generate_dynamic_quizzes(text, target=target_count)

        return {
            "text_preview": text[:2000], 
            "total": len(quiz_pool),
            "quizzes": quiz_pool
        }

    except Exception as e:
        error_msg = f"Server Error: {str(e)}\n{traceback.format_exc()}"
        logger.exception("Server Error")
        return JSONResponse(
            status_code=500,
            content={"detail": error_msg}
        )


@app.post("/ai/parse_only/pdf")
async def parse_only_pdf(
    file: UploadFile = File(...)
):
    logger.info("ENDPOINT HIT: /ai/parse_only/pdf")
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name

        with fitz.open(tmp_path) as doc:
            page_count = doc.page_count
        if page_count > MAX_PAGES:
            return JSONResponse(
                status_code=400,
                content={
                    "detail": "PDF has too many pages. Please upload a shorter document.",
                    "page_count": page_count,
                    "max_pages": MAX_PAGES,
                }
            )

        text = extract_text_with_fallback(tmp_path)
        logger.info("Final Text preview (first 500 chars): %s", text[:500])

        if len(text.strip()) < 50:
            return JSONResponse(
                status_code=400,
                content={
                    "detail": "Text is too short. Please upload a clearer PDF.",
                    "text_preview": text[:2000]
                }
            )
        
        # Quality Check Metrics
        hangul_count = len(re.findall(r"[가-힣]", text))
        allowed_count = len(re.findall(r"[0-9A-Za-z가-힣\s\.\,\?\!\:\;\-\(\)\"\'\+\=\*\/%]", text))
        text_len = len(text.strip())
        hangul_ratio = (hangul_count / text_len) if text_len else 0
        allowed_ratio = (allowed_count / text_len) if text_len else 0
        
        return {
            "filename": file.filename,
            "text_full": text,
            "metrics": {
                "length": text_len,
                "hangul_count": hangul_count,
                "hangul_ratio": round(hangul_ratio, 3),
                "allowed_ratio": round(allowed_ratio, 3)
            }
        }

    except Exception as e:
        error_msg = f"Server Error: {str(e)}\n{traceback.format_exc()}"
        logger.exception("Server Error")
        return JSONResponse(
            status_code=500,
            content={"detail": error_msg}
        )
