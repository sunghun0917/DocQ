from app.pdf_text_extractor import extract_text_from_pdf
from app.pdf_ocr import ocr_pdf
import logging

logger = logging.getLogger(__name__)


def extract_text_with_fallback(file_path: str) -> str:
    logger.info("PIPELINE START")
    text = extract_text_from_pdf(file_path)

    logger.info("Raw extracted text length: %s", len(text))
    logger.info("Raw extracted text preview (first 100 chars): %s", text[:100])

    if len(text) < 3000:
        logger.info("OCR fallback triggered")
        ocr_text = ocr_pdf(file_path)
        logger.info("OCR result length: %s", len(ocr_text))
        if len(ocr_text) > len(text):
            text = ocr_text

    return text
