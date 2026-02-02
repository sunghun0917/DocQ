import logging

from app.mineru_client import parse_pdf_with_mineru

logger = logging.getLogger(__name__)


def extract_text_with_fallback(file_path: str) -> str:
    logger.info("PIPELINE START (MinerU)")
    text = parse_pdf_with_mineru(file_path)
    logger.info("MinerU text length: %s", len(text))
    logger.info("MinerU text preview (first 100 chars): %s", text[:100])
    return text
